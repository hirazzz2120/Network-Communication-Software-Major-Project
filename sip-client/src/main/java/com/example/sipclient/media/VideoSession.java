package com.example.sipclient.media;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class VideoSession implements MediaSession {

    private static final Logger log = LoggerFactory.getLogger(VideoSession.class);
    // UDP 理论上限是 65535，我们预留一些头部空间
    private static final int MAX_PACKET_SIZE = 60000;

    private volatile boolean running = false;
    private DatagramSocket socket;
    private String remoteIp;
    private int remotePort;
    private Webcam webcam;

    private Consumer<Image> frameCallback;      // 远程画面回调
    private Consumer<Image> localFrameCallback; // 本地画面回调

    // 使用线程池来管理发送和接收线程，避免频繁创建销毁
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void start() {
        log.warn("请调用带参数的 start(ip, port, localPort)");
    }

    public synchronized void start(String targetIp, int targetPort, int localPort) {
        if (running) return;
        this.remoteIp = targetIp;
        this.remotePort = targetPort;
        this.running = true;

        try {
            // 绑定本地端口用于接收
            socket = new DatagramSocket(localPort);
            log.info(">>> [Video] 启动! 本地监听:{} -> 发送目标:{}:{}", localPort, targetIp, targetPort);

            // 提交任务到线程池
            executor.submit(this::captureAndSend);
            executor.submit(this::receiveAndPlay);

        } catch (SocketException e) {
            log.error("Socket启动失败", e);
            running = false;
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        log.info(">>> [Video] 停止");
    }

    public boolean isRunning() { return running; }
    public void setFrameCallback(Consumer<Image> callback) { this.frameCallback = callback; }
    public void setLocalFrameCallback(Consumer<Image> callback) { this.localFrameCallback = callback; }

    private void captureAndSend() {
        try {
            // 获取默认摄像头
            webcam = Webcam.getDefault();
            if (webcam == null) {
                log.error("❌ 未检测到摄像头");
                return;
            }

            // 使用较低分辨率以减小数据包体积，防止 UDP 丢包严重
            // QCIF (176x144) 是最安全的，局域网可以尝试 320x240
            webcam.setViewSize(new Dimension(176, 144));
            webcam.open();

            InetAddress targetAddress = InetAddress.getByName(remoteIp);

            while (running && !socket.isClosed()) {
                long start = System.currentTimeMillis();

                if (!webcam.isOpen()) break;
                BufferedImage bImage = webcam.getImage();
                if (bImage == null) continue;

                // 1. 本地预览回调
                if (localFrameCallback != null) {
                    try {
                        Image fxImage = SwingFXUtils.toFXImage(bImage, null);
                        Platform.runLater(() -> localFrameCallback.accept(fxImage));
                    } catch (Exception e) {
                        // 忽略转换错误
                    }
                }

                // 2. 压缩并发送
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // 写入 JPG 格式
                ImageIO.write(bImage, "jpg", baos);
                byte[] data = baos.toByteArray();

                // 只要数据包不超过 UDP 限制就发送
                // 注意：如果网络状况不好，大包容易丢失，这是 UDP 的特性
                if (data.length < MAX_PACKET_SIZE) {
                    DatagramPacket packet = new DatagramPacket(data, data.length, targetAddress, remotePort);
                    socket.send(packet);
                } else {
                    log.warn("视频帧过大丢弃: {} bytes", data.length);
                }

                // 控制帧率，大约 20 FPS
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 50) {
                    try { Thread.sleep(50 - elapsed); } catch (Exception e) {}
                }
            }
        } catch (Exception e) {
            log.error("视频采集/发送异常", e);
        } finally {
            if (webcam != null) webcam.close();
        }
    }

    private void receiveAndPlay() {
        try {
            // 缓冲区必须足够大，否则图像数据会被截断导致花屏或报错
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            log.info(">>> [Video] 开始接收数据...");

            while (running && !socket.isClosed()) {
                try {
                    socket.receive(packet); // 阻塞等待数据

                    if (packet.getLength() > 0) {
                        // 复制有效数据
                        byte[] validData = new byte[packet.getLength()];
                        System.arraycopy(packet.getData(), 0, validData, 0, packet.getLength());

                        // 转换为 JavaFX Image
                        ByteArrayInputStream bais = new ByteArrayInputStream(validData);
                        Image image = new Image(bais);

                        // 回调给界面显示
                        // 🔴 修复点：将 getError() 改为 getException()
                        if (frameCallback != null && image.getException() == null) {
                            Platform.runLater(() -> frameCallback.accept(image));
                        }
                    }
                } catch (SocketException se) {
                    // Socket 关闭时会抛出此异常，属正常退出流程
                    break;
                } catch (Exception e) {
                    log.error("视频帧处理错误", e);
                }
            }
        } catch (Exception e) {
            if (running) log.error("视频接收线程异常", e);
        }
    }
}