package com.example.sipclient.gui.controller;

import com.example.sipclient.call.CallManager;
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

        // 🛠️ 绑定视频回调
        // 1. 远程画面 -> 大屏幕
        userAgent.getVideoSession().setFrameCallback(image -> {
            if (image != null) {
                Platform.runLater(() -> {
                    avatarLabel.setVisible(false); // 有画面就隐藏头像
                    remoteVideoView.setImage(image);
                });
            }
        });

        // 2. 本地画面 -> 右下角小屏幕 (需要 VideoSession 支持，下一步我们会加)
        userAgent.getVideoSession().setLocalFrameCallback(image -> {
            if (image != null) {
                Platform.runLater(() -> localVideoView.setImage(image));
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
            // 清理回调
            if (userAgent != null && userAgent.getVideoSession() != null) {
                userAgent.getVideoSession().setFrameCallback(null);
                userAgent.getVideoSession().setLocalFrameCallback(null);
            }
            userAgent.hangup(contact.getSipUri());
            stopTimer();
            closeWindow();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleMute() {
        muted = !muted;
        muteButton.setText(muted ? "🔈" : "🔇");
        muteButton.setStyle(muted
                ? "-fx-background-color: #ffc107; -fx-text-fill: black; -fx-font-size: 24px; -fx-background-radius: 30;"
                : "-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-font-size: 24px; -fx-background-radius: 30;");
    }

    private void waitForCallEstablished() {
        Timeline checkTimer = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            if (callManager != null) {
                callManager.findByRemote(contact.getSipUri()).ifPresent(session -> {
                    if (session.getState() == com.example.sipclient.call.CallSession.State.ACTIVE) {
                        callStatusLabel.setText("通话已建立");
                        startTimer();
                    }
                });
            }
        }));
        checkTimer.setCycleCount(Timeline.INDEFINITE);
        checkTimer.play();
        new Timeline(new KeyFrame(Duration.seconds(60), e -> checkTimer.stop())).play();
    }

    private void startTimer() {
        if (timer != null) return;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            seconds++;
            long hrs = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            long secs = seconds % 60;
            timerLabel.setText(String.format("%02d:%02d", mins, secs)); // 简化显示分:秒
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() { if (timer != null) timer.stop(); }
    private void closeWindow() { ((Stage) hangupButton.getScene().getWindow()).close(); }
}