package com.example.sipclient.file;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.sound.sampled.*;
import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 语音录制对话框
 */
public class AudioRecorderDialog {

    private Stage stage;
    private Label statusLabel;
    private Label timeLabel;
    private Button recordButton;
    private Button stopButton;
    private Button playButton;
    private Button sendButton;
    private Button cancelButton;
    private ProgressBar levelMeter;

    private TargetDataLine microphone;
    private ByteArrayOutputStream audioData;
    private boolean isRecording = false;
    private Timer recordingTimer;
    private int recordingSeconds = 0;
    private File recordedFile;

    // 音频格式
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16000, // 采样率
            16, // 采样位数
            1, // 单声道
            2, // 帧大小
            16000, // 帧率
            false // 小端序
    );

    // 最大录音时长（秒）
    private static final int MAX_RECORDING_SECONDS = 60;

    private Runnable onSendCallback;

    public AudioRecorderDialog() {
        createUI();
    }

    private void createUI() {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("录制语音");
        stage.setResizable(false);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 状态标签
        statusLabel = new Label("点击录音按钮开始");
        statusLabel.setStyle("-fx-font-size: 14px;");

        // 时间标签
        timeLabel = new Label("00:00");
        timeLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // 音量电平条
        levelMeter = new ProgressBar(0);
        levelMeter.setPrefWidth(200);
        levelMeter.setStyle("-fx-accent: #4CAF50;");

        // 按钮区域
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        recordButton = new Button("🎤 开始录音");
        recordButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 10 20;");
        recordButton.setOnAction(e -> startRecording());

        stopButton = new Button("⏹ 停止");
        stopButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 10 20;");
        stopButton.setOnAction(e -> stopRecording());
        stopButton.setDisable(true);

        playButton = new Button("▶ 试听");
        playButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 10 20;");
        playButton.setOnAction(e -> playRecording());
        playButton.setDisable(true);

        buttonBox.getChildren().addAll(recordButton, stopButton, playButton);

        // 发送和取消按钮
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER);

        sendButton = new Button("发送");
        sendButton.setStyle("-fx-background-color: #0084ff; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 10 30;");
        sendButton.setOnAction(e -> {
            if (onSendCallback != null && recordedFile != null) {
                onSendCallback.run();
            }
            stage.close();
        });
        sendButton.setDisable(true);

        cancelButton = new Button("取消");
        cancelButton.setStyle("-fx-font-size: 14px; -fx-padding: 10 30;");
        cancelButton.setOnAction(e -> {
            cleanup();
            stage.close();
        });

        actionBox.getChildren().addAll(sendButton, cancelButton);

        // 提示标签
        Label tipLabel = new Label("最长录音时间: " + MAX_RECORDING_SECONDS + " 秒");
        tipLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");

        root.getChildren().addAll(statusLabel, timeLabel, levelMeter, buttonBox, actionBox, tipLabel);

        Scene scene = new Scene(root, 350, 280);
        stage.setScene(scene);
    }

    public void show() {
        stage.showAndWait();
    }

    public void setOnSendCallback(Runnable callback) {
        this.onSendCallback = callback;
    }

    public File getRecordedFile() {
        return recordedFile;
    }

    private void startRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);

            if (!AudioSystem.isLineSupported(info)) {
                statusLabel.setText("不支持音频录制！");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(AUDIO_FORMAT);
            microphone.start();

            audioData = new ByteArrayOutputStream();
            isRecording = true;
            recordingSeconds = 0;

            // 更新UI
            recordButton.setDisable(true);
            stopButton.setDisable(false);
            playButton.setDisable(true);
            sendButton.setDisable(true);
            statusLabel.setText("正在录音...");

            // 录音线程
            Thread recordingThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isRecording) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioData.write(buffer, 0, bytesRead);

                        // 计算音量电平
                        double level = calculateLevel(buffer, bytesRead);
                        Platform.runLater(() -> levelMeter.setProgress(level));
                    }
                }
            });
            recordingThread.setDaemon(true);
            recordingThread.start();

            // 计时器
            recordingTimer = new Timer();
            recordingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    recordingSeconds++;
                    Platform.runLater(() -> {
                        int mins = recordingSeconds / 60;
                        int secs = recordingSeconds % 60;
                        timeLabel.setText(String.format("%02d:%02d", mins, secs));
                    });

                    if (recordingSeconds >= MAX_RECORDING_SECONDS) {
                        Platform.runLater(() -> stopRecording());
                    }
                }
            }, 1000, 1000);

        } catch (LineUnavailableException e) {
            statusLabel.setText("无法访问麦克风: " + e.getMessage());
        }
    }

    private void stopRecording() {
        isRecording = false;

        if (recordingTimer != null) {
            recordingTimer.cancel();
            recordingTimer = null;
        }

        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }

        // 保存到临时文件
        try {
            recordedFile = File.createTempFile("voice_", ".wav");
            recordedFile.deleteOnExit();

            byte[] audioBytes = audioData.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream audioInputStream = new AudioInputStream(
                    bais, AUDIO_FORMAT, audioBytes.length / AUDIO_FORMAT.getFrameSize());

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, recordedFile);

            statusLabel.setText("录音完成 (" + recordedFile.length() / 1024 + " KB)");

        } catch (IOException e) {
            statusLabel.setText("保存录音失败: " + e.getMessage());
        }

        // 更新UI
        recordButton.setDisable(false);
        stopButton.setDisable(true);
        playButton.setDisable(false);
        sendButton.setDisable(false);
        levelMeter.setProgress(0);
    }

    private void playRecording() {
        if (recordedFile == null || !recordedFile.exists()) {
            statusLabel.setText("没有录音文件");
            return;
        }

        try {
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(recordedFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
            statusLabel.setText("正在播放...");

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    Platform.runLater(() -> statusLabel.setText("播放完成"));
                    clip.close();
                }
            });

        } catch (Exception e) {
            statusLabel.setText("播放失败: " + e.getMessage());
        }
    }

    private double calculateLevel(byte[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            if (i + 1 < length) {
                short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
                sum += Math.abs(sample);
            }
        }
        double average = sum / (length / 2.0);
        return Math.min(1.0, average / 10000.0);
    }

    private void cleanup() {
        isRecording = false;
        if (recordingTimer != null) {
            recordingTimer.cancel();
        }
        if (microphone != null && microphone.isOpen()) {
            microphone.stop();
            microphone.close();
        }
        if (recordedFile != null && recordedFile.exists()) {
            recordedFile.delete();
            recordedFile = null;
        }
    }
}
