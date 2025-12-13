package com.example.sipclient.media;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils; // 需要用到这个转换工具
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

public class VideoSession implements MediaSession {

    private static final Logger log = LoggerFactory.getLogger(VideoSession.class);
    private volatile boolean running = false;
    private DatagramSocket socket;
    private String remoteIp;
    private int remotePort;
    private Webcam webcam;

    private Consumer<Image> frameCallback;      // 远程
    private Consumer<Image> localFrameCallback; // 本地

    @Override
    public void start() {}

    public void start(String targetIp, int targetPort, int localPort) {
        if (running) return;
        this.remoteIp = targetIp;
        this.remotePort = targetPort;
        this.running = true;

        try {
            socket = new DatagramSocket(localPort);
            log.info(">>> [Video] 启动! 本地:{} -> 目标:{}:{}", localPort, targetIp, targetPort);
            new Thread(this::captureAndSend, "Camera-Sender").start();
            new Thread(this::receiveAndPlay, "Video-Receiver").start();
        } catch (SocketException e) {
            log.error("Socket启动失败", e);
            running = false;
        }
    }

    @Override
    public void stop() {
        running = false;
        if (socket != null) socket.close();
        if (webcam != null) { webcam.close(); }
        log.info(">>> [Video] 停止");
    }

    public boolean isRunning() { return running; }
    public void setFrameCallback(Consumer<Image> callback) { this.frameCallback = callback; }
    public void setLocalFrameCallback(Consumer<Image> callback) { this.localFrameCallback = callback; }

    private void captureAndSend() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) { log.error("❌ 无摄像头"); return; }

            // 必须用低分辨率防止 UDP 丢包
            webcam.setViewSize(new Dimension(176, 144));
            webcam.open();

            while (running) {
                long start = System.currentTimeMillis();
                if (!webcam.isOpen()) break;

                BufferedImage bImage = webcam.getImage();
                if (bImage == null) continue;

                // 1. [新增] 回调给本地界面预览 (JavaFX Image)
                if (localFrameCallback != null) {
                    // 注意：SwingFXUtils 需要 requires javafx.swing 模块，
                    // 如果报错找不到，可以用简易转换，或者忽略本地预览
                    Image fxImage = SwingFXUtils.toFXImage(bImage, null);
                    Platform.runLater(() -> localFrameCallback.accept(fxImage));
                }

                // 2. 压缩发送
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImage, "jpg", baos);
                byte[] data = baos.toByteArray();

                if (data.length < 4096) { // 宽松限制
                    DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(remoteIp), remotePort);
                    socket.send(packet);
                }

                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 50) { try { Thread.sleep(50 - elapsed); } catch (Exception e) {} }
            }
        } catch (Exception e) {
            log.error("采集异常", e);
        } finally {
            if (webcam != null) webcam.close();
        }
    }

    private void receiveAndPlay() {
        try {
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (running) {
                socket.receive(packet);
                byte[] validData = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, validData, 0, packet.getLength());
                Image image = new Image(new ByteArrayInputStream(validData));
                if (frameCallback != null) {
                    Platform.runLater(() -> frameCallback.accept(image));
                }
            }
        } catch (Exception e) {
            if (running) log.error("接收异常", e);
        }
    }
}