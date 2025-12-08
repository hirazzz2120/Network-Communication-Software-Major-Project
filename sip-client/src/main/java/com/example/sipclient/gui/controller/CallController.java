package com.example.sipclient.gui.controller;

import com.example.sipclient.call.CallManager;
import com.example.sipclient.gui.model.Contact;
import com.example.sipclient.sip.SipUserAgent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform; // 🟢 新增导入
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView; // 🟢 新增导入
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * 通话窗口控制器
 */
public class CallController {

    @FXML private Label contactNameLabel;
    @FXML private Label callStatusLabel;
    @FXML private Label timerLabel;
    @FXML private Button hangupButton;
    @FXML private Button muteButton;

    // 👇👇👇【新增变量】👇👇👇
    @FXML private ImageView videoView; // 用于显示视频画面
    @FXML private Label avatarLabel;   // 默认的头像（有视频时隐藏）
    // 👆👆👆【新增结束】👆👆👆

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

        // 👇👇👇【新增核心绑定逻辑】👇👇👇
        // 当 VideoSession 收到摄像头画面时，自动在界面的 videoView 上显示
        // 注意：必须用 Platform.runLater 包裹，因为这属于 UI 操作
        userAgent.getVideoSession().setFrameCallback(image -> {
            if (image != null) {
                Platform.runLater(() -> {
                    avatarLabel.setVisible(false); // 隐藏"👤"头像
                    videoView.setImage(image);     // 显示对方的脸！
                });
            }
        });
        // 👆👆👆【新增结束】👆👆👆

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
            // 👇👇👇【新增清理逻辑】👇👇👇
            // 挂断时清理回调，防止后台还在不停刷新 UI
            if (userAgent != null) {
                userAgent.getVideoSession().setFrameCallback(null);
            }
            // 👆👆👆【新增结束】👆👆👆

            userAgent.hangup(contact.getSipUri());
            stopTimer();
            closeWindow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleMute() {
        muted = !muted;
        muteButton.setText(muted ? "取消静音" : "静音");
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
            timerLabel.setText(String.format("%02d:%02d:%02d", seconds/3600, (seconds%3600)/60, seconds%60));
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) timer.stop();
    }

    private void closeWindow() {
        Stage stage = (Stage) hangupButton.getScene().getWindow();
        stage.close();
    }
}