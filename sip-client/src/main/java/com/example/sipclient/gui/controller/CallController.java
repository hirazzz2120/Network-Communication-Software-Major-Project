package com.example.sipclient.gui.controller;

import com.example.sipclient.call.CallManager;
import com.example.sipclient.call.CallSession;
import com.example.sipclient.gui.model.Contact;
import com.example.sipclient.sip.SipUserAgent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CallController {

    @FXML private Label contactNameLabel;
    @FXML private Label callStatusLabel;
    @FXML private Label timerLabel;
    @FXML private Button hangupButton;
    @FXML private Button muteButton;

    // 📺 视频组件
    @FXML private ImageView remoteVideoView; // 对方画面 (大)
    @FXML private ImageView localVideoView;  // 本机画面 (小 - 画中画)
    @FXML private Label avatarLabel;         // 默认头像

    private Contact contact;
    private SipUserAgent userAgent;
    private CallManager callManager;
    private Timeline timer;
    private int seconds = 0;
    private boolean muted = false;

    public void setCallInfo(Contact contact, SipUserAgent userAgent, CallManager callManager, boolean isReceiver) {
        this.contact = contact;
        this.userAgent = userAgent;
        this.callManager = callManager;

        contactNameLabel.setText(contact.getDisplayName());

        // 🛠️ 绑定视频回调 (远程)
        userAgent.getVideoSession().setFrameCallback(image -> {
            if (image != null) {
                Platform.runLater(() -> {
                    avatarLabel.setVisible(false); // 有画面就隐藏头像
                    remoteVideoView.setImage(image);
                });
            }
        });

        // 🛠️ 绑定视频回调 (本地预览)
        userAgent.getVideoSession().setLocalFrameCallback(image -> {
            if (image != null) {
                Platform.runLater(() -> {
                    localVideoView.setImage(image);
                });
            }
        });

        if (isReceiver) {
            callStatusLabel.setText("通话中...");
            startTimer();
        } else {
            callStatusLabel.setText("正在呼叫...");
            waitForCallEstablished();
        }
    }

    @FXML
    private void handleHangup() {
        try {
            cleanupCallbacks();
            userAgent.hangup(contact.getSipUri());
            stopTimer();
            closeWindow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 清理回调，防止内存泄漏和后台更新UI报错
    private void cleanupCallbacks() {
        if (userAgent != null && userAgent.getVideoSession() != null) {
            userAgent.getVideoSession().setFrameCallback(null);
            userAgent.getVideoSession().setLocalFrameCallback(null);
        }
    }

    @FXML
    private void handleMute() {
        muted = !muted;
        muteButton.setText(muted ? "🔈" : "🔇");
        // 实际上这里还需要调用 AudioSession 的 mute 方法，暂时只做 UI 变更
    }

    private void waitForCallEstablished() {
        Timeline checkTimer = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            if (callManager != null) {
                callManager.findByRemote(contact.getSipUri()).ifPresent(session -> {
                    if (session.getState() == CallSession.State.ACTIVE) {
                        callStatusLabel.setText("通话已建立");
                        startTimer();
                    } else if (session.getState() == CallSession.State.TERMINATED) {
                        // 对方拒接或挂断
                        cleanupCallbacks();
                        stopTimer();
                        closeWindow();
                    }
                });
            }
        }));
        checkTimer.setCycleCount(Timeline.INDEFINITE);
        checkTimer.play();

        // 60秒超时自动挂断
        new Timeline(new KeyFrame(Duration.seconds(60), e -> {
            checkTimer.stop();
            if (timer == null) handleHangup();
        })).play();
    }

    private void startTimer() {
        if (timer != null) return;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            seconds++;
            long mins = seconds / 60;
            long secs = seconds % 60;
            timerLabel.setText(String.format("%02d:%02d", mins, secs));
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() { if (timer != null) timer.stop(); }

    private void closeWindow() {
        Stage stage = (Stage) hangupButton.getScene().getWindow();
        if (stage != null) stage.close();
    }
}