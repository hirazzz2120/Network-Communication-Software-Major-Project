package com.example.sipclient.sip;

import com.example.sipclient.call.CallManager;
import com.example.sipclient.call.CallSession;
import com.example.sipclient.chat.MessageHandler;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.clientauthutils.UserCredentials;

import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import com.example.sipclient.media.SdpTools;
import com.example.sipclient.media.AudioSession;
import com.example.sipclient.media.VideoSession; // ✅ 必须引入这个

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

    // ✅ 新增：同时拥有音频和视频引擎
    private final AudioSession audioSession = new AudioSession();
    private final VideoSession videoSession = new VideoSession();

    // ✅ 新增：独立的视频端口
    private final int localAudioPort = 50000 + (int)(Math.random() * 1000);
    private final int localVideoPort = 52000 + (int)(Math.random() * 1000);

    private final AtomicLong cseq = new AtomicLong(1);
    private volatile boolean registered;
    private volatile CountDownLatch registrationLatch = new CountDownLatch(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> reRegisterTask;
    private volatile ScheduledFuture<?> keepAliveTask;

    public SipUserAgent(String userAddress, String password, String localIp, int localPort) throws Exception {
        // ... (构造函数基础配置保持不变) ...
        Objects.requireNonNull(userAddress); Objects.requireNonNull(password); Objects.requireNonNull(localIp);
        this.password = password;
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        this.addressFactory = sipFactory.createAddressFactory();
        this.headerFactory = sipFactory.createHeaderFactory();
        this.messageFactory = sipFactory.createMessageFactory();

        SipURI parsedUri = (SipURI) addressFactory.createURI(userAddress);
        this.username = parsedUri.getUser();
        this.registrarHost = parsedUri.getHost();
        this.registrarPort = parsedUri.getPort() == -1 ? 5060 : parsedUri.getPort();
        this.transport = parsedUri.getTransportParam() != null ? parsedUri.getTransportParam() : ListeningPoint.UDP;

        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "SipClientStack-" + localPort);
        properties.setProperty("javax.sip.IP_ADDRESS", localIp);
        properties.setProperty("gov.nist.javax.sip.OUTBOUND_PROXY", registrarHost + ":" + registrarPort + "/" + transport);
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
        properties.setProperty("gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT", "30");
        properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "4");

        this.sipStack = sipFactory.createSipStack(properties);
        this.listeningPoint = sipStack.createListeningPoint(localIp, localPort, transport);
        this.sipProvider = sipStack.createSipProvider(listeningPoint);
        this.sipProvider.addSipListener(this);
        this.contactHeader = buildContactHeader(localIp, localPort);

        AccountManager accountManager = (ct, realm) -> new UserCredentials() {
            public String getUserName() { return username; }
            public String getPassword() { return password; }
            public String getSipDomain() { return registrarHost; }
        };
        this.authenticationHelper = ((SipStackExt) sipStack).getAuthenticationHelper(accountManager, headerFactory);
    }

    // ✅ 关键方法：暴露视频引擎给 UI 调用（修复 CallController 报错）
    public VideoSession getVideoSession() {
        return this.videoSession;
    }

    public void setMessageHandler(MessageHandler messageHandler) { this.messageHandler = messageHandler; }
    public void setCallManager(CallManager callManager) { this.callManager = callManager; }
    public CallManager getCallManager() { return this.callManager; }

    public boolean register(Duration timeout) throws SipException, InterruptedException {
        return sendRegister(3600, timeout);
    }
    public boolean unregister(Duration timeout) throws SipException, InterruptedException {
        return sendRegister(0, timeout);
    }

    public void shutdown() {
        scheduler.shutdownNow();
        if (registered) try { unregister(Duration.ofSeconds(1)); } catch (Exception e) {}
        // ✅ 关闭时同时停止音频和视频
        if (audioSession.isRunning()) audioSession.stop();
        if (videoSession.isRunning()) videoSession.stop();
        if (sipStack != null) try { sipStack.stop(); } catch (Exception e) {}
    }

    public boolean isRegistered() { return registered; }

    // --- 呼叫相关 ---

    // 默认音频呼叫
    public void startCall(String targetUri) throws SipException { startCall(targetUri, false); }

    // ✅ 支持视频的呼叫
    public void startCall(String targetUri, boolean enableVideo) throws SipException {
        try {
            Request invite = createInviteRequest(targetUri, enableVideo);
            if (callManager != null) callManager.startOutgoing(normalizeUri(targetUri));
            sipProvider.getNewClientTransaction(invite).sendRequest();
        } catch (Exception e) { throw new IllegalArgumentException("呼叫失败", e); }
    }

    // 兼容旧代码
    public void makeCall(String targetUri) throws SipException { startCall(targetUri, false); }

    public void hangup(String targetUri) throws SipException {
        // ✅ 挂断时全停
        if (audioSession.isRunning()) audioSession.stop();
        if (videoSession.isRunning()) videoSession.stop();

        if (callManager == null) return;
        String normalized = normalizeUri(targetUri);
        callManager.findByRemote(normalized).ifPresent(session -> {
            try {
                if (session.getDialog() != null) {
                    session.getDialog().sendRequest(sipProvider.getNewClientTransaction(session.getDialog().createRequest(Request.BYE)));
                }
                callManager.terminateLocal(normalized);
            } catch (Exception e) {}
        });
    }

    public void answerCall(String fromUri) throws SipException {
        String normalized = normalizeUri(fromUri);
        ServerTransaction tx = pendingInvites.remove(normalized);
        if (tx == null) return;

        try {
            // ✅ 接听时解析 SDP 启动媒体
            byte[] raw = tx.getRequest().getRawContent();
            if (raw != null) startMediaEngines(new String(raw, StandardCharsets.UTF_8));

            Response ok = messageFactory.createResponse(Response.OK, tx.getRequest());
            ok.addHeader(contactHeader);
            // ✅ 回复 SDP (带视频端口)
            String mySdp = SdpTools.createSdp(listeningPoint.getIPAddress(), localAudioPort, videoSession.isRunning() ? localVideoPort : 0);
            ok.setContent(mySdp, headerFactory.createContentTypeHeader("application", "sdp"));
            tx.sendResponse(ok);

            if (callManager != null) callManager.answerCall(normalized);
        } catch (Exception e) { throw new SipException("接听失败", e); }
    }

    // --- 事件处理 ---
    public void processRequest(RequestEvent evt) {
        String m = evt.getRequest().getMethod();
        if (Request.MESSAGE.equals(m)) handleMessage(evt);
        else if (Request.INVITE.equals(m)) handleInvite(evt);
        else if (Request.BYE.equals(m)) handleBye(evt);
        else if (Request.ACK.equals(m)) handleAck(evt);
    }
    public void processResponse(ResponseEvent evt) {
        String m = ((CSeqHeader)evt.getResponse().getHeader(CSeqHeader.NAME)).getMethod();
        if (Request.REGISTER.equals(m)) handleRegisterResponse(evt);
        else if (Request.INVITE.equals(m)) handleInviteResponse(evt);
    }
    public void processTimeout(TimeoutEvent e) { registrationLatch.countDown(); }
    public void processIOException(IOExceptionEvent e) { registrationLatch.countDown(); }
    public void processTransactionTerminated(TransactionTerminatedEvent e) {}
    public void processDialogTerminated(DialogTerminatedEvent e) {}

    // --- 逻辑处理 ---
    private void handleInviteResponse(ResponseEvent event) {
        Response response = event.getResponse();
        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            // ✅ 修复：呼叫方收到 200 OK 必须启动媒体
            try {
                byte[] raw = response.getRawContent();
                if (raw != null) startMediaEngines(new String(raw, StandardCharsets.UTF_8));

                if (event.getDialog() != null) {
                    event.getDialog().sendAck(event.getDialog().createAck(((CSeqHeader)response.getHeader(CSeqHeader.NAME)).getSeqNumber()));
                    if (callManager != null) {
                        callManager.attachDialog(extractToUri(response), event.getDialog());
                        callManager.markActive(extractToUri(response));
                    }
                }
            } catch (Exception e) {}
        } else if (response.getStatusCode() >= 400 && callManager != null) {
            callManager.terminateLocal(extractToUri(response));
        }
    }

    // ✅ 统一启动双引擎
    private void startMediaEngines(String remoteSdp) {
        String remoteIp = SdpTools.getRemoteIp(remoteSdp);
        int rAudio = SdpTools.getRemotePort(remoteSdp);
        int rVideo = SdpTools.getRemoteVideoPort(remoteSdp);

        if (remoteIp != null && rAudio > 0) {
            System.out.println(">>> 启动音频 -> " + remoteIp + ":" + rAudio);
            new Thread(() -> audioSession.start(remoteIp, rAudio, localAudioPort)).start();
        }
        if (remoteIp != null && rVideo > 0) {
            System.out.println(">>> 启动视频 -> " + remoteIp + ":" + rVideo);
            new Thread(() -> videoSession.start(remoteIp, rVideo, localVideoPort)).start();
        }
    }

    // ... (标准辅助方法) ...
    private void handleMessage(RequestEvent evt) {
        try {
            sipProvider.getNewServerTransaction(evt.getRequest()).sendResponse(messageFactory.createResponse(Response.OK, evt.getRequest()));
            if (messageHandler != null) messageHandler.handleIncomingMessage(extractFromUri(evt.getRequest()), new String(evt.getRequest().getRawContent(), StandardCharsets.UTF_8));
        } catch (Exception e) {}
    }
    private void handleInvite(RequestEvent evt) {
        try {
            ServerTransaction tx = sipProvider.getNewServerTransaction(evt.getRequest());
            Response r = messageFactory.createResponse(Response.RINGING, evt.getRequest());
            r.addHeader(contactHeader);
            tx.sendResponse(r);
            pendingInvites.put(extractFromUri(evt.getRequest()), tx);
            if (callManager != null) callManager.acceptIncoming(extractFromUri(evt.getRequest()));
        } catch (Exception e) {}
    }
    private void handleBye(RequestEvent evt) {
        try { sipProvider.getNewServerTransaction(evt.getRequest()).sendResponse(messageFactory.createResponse(Response.OK, evt.getRequest())); } catch (Exception e) {}
        if (callManager != null) callManager.terminateByRemote(extractFromUri(evt.getRequest()));
        if (audioSession.isRunning()) audioSession.stop();
        if (videoSession.isRunning()) videoSession.stop();
    }
    private void handleAck(RequestEvent evt) { if (callManager != null) callManager.markActive(extractFromUri(evt.getRequest())); }

    public void sendMessage(String targetUri, String text) throws SipException {
        try {
            SipURI reqUri = (SipURI) addressFactory.createURI(targetUri);
            Request req = messageFactory.createRequest(reqUri, Request.MESSAGE, sipProvider.getNewCallId(),
                    headerFactory.createCSeqHeader(cseq.getAndIncrement(), Request.MESSAGE),
                    headerFactory.createFromHeader(addressFactory.createAddress(addressFactory.createSipURI(username, registrarHost)), generateTag()),
                    headerFactory.createToHeader(addressFactory.createAddress(reqUri), null),
                    Collections.singletonList(headerFactory.createViaHeader(listeningPoint.getIPAddress(), listeningPoint.getPort(), transport, null)),
                    headerFactory.createMaxForwardsHeader(70));
            req.addHeader(contactHeader);
            req.setContent(text, headerFactory.createContentTypeHeader("text", "plain"));
            sipProvider.getNewClientTransaction(req).sendRequest();
        } catch (Exception e) { throw new SipException("发送失败", e); }
    }

    public void rejectCall(String fromUri) throws SipException {
        ServerTransaction tx = pendingInvites.remove(normalizeUri(fromUri));
        if (tx != null) {
            try { tx.sendResponse(messageFactory.createResponse(Response.BUSY_HERE, tx.getRequest())); } catch (Exception e) {}
            if (callManager != null) callManager.rejectCall(normalizeUri(fromUri));
        }
    }

    private Request createInviteRequest(String target, boolean video) throws Exception {
        SipURI reqUri = (SipURI) addressFactory.createURI(target);
        Request req = messageFactory.createRequest(reqUri, Request.INVITE, sipProvider.getNewCallId(),
                headerFactory.createCSeqHeader(cseq.getAndIncrement(), Request.INVITE),
                headerFactory.createFromHeader(addressFactory.createAddress(addressFactory.createSipURI(username, registrarHost)), generateTag()),
                headerFactory.createToHeader(addressFactory.createAddress(reqUri), null),
                Collections.singletonList(headerFactory.createViaHeader(listeningPoint.getIPAddress(), listeningPoint.getPort(), transport, null)),
                headerFactory.createMaxForwardsHeader(70));
        req.addHeader(contactHeader);
        String sdp = SdpTools.createSdp(listeningPoint.getIPAddress(), localAudioPort, video ? localVideoPort : 0);
        req.setContent(sdp, headerFactory.createContentTypeHeader("application", "sdp"));
        return req;
    }

    private boolean sendRegister(int expires, Duration timeout) throws SipException, InterruptedException {
        try {
            Request req = createRegisterRequest(expires);
            ClientTransaction tx = sipProvider.getNewClientTransaction(req);
            registrationLatch = new CountDownLatch(1);
            if(expires > 0) registered = false;
            tx.sendRequest();
            boolean ok = registrationLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return ok && registered == (expires > 0);
        } catch (Exception e) { throw new SipException("注册失败", e); }
    }

    private void handleRegisterResponse(ResponseEvent evt) {
        Response r = evt.getResponse();
        if(r.getStatusCode()==Response.UNAUTHORIZED) {
            try { authenticationHelper.handleChallenge(r, evt.getClientTransaction(), sipProvider, 5).sendRequest(); } catch(Exception e) { registrationLatch.countDown(); }
        } else if(r.getStatusCode()==Response.OK) {
            registered = true;
            scheduleReRegistration();
            registrationLatch.countDown();
        }
    }

    private Request createRegisterRequest(int expires) throws Exception {
        SipURI uri = addressFactory.createSipURI(null, registrarHost); uri.setPort(registrarPort);
        Address addr = addressFactory.createAddress(addressFactory.createSipURI(username, registrarHost));
        Request req = messageFactory.createRequest(uri, Request.REGISTER, sipProvider.getNewCallId(), headerFactory.createCSeqHeader(cseq.getAndIncrement(), Request.REGISTER),
                headerFactory.createFromHeader(addr, generateTag()), headerFactory.createToHeader(addr, null),
                Collections.singletonList(headerFactory.createViaHeader(listeningPoint.getIPAddress(), listeningPoint.getPort(), transport, null)), headerFactory.createMaxForwardsHeader(70));
        req.addHeader(contactHeader);
        req.addHeader(headerFactory.createExpiresHeader(expires));
        return req;
    }

    private ContactHeader buildContactHeader(String ip, int port) throws Exception {
        SipURI uri = addressFactory.createSipURI(username, ip); uri.setPort(port); uri.setTransportParam(transport);
        ContactHeader hdr = headerFactory.createContactHeader(addressFactory.createAddress(uri));
        hdr.setExpires(DEFAULT_EXPIRES_SECONDS);
        return hdr;
    }

    private void scheduleReRegistration() {
        if(reRegisterTask!=null) reRegisterTask.cancel(false);
        reRegisterTask = scheduler.schedule(()->{try{register(Duration.ofSeconds(10));}catch(Exception e){}}, (long)(3600*0.8), TimeUnit.SECONDS);
    }

    private String extractFromUri(Request r) { return normalizeUri(((FromHeader)r.getHeader(FromHeader.NAME)).getAddress().getURI()); }
    private String extractToUri(Response r) { return normalizeUri(((ToHeader)r.getHeader(ToHeader.NAME)).getAddress().getURI()); }
    private String normalizeUri(URI u) { return (u instanceof SipURI)?((SipURI)u).toString():u.toString(); }
    private String normalizeUri(String u) { try{return ((SipURI)addressFactory.createURI(u)).toString();}catch(Exception e){return u;} }
    private String generateTag() { return Long.toHexString(System.currentTimeMillis()); }
}