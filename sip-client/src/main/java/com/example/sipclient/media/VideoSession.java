package com.example.sipclient.media;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.util.function.Consumer;

/**
 * 视频会话 - 真·摄像头版 (Webcam Capture)
 * 功能：打开真实摄像头 -> 采集画面 -> JPG压缩 -> UDP发送 -> 接收显示
 */
public class VideoSession implements MediaSession {

    private static final Logger log = LoggerFactory.getLogger(VideoSession.class);
    private volatile boolean running = false;
    private DatagramSocket socket;

    private String remoteIp;
    private int remotePort;

    // 摄像头对象
    private Webcam webcam;

    // 回调给 UI 显示
    private Consumer<Image> frameCallback;

    @Override
    public void start() {}

    public void start(String targetIp, int targetPort, int localPort) {
        if (running) return;
        this.remoteIp = targetIp;
        this.remotePort = targetPort;
        this.running = true;

        try {
            socket = new DatagramSocket(localPort);
            log.info(">>> [Video] 摄像头引擎启动! 本地:{} -> 目标:{}:{}", localPort, targetIp, targetPort);

            // 启动发送线程 (采集)
            new Thread(this::captureAndSend, "Camera-Sender").start();
            // 启动接收线程 (播放)
            new Thread(this::receiveAndPlay, "Video-Receiver").start();

        } catch (SocketException e) {
            log.error("视频Socket启动失败", e);
            running = false;
        }
    }

    @Override
    public void stop() {
        running = false;
        if (socket != null) socket.close();

        // 关闭摄像头 (释放硬件资源)
        if (webcam != null) {
            log.info("正在关闭摄像头...");
            webcam.close();
            log.info("摄像头已关闭");
        }
        log.info(">>> [Video] 视频会话已停止");
    }

    public boolean isRunning() { return running; }

    public void setFrameCallback(Consumer<Image> callback) {
        this.frameCallback = callback;
    }

    // --- 发送端：摄像头采集 ---
    private void captureAndSend() {
        try {
            // 1. 获取并打开摄像头
            webcam = Webcam.getDefault();
            if (webcam == null) {
                log.error("❌ 未检测到摄像头！请检查设备连接。");
                return;
            }

            // 2. 设置分辨率
            // 必须设置低分辨率 (320x240)，否则 UDP 包会溢出导致画面卡死
            webcam.setViewSize(new Dimension(320, 240));
            webcam.open();
            log.info("✅ 摄像头已开启: {}", webcam.getName());

            while (running) {
                long start = System.currentTimeMillis();

                // 3. 获取一帧画面 (这是真人的脸！)
                if (!webcam.isOpen()) break;
                BufferedImage image = webcam.getImage();
                if (image == null) continue;

                // 4. 压缩成 JPG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                byte[] data = baos.toByteArray();

                // 5. UDP 发送 (限制 60k 以下，防止分片丢包)
                if (data.length < 60000) {
                    DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(remoteIp), remotePort);
                    socket.send(packet);
                } else {
                    // 如果画面太复杂导致压缩后依然很大，只能丢弃
                    log.warn("⚠️ 帧数据过大 ({} bytes) 已丢弃，请背景不要太花哨", data.length);
                }

                // 6. 控制帧率 (约 15 FPS，保证流畅度)
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 66) { // 1000ms / 15fps ≈ 66ms
                    try { Thread.sleep(66 - elapsed); } catch (InterruptedException e) {}
                }
            }
        } catch (Exception e) {
            log.error("摄像头采集异常", e);
        } finally {
            if (webcam != null) webcam.close();
        }
    }

    // --- 接收端：接收并显示 (逻辑不变) ---
    private void receiveAndPlay() {
        try {
            byte[] buffer = new byte[65535]; // UDP 最大包
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running) {
                try {
                    socket.receive(packet);

                    // 提取数据
                    byte[] validData = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, validData, 0, packet.getLength());

                    // 转为 JavaFX Image
                    ByteArrayInputStream bais = new ByteArrayInputStream(validData);
                    Image image = new Image(bais);

                    // 回调显示
                    if (frameCallback != null) {
                        Platform.runLater(() -> frameCallback.accept(image));
                    }

                } catch (SocketException e) {
                    break;
                } catch (Exception e) {
                    log.warn("帧解码失败", e);
                }
            }
        } catch (Exception e) {
            log.error("视频接收异常", e);
        }
    }
}