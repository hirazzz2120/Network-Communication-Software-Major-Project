package com.example.sipclient.sip;

import com.example.sipclient.call.CallManager;
import com.example.sipclient.call.CallSession;
import com.example.sipclient.chat.MessageHandler;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.clientauthutils.UserCredentials;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.example.sipclient.media.SdpTools;
import com.example.sipclient.media.AudioSession;
/**
 * Simple SIP user agent that can REGISTER and unREGISTER against an MSS registrar.
 * <p>
 * The class follows the minimum set of configuration options needed to let a desktop client
 * authenticate against a Mobicents SIP Server (MSS) instance using the JAIN SIP stack.
 */
public final class SipUserAgent implements SipListener {

    private static final int DEFAULT_EXPIRES_SECONDS = 3600;

    private final String username;
    private final String registrarHost;
    private final int registrarPort;
    private final String password;
    private final String transport;

    private final SipStack sipStack;
    private final SipProvider sipProvider;
    private final AddressFactory addressFactory;
    private final HeaderFactory headerFactory;
    private final MessageFactory messageFactory;
    private final ListeningPoint listeningPoint;
    private final ContactHeader contactHeader;
    private final AuthenticationHelper authenticationHelper;

    private MessageHandler messageHandler;
    private CallManager callManager;
    private final ConcurrentHashMap<String, ServerTransaction> pendingInvites = new ConcurrentHashMap<>();
    // [新增] 音频引擎与端口
    private final AudioSession audioSession = new AudioSession();
    private final int localAudioPort = 50000 + (int)(Math.random() * 1000);
    private final AtomicLong cseq = new AtomicLong(1);

    private volatile boolean registered;
    private volatile CountDownLatch registrationLatch = new CountDownLatch(0);

    /**
     * Creates a SIP user agent bound to a local socket that can register to MSS.
     *
     * @param userAddress a SIP URI such as {@code sip:alice@example.com}
     * @param password    plaintext password used during digest authentication
     * @param localIp     local IP address that MSS can reach (e.g. {@code 192.168.1.100})
     * @param localPort   local UDP port to bind (e.g. {@code 5070})
     * @throws Exception if the SIP stack cannot be initialised
     */
    public SipUserAgent(String userAddress, String password, String localIp, int localPort) throws Exception {
        Objects.requireNonNull(userAddress, "userAddress");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(localIp, "localIp");

        this.password = password;

        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        this.addressFactory = sipFactory.createAddressFactory();
        this.headerFactory = sipFactory.createHeaderFactory();
        this.messageFactory = sipFactory.createMessageFactory();

        SipURI parsedUri = (SipURI) addressFactory.createURI(userAddress);
        if (!"sip".equalsIgnoreCase(parsedUri.getScheme())) {
            throw new IllegalArgumentException("Only sip: URIs are supported");
        }
        this.username = parsedUri.getUser();
        this.registrarHost = parsedUri.getHost();
        this.registrarPort = parsedUri.getPort() == -1 ? 5060 : parsedUri.getPort();
        this.transport = parsedUri.getTransportParam() != null ? parsedUri.getTransportParam() : ListeningPoint.UDP;

        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "SipClientStack");
        properties.setProperty("javax.sip.IP_ADDRESS", localIp);
        // OUTBOUND_PROXY makes sure requests are routed to MSS instead of DNS lookups.
        properties.setProperty("gov.nist.javax.sip.OUTBOUND_PROXY",
                registrarHost + ":" + registrarPort + "/" + transport);
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");

        this.sipStack = sipFactory.createSipStack(properties);
        this.listeningPoint = sipStack.createListeningPoint(localIp, localPort, transport);
        this.sipProvider = sipStack.createSipProvider(listeningPoint);
        this.sipProvider.addSipListener(this);

        this.contactHeader = buildContactHeader(localIp, localPort);

        AccountManager accountManager = (ClientTransaction ct, String realm) -> new UserCredentials() {
            @Override
            public String getUserName() {
                return username;
            }

            @Override
            public String getPassword() {
                return password;
            }

            @Override
            public String getSipDomain() {
                return registrarHost;
            }
        };
        this.authenticationHelper = ((SipStackExt) sipStack).getAuthenticationHelper(accountManager, headerFactory);
    }

    public void setMessageHandler(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void setCallManager(CallManager callManager) {
        this.callManager = callManager;
    }

    /**
     * Performs SIP registration and blocks until MSS responds or the timeout expires.
     *
     * @param timeout how long to wait for the final response
     * @return {@code true} if the 200 OK for REGISTER is received before timeout
     * @throws SipException         when the REGISTER request fails to send
     * @throws InterruptedException if waiting for the response is interrupted
     */
    public boolean register(Duration timeout) throws SipException, InterruptedException {
        return sendRegister(DEFAULT_EXPIRES_SECONDS, timeout);
    }

    /**
     * Unregisters the contact by sending a REGISTER with Expires=0.
     *
     * @param timeout how long to wait for the final response
     * @return {@code true} if MSS confirms the de-registration
     * @throws SipException         when the REGISTER request fails to send
     * @throws InterruptedException if waiting for the response is interrupted
     */
    public boolean unregister(Duration timeout) throws SipException, InterruptedException {
        return sendRegister(0, timeout);
    }

    /**
     * Shuts down the SIP stack and releases sockets.
     */
    public void shutdown() {
        System.out.println("[SipUserAgent] 关闭 SIP 连接...");
        
        // 1. 先尝试注销（如果已注册）
        if (registered) {
            try {
                System.out.println("[SipUserAgent] 正在向服务器发送注销请求...");
                unregister(java.time.Duration.ofSeconds(2)); // 2秒超时
                System.out.println("[SipUserAgent] 已向服务器发送注销请求");
            } catch (Exception e) {
                System.err.println("[SipUserAgent] 注销失败（继续关闭）: " + e.getMessage());
            }
        }
        
        // 2. 标记为未注册
        registered = false;
        
        try {
            // 1. 移除监听器
            if (sipProvider != null) {
                sipProvider.removeSipListener(this);
                System.out.println("[SipUserAgent] 已移除 SIP 监听器");
            }
        } catch (Exception e) {
            System.err.println("[SipUserAgent] 移除监听器失败: " + e.getMessage());
        }
        
        try {
            // 2. 删除 ListeningPoint (先删除ListeningPoint释放端口)
            if (listeningPoint != null && sipStack != null) {
                int port = listeningPoint.getPort();
                sipStack.deleteListeningPoint(listeningPoint);
                System.out.println("[SipUserAgent] 已删除 ListeningPoint (端口: " + port + ")");
            }
        } catch (Exception e) {
            System.err.println("[SipUserAgent] 删除 ListeningPoint 失败: " + e.getMessage());
        }
        
        try {
            // 3. 删除 SipProvider
            if (sipProvider != null && sipStack != null) {
                sipStack.deleteSipProvider(sipProvider);
                System.out.println("[SipUserAgent] 已删除 SipProvider");
            }
        } catch (Exception e) {
            System.err.println("[SipUserAgent] 删除 SipProvider 失败: " + e.getMessage());
        }
        
        // 4. 异步停止 SIP 栈（避免阻塞）
        if (sipStack != null) {
            new Thread(() -> {
                try {
                    System.out.println("[SipUserAgent] 正在异步停止 SIP 栈...");
                    sipStack.stop();
                    System.out.println("[SipUserAgent] SIP 栈已停止");
                } catch (Exception e) {
                    System.err.println("[SipUserAgent] 停止 SIP 栈失败: " + e.getMessage());
                }
            }, "SipStack-Shutdown").start();
        }
        
        System.out.println("[SipUserAgent] SIP 连接已关闭，端口已释放");
    }

    public boolean isRegistered() {
        return registered;
    }

    public void sendMessage(String targetUri, String text) throws SipException {
        Objects.requireNonNull(targetUri, "targetUri");
        Objects.requireNonNull(text, "text");
        try {
            SipURI requestUri = (SipURI) addressFactory.createURI(targetUri);
            Address fromAddress = addressFactory.createAddress(addressFactory.createSipURI(username, registrarHost));
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, generateTag());
            Address toAddress = addressFactory.createAddress(requestUri);
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            List<ViaHeader> viaHeaders = Collections.singletonList(
                    headerFactory.createViaHeader(listeningPoint.getIPAddress(),
                            listeningPoint.getPort(), transport, null));

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq.getAndIncrement(), Request.MESSAGE);
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            Request request = messageFactory.createRequest(
                    requestUri,
                    Request.MESSAGE,
                    callIdHeader,
                    cSeqHeader,
                    fromHeader,
                    toHeader,
                    viaHeaders,
                    maxForwardsHeader
            );

            request.addHeader(contactHeader);
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("text", "plain");
            request.setContent(text, contentTypeHeader);

            ClientTransaction transaction = sipProvider.getNewClientTransaction(request);
            transaction.sendRequest();
        } catch (ParseException | javax.sip.InvalidArgumentException ex) {
            throw new IllegalArgumentException("目标 URI 不合法", ex);
        }
    }

    public void startCall(String targetUri) throws SipException {
        Objects.requireNonNull(targetUri, "targetUri");
        try {
            Request invite = createInviteRequest(targetUri);
            if (callManager != null) {
                callManager.startOutgoing(normalizeUri(targetUri));
            }
            ClientTransaction transaction = sipProvider.getNewClientTransaction(invite);
            transaction.sendRequest();
        } catch (ParseException | javax.sip.InvalidArgumentException ex) {
            throw new IllegalArgumentException("目标 URI 不合法", ex);
        }
    }

    /**
     * 发起呼叫（startCall 的别名）
     */
    public void makeCall(String targetUri) throws SipException {
        startCall(targetUri);
    }

    public void hangup(String targetUri) throws SipException {
        // [新增] 挂断时停止音频
        if (audioSession.isRunning()) {
            audioSession.stop();
        }
        Objects.requireNonNull(targetUri, "targetUri");
        if (callManager == null) {
            throw new IllegalStateException("Call manager is not configured");
        }
        String normalized = normalizeUri(targetUri);
        Dialog dialog = callManager.findByRemote(normalized)
                .map(CallSession::getDialog)
                .orElse(null);
        if (dialog == null) {
            callManager.terminateLocal(normalized);
            return;
        }
        Request bye = dialog.createRequest(Request.BYE);
        ClientTransaction transaction = sipProvider.getNewClientTransaction(bye);
        dialog.sendRequest(transaction);
        callManager.terminateLocal(normalized);
    }

    /**
     * 接听来电
     */
    public void answerCall(String fromUri) throws SipException {
        Objects.requireNonNull(fromUri, "fromUri");
        String normalized = normalizeUri(fromUri);
        ServerTransaction transaction = pendingInvites.remove(normalized);
        
        if (transaction == null) {
            System.err.println("没有来自 " + fromUri + " 的待处理来电");
            return;
        }

        try {
            // [新增] 1. 解析对方名片，启动音频
            byte[] rawContent = transaction.getRequest().getRawContent();
            if (rawContent != null) {
                String remoteSdp = new String(rawContent, StandardCharsets.UTF_8);
                startAudioEngine(remoteSdp);
            }
            // 发送 200 OK 响应
            Response ok = messageFactory.createResponse(Response.OK, transaction.getRequest());
            ok.addHeader(contactHeader);
            // [新增] 2. 回复我的名片
            String mySdp = SdpTools.createAudioSdp(listeningPoint.getIPAddress(), localAudioPort);
            ContentTypeHeader cth = headerFactory.createContentTypeHeader("application", "sdp");
            ok.setContent(mySdp, cth);
            transaction.sendResponse(ok);

            // 标记呼叫为活跃状态
            if (callManager != null) {
                callManager.answerCall(normalized);
            }
            
            System.out.println("✓ 已接听来自 " + fromUri + " 的呼叫");
        } catch (Exception ex) {
            System.err.println("接听失败: " + ex.getMessage());
            throw new SipException("Failed to answer call", ex);
        }
    }

    /**
     * 拒接来电
     */
    public void rejectCall(String fromUri) throws SipException {
        Objects.requireNonNull(fromUri, "fromUri");
        String normalized = normalizeUri(fromUri);
        ServerTransaction transaction = pendingInvites.remove(normalized);
        
        if (transaction == null) {
            System.err.println("没有来自 " + fromUri + " 的待处理来电");
            return;
        }

        try {
            // 发送 486 Busy Here 响应
            Response busy = messageFactory.createResponse(Response.BUSY_HERE, transaction.getRequest());
            transaction.sendResponse(busy);

            // 移除呼叫会话
            if (callManager != null) {
                callManager.rejectCall(normalized);
            }
            
            System.out.println("✓ 已拒接来自 " + fromUri + " 的呼叫");
        } catch (Exception ex) {
            System.err.println("拒接失败: " + ex.getMessage());
            throw new SipException("Failed to reject call", ex);
        }
    }

    private boolean sendRegister(int expires, Duration timeout) throws SipException, InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Request registerRequest = createRegisterRequest(expires);
        ClientTransaction transaction = sipProvider.getNewClientTransaction(registerRequest);
        registrationLatch = new CountDownLatch(1);
        if (expires > 0) {
            registered = false;
        }
        transaction.sendRequest();

        boolean success = registrationLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return success && registered == (expires > 0);
    }

    private Request createRegisterRequest(int expires) {
        try {
            SipURI requestUri = addressFactory.createSipURI(null, registrarHost);
            requestUri.setPort(registrarPort);
            requestUri.setTransportParam(transport);

            SipURI fromUri = addressFactory.createSipURI(username, registrarHost);
            Address fromAddress = addressFactory.createAddress(fromUri);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, generateTag());
            ToHeader toHeader = headerFactory.createToHeader(fromAddress, null);

            List<ViaHeader> viaHeaders = Collections.singletonList(
                    headerFactory.createViaHeader(listeningPoint.getIPAddress(),
                            listeningPoint.getPort(), transport, null));

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq.getAndIncrement(), Request.REGISTER);
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            Request request = messageFactory.createRequest(
                    requestUri,
                    Request.REGISTER,
                    callIdHeader,
                    cSeqHeader,
                    fromHeader,
                    toHeader,
                    viaHeaders,
                    maxForwardsHeader
            );

            request.addHeader(contactHeader);
            ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(expires);
            request.addHeader(expiresHeader);

            UserAgentHeader userAgentHeader = headerFactory.createUserAgentHeader(
                    List.of("Project-SIP-Client/1.0"));
            request.addHeader(userAgentHeader);

            return request;
        } catch (ParseException | javax.sip.InvalidArgumentException ex) {
            throw new IllegalStateException("Failed to build REGISTER request", ex);
        }
    }

    private Request createInviteRequest(String targetUri) throws ParseException, SipException, javax.sip.InvalidArgumentException {
        SipURI requestUri = (SipURI) addressFactory.createURI(targetUri);

        SipURI fromUri = addressFactory.createSipURI(username, registrarHost);
        Address fromAddress = addressFactory.createAddress(fromUri);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, generateTag());

        Address toAddress = addressFactory.createAddress(requestUri);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        List<ViaHeader> viaHeaders = Collections.singletonList(
                headerFactory.createViaHeader(listeningPoint.getIPAddress(),
                        listeningPoint.getPort(), transport, null));

        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq.getAndIncrement(), Request.INVITE);
        MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

        Request request = messageFactory.createRequest(
                requestUri,
                Request.INVITE,
                callIdHeader,
                cSeqHeader,
                fromHeader,
                toHeader,
                viaHeaders,
                maxForwardsHeader
        );

        request.addHeader(contactHeader);
        // [修改] 使用 SdpTools 生成真正的 SDP 名片
        ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
        String sdpData = SdpTools.createAudioSdp(listeningPoint.getIPAddress(), localAudioPort);
        request.setContent(sdpData, contentTypeHeader);

        return request;
    }

    private String buildPlaceholderSdp() {
        return "v=0\r\n" +
                "o=" + username + " 0 0 IN IP4 " + listeningPoint.getIPAddress() + "\r\n" +
                "s=Project-SIP-Client\r\n" +
                "c=IN IP4 " + listeningPoint.getIPAddress() + "\r\n" +
                "t=0 0\r\n" +
                "m=audio " + listeningPoint.getPort() + " RTP/AVP 0\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n";
    }

    private void handleIncomingMessage(RequestEvent event) {
        try {
            ServerTransaction transaction = ensureServerTransaction(event);
            Response ok = messageFactory.createResponse(Response.OK, event.getRequest());
            ok.addHeader(contactHeader);
            transaction.sendResponse(ok);
        } catch (Exception ex) {
            System.err.println("Failed to respond to MESSAGE: " + ex.getMessage());
        }
        if (messageHandler != null) {
            String fromUri = extractFromUri(event.getRequest());
            byte[] raw = event.getRequest().getRawContent();
            String body = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
            messageHandler.handleIncomingMessage(fromUri, body);
        }
    }

    private void handleIncomingInvite(RequestEvent event) {
        String remote = extractFromUri(event.getRequest());
        try {
            ServerTransaction transaction = ensureServerTransaction(event);
            
            // 发送 180 Ringing 响应,表示振铃中
            Response ringing = messageFactory.createResponse(Response.RINGING, event.getRequest());
            ringing.addHeader(contactHeader);
            transaction.sendResponse(ringing);

            // 保存待处理的邀请,等待用户手动接听或拒接
            pendingInvites.put(remote, transaction);

            // 通知 CallManager 有来电
            if (callManager != null) {
                System.out.println("[DEBUG] 调用 CallManager.acceptIncoming: " + remote);
                callManager.acceptIncoming(remote);
                Dialog dialog = transaction.getDialog();
                if (dialog != null) {
                    callManager.attachDialog(remote, dialog);
                }
            } else {
                System.out.println("[DEBUG] CallManager 为 null!");
            }
        } catch (Exception ex) {
            try {
                ServerTransaction transaction = ensureServerTransaction(event);
                Response busy = messageFactory.createResponse(Response.BUSY_HERE, event.getRequest());
                transaction.sendResponse(busy);
            } catch (Exception ignored) {
            }
            System.err.println("Failed to handle INVITE: " + ex.getMessage());
        }
    }

    private void handleIncomingBye(RequestEvent event) {
        try {
            ServerTransaction transaction = ensureServerTransaction(event);
            Response ok = messageFactory.createResponse(Response.OK, event.getRequest());
            transaction.sendResponse(ok);
        } catch (Exception ex) {
            System.err.println("Failed to acknowledge BYE: " + ex.getMessage());
        }
        String remote = extractFromUri(event.getRequest());
        if (callManager != null) {
            callManager.terminateByRemote(remote);
        }
    }

    private void handleAck(RequestEvent event) {
        String remote = extractFromUri(event.getRequest());
        Dialog dialog = event.getDialog();
        if (dialog != null && callManager != null) {
            callManager.attachDialog(remote, dialog);
        }
        if (callManager != null) {
            callManager.markActive(remote);
        }
    }

    private ServerTransaction ensureServerTransaction(RequestEvent event) throws SipException {
        ServerTransaction transaction = event.getServerTransaction();
        if (transaction == null) {
            transaction = sipProvider.getNewServerTransaction(event.getRequest());
        }
        return transaction;
    }

    private String extractFromUri(Request request) {
        FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
        if (fromHeader == null) {
            return "unknown";
        }
        return normalizeUri(fromHeader.getAddress().getURI());
    }

    private String extractToUri(Response response) {
        ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
        if (toHeader == null) {
            return "unknown";
        }
        return normalizeUri(toHeader.getAddress().getURI());
    }

    private ContactHeader buildContactHeader(String localIp, int localPort) throws ParseException, javax.sip.InvalidArgumentException {
        SipURI contactUri = addressFactory.createSipURI(username, localIp);
        contactUri.setPort(localPort);
        contactUri.setTransportParam(transport);
        Address contactAddress = addressFactory.createAddress(contactUri);
        ContactHeader header = headerFactory.createContactHeader(contactAddress);
        header.setExpires(DEFAULT_EXPIRES_SECONDS);
        return header;
    }

    private String generateTag() {
        return Long.toHexString(System.currentTimeMillis());
    }

    private String normalizeUri(String rawUri) {
        try {
            return ((SipURI) addressFactory.createURI(rawUri)).toString();
        } catch (ParseException ex) {
            return rawUri;
        }
    }

    private String normalizeUri(URI uri) {
        if (uri == null) {
            return "unknown";
        }
        if (uri instanceof SipURI sipURI) {
            return sipURI.toString();
        }
        return uri.toString();
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        if (Request.MESSAGE.equals(method)) {
            handleIncomingMessage(requestEvent);
        } else if (Request.INVITE.equals(method)) {
            handleIncomingInvite(requestEvent);
        } else if (Request.BYE.equals(method)) {
            handleIncomingBye(requestEvent);
        } else if (Request.ACK.equals(method)) {
            handleAck(requestEvent);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        String method = ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod();

        if (Request.REGISTER.equals(method)) {
            handleRegisterResponse(responseEvent);
        } else if (Request.INVITE.equals(method)) {
            handleInviteResponse(responseEvent);
        }
    }

    private int getExpiresFromResponse(Response response) {
        ExpiresHeader expiresHeader = (ExpiresHeader) response.getHeader(ExpiresHeader.NAME);
        if (expiresHeader != null) {
            return expiresHeader.getExpires();
        }
        ContactHeader responseContact = (ContactHeader) response.getHeader(ContactHeader.NAME);
        if (responseContact != null && responseContact.getExpires() != -1) {
            return responseContact.getExpires();
        }
        return registered ? DEFAULT_EXPIRES_SECONDS : 0;
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        registered = false;
        registrationLatch.countDown();
    }

    private void handleRegisterResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();

        if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
            try {
                ClientTransaction retryTransaction = authenticationHelper.handleChallenge(
                        response,
                        responseEvent.getClientTransaction(),
                        sipProvider,
                        5
                );
                retryTransaction.sendRequest();
                return;
            } catch (Exception ex) {
                registered = false;
                registrationLatch.countDown();
                System.err.println("Failed to respond to authentication challenge: " + ex.getMessage());
                return;
            }
        }

        if (status >= 200 && status < 300) {
            // [新增] 对方接听了，解析对方名片并启动音频
            if (response.getRawContent() != null) {
                String remoteSdp = new String(response.getRawContent(), StandardCharsets.UTF_8);
                startAudioEngine(remoteSdp);
            }
            registered = getExpiresFromResponse(response) > 0;
            registrationLatch.countDown();
        } else if (status >= 400) {
            registered = false;
            registrationLatch.countDown();
        }
    }

    private void handleInviteResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        String remote = extractToUri(response);

        if (status >= 100 && status < 200) {
            System.out.println("对方振铃中：" + remote + "，状态码 " + status);
            return;
        }

        if (status >= 200 && status < 300) {
            Dialog dialog = responseEvent.getDialog();
            if (dialog != null) {
                if (callManager != null) {
                    callManager.attachDialog(remote, dialog);
                }
                try {
                    Request ack = dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
                    dialog.sendAck(ack);
                } catch (SipException | javax.sip.InvalidArgumentException ex) {
                    System.err.println("Failed to send ACK: " + ex.getMessage());
                }
            }
            if (callManager != null) {
                callManager.markActive(remote);
            }
        } else if (status >= 400) {
            System.err.println("呼叫失败 (status=" + status + ")");
            if (callManager != null) {
                callManager.terminateLocal(remote);
            }
        }
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        registered = false;
        registrationLatch.countDown();
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        // No-op
    }

    @Override
    public void processDialogTerminated(javax.sip.DialogTerminatedEvent dialogTerminatedEvent) {
        // No-op
    }
    // [新增] 辅助方法：启动音频引擎
    private void startAudioEngine(String remoteSdp) {
        String remoteIp = SdpTools.getRemoteIp(remoteSdp);
        int remotePort = SdpTools.getRemotePort(remoteSdp);
        System.out.println(">>> [Audio] 启动通话，对方: " + remoteIp + ":" + remotePort);

        if (remoteIp != null && remotePort > 0) {
            new Thread(() -> audioSession.start(remoteIp, remotePort, localAudioPort)).start();
        }
    }
}





