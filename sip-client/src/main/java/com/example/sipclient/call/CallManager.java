package com.example.sipclient.call;

import com.example.sipclient.media.AudioSession;
import com.example.sipclient.media.MediaSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.Dialog;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 管理当前会话的状态，并在通话建立/结束时拉起或关闭媒体会话。
 */
public class CallManager {

    public interface IncomingCallListener {
        void onIncomingCall(String fromUri, String sessionId);
    }

    private static final Logger log = LoggerFactory.getLogger(CallManager.class);

    private final Map<String, CallSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, String> remoteIndex = new ConcurrentHashMap<>();
    private final Supplier<MediaSession> mediaSupplier;
    private IncomingCallListener incomingCallListener;

    public CallManager() {
        this(AudioSession::new);
    }

    public CallManager(Supplier<MediaSession> mediaSupplier) {
        this.mediaSupplier = mediaSupplier == null ? AudioSession::new : mediaSupplier;
    }

    public void setIncomingCallListener(IncomingCallListener listener) {
        this.incomingCallListener = listener;
    }

    public CallSession startOutgoing(String targetUri) {
        CallSession session = new CallSession(targetUri, false);
        session.markRinging();
        registerSession(session);
        log.info("已发起到 {} 的呼叫，sessionId={}", targetUri, session.getId());
        return session;
    }

    public CallSession acceptIncoming(String fromUri) {
        CallSession session = new CallSession(fromUri, true);
        session.markRinging();
        registerSession(session);
        log.info("收到来自 {} 的来电，sessionId={}", fromUri, session.getId());
        if (incomingCallListener != null) {
            incomingCallListener.onIncomingCall(fromUri, session.getId());
        }
        return session;
    }

    public void markActive(String remoteUri) {
        findByRemote(remoteUri).ifPresent(session -> {
            if (session.getState() != CallSession.State.ACTIVE) {
                session.markActive();
                // 音频已由 SipUserAgent 直接管理，不再在这里启动
                // session.startMedia(mediaSupplier.get());
                log.info("呼叫 {} 已建立", session.getId());
            }
        });
    }

    public void terminateByRemote(String remoteUri) {
        findByRemote(remoteUri).ifPresent(this::removeSession);
    }

    public void terminateLocal(String remoteUri) {
        findByRemote(remoteUri).ifPresent(this::removeSession);
    }

    public void attachDialog(String remoteUri, Dialog dialog) {
        Objects.requireNonNull(remoteUri, "remoteUri");
        Objects.requireNonNull(dialog, "dialog");
        findByRemote(remoteUri).ifPresent(session -> {
            session.bindDialog(dialog);
            log.debug("Session {} bound to dialog {}", session.getId(), dialog);
        });
    }

    public Collection<CallSession> listSessions() {
        return List.copyOf(sessionsById.values());
    }

    public Optional<CallSession> findByRemote(String remoteUri) {
        String sessionId = remoteIndex.get(remoteUri);
        return sessionId == null ? Optional.empty() : Optional.ofNullable(sessionsById.get(sessionId));
    }

    public void answerCall(String remoteUri) {
        findByRemote(remoteUri).ifPresent(session -> {
            session.markActive();
            // 音频已由 SipUserAgent 直接管理，不再在这里启动
            // session.startMedia(mediaSupplier.get());
            log.info("已接听来自 {} 的呼叫", remoteUri);
        });
    }

    public void rejectCall(String remoteUri) {
        findByRemote(remoteUri).ifPresent(session -> {
            removeSession(session);
            log.info("已拒接来自 {} 的呼叫", remoteUri);
        });
    }

    private void registerSession(CallSession session) {
        sessionsById.put(session.getId(), session);
        remoteIndex.put(session.getRemoteUri(), session.getId());
    }

    private void removeSession(CallSession session) {
        session.terminate();
        sessionsById.remove(session.getId());
        remoteIndex.remove(session.getRemoteUri());
        log.info("呼叫 {} 已结束", session.getId());
    }
}
