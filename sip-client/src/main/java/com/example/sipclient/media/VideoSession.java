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
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 视频会话 - 实现 UDP 分片传输
 * 解决大包(>1500 bytes)在网络中被丢弃导致黑屏的问题
 */
public class VideoSession implements MediaSession {

    private static final Logger log = LoggerFactory.getLogger(VideoSession.class);

    // 网络 MTU 通常是 1500，我们留出头部空间，每个包只发 1400 字节数据
    private static final int CHUNK_SIZE = 1400;
    // 协议头长度：FrameId(8) + TotalChunks(2) + ChunkIndex(2) = 12 bytes
    private static final int HEADER_SIZE = 12;

    private volatile boolean running = false;
    private DatagramSocket socket;
    private String remoteIp;
    private int remotePort;
    private Webcam webcam;

    private Consumer<Image> frameCallback;      // 远程画面回调
    private Consumer<Image> localFrameCallback; // 本地画面回调

    private final ExecutorService executor = Executors.newFixedThreadPool(3); // 发送+接收+处理

    // 用于重组分片的缓存: Map<FrameId, ReceivedChunks[]>
    private final Map<Long, byte[][]> frameBuffer = new ConcurrentHashMap<>();
    // 记录每帧收到的分片数量: Map<FrameId, ReceivedCount>
    private final Map<Long, Integer> frameProgress = new ConcurrentHashMap<>();

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
            // 允许端口重用，防止快速重启时报错
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(localPort));

            log.info(">>> [Video] 启动! 本地监听:{} -> 发送目标:{}:{}", localPort, targetIp, targetPort);

            executor.submit(this::captureAndSend);
            executor.submit(this::receiveAndProcess);

        } catch (SocketException e) {
            log.error("Video Socket启动失败", e);
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
        frameBuffer.clear();
        frameProgress.clear();
        log.info(">>> [Video] 停止");
    }

    public boolean isRunning() { return running; }
    public void setFrameCallback(Consumer<Image> callback) { this.frameCallback = callback; }
    public void setLocalFrameCallback(Consumer<Image> callback) { this.localFrameCallback = callback; }

    // --- 发送逻辑：切片 ---
    private void captureAndSend() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                log.error("❌ 未检测到摄像头");
                return;
            }

            // 使用较低分辨率保证流畅度 (QQ/微信常用分辨率)
            webcam.setViewSize(new Dimension(320, 240));
            webcam.open();

            InetAddress targetAddress = InetAddress.getByName(remoteIp);
            long frameId = 0;

            while (running && !socket.isClosed()) {
                long start = System.currentTimeMillis();

                if (!webcam.isOpen()) break;
                BufferedImage bImage = webcam.getImage();
                if (bImage == null) continue;

                // 1. 本地预览
                if (localFrameCallback != null) {
                    try {
                        final Image fxImage = SwingFXUtils.toFXImage(bImage, null);
                        Platform.runLater(() -> localFrameCallback.accept(fxImage));
                    } catch (Exception e) { /* ignore */ }
                }

                // 2. 压缩图片 (JPG)
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImage, "jpg", baos);
                byte[] fullData = baos.toByteArray();

                // 3. 切片发送
                frameId++;
                int totalLength = fullData.length;
                // 计算需要多少个包
                int chunks = (int) Math.ceil((double) totalLength / CHUNK_SIZE);

                if (chunks > 100) { // 保护：如果图片太大(>140KB)，丢弃该帧
                    log.warn("帧过大丢弃: {} bytes", totalLength);
                    continue;
                }

                for (int i = 0; i < chunks; i++) {
                    int offset = i * CHUNK_SIZE;
                    int length = Math.min(CHUNK_SIZE, totalLength - offset);

                    // 构造数据包: [Header 12 bytes] + [Payload]
                    // Header: FrameId(8) + TotalChunks(2) + ChunkIndex(2)
                    ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + length);
                    buffer.putLong(frameId);
                    buffer.putShort((short) chunks);
                    buffer.putShort((short) i);
                    buffer.put(fullData, offset, length);

                    byte[] packetData = buffer.array();
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, targetAddress, remotePort);
                    socket.send(packet);

                    // 微小的发送间隔，防止瞬间塞满网卡缓冲区导致丢包
                    // Thread.sleep(0, 100);
                }

                // 控制帧率 ~20 FPS
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 50) {
                    try { Thread.sleep(50 - elapsed); } catch (Exception e) {}
                }
            }
        } catch (Exception e) {
            log.error("视频发送异常", e);
        } finally {
            if (webcam != null) webcam.close();
        }
    }

    // --- 接收逻辑：重组 ---
    private void receiveAndProcess() {
        try {
            // 接收缓冲区 (Header + Chunk)
            byte[] buffer = new byte[HEADER_SIZE + CHUNK_SIZE + 100];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            // 定期清理过期帧的线程
            new Thread(this::cleanUpOldFrames).start();

            log.info(">>> [Video] 开始接收...");

            while (running && !socket.isClosed()) {
                try {
                    socket.receive(packet);
                    if (packet.getLength() < HEADER_SIZE) continue;

                    ByteBuffer bb = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                    long frameId = bb.getLong();
                    short totalChunks = bb.getShort();
                    short chunkIndex = bb.getShort();

                    // 读取实际数据
                    byte[] data = new byte[packet.getLength() - HEADER_SIZE];
                    bb.get(data);

                    // 放入缓冲区重组
                    processChunk(frameId, totalChunks, chunkIndex, data);

                } catch (SocketException se) {
                    break;
                } catch (Exception e) {
                    log.error("视频帧处理错误", e);
                }
            }
        } catch (Exception e) {
            log.error("视频接收线程崩溃", e);
        }
    }

    private void processChunk(long frameId, int totalChunks, int chunkIndex, byte[] data) {
        // 如果是新的一帧，初始化缓冲区
        frameBuffer.putIfAbsent(frameId, new byte[totalChunks][]);
        frameProgress.putIfAbsent(frameId, 0);

        byte[][] chunks = frameBuffer.get(frameId);
        // 如果该帧已经处理完或数组大小不匹配（异常情况），跳过
        if (chunks == null || chunks.length != totalChunks) return;

        // 如果这个分片没收过，存入
        if (chunks[chunkIndex] == null) {
            chunks[chunkIndex] = data;
            int currentCount = frameProgress.compute(frameId, (k, v) -> v == null ? 1 : v + 1);

            // 如果所有分片都齐了 -> 合并 -> 显示
            if (currentCount == totalChunks) {
                assembleAndDisplay(frameId, chunks);
            }
        }
    }

    private void assembleAndDisplay(long frameId, byte[][] chunks) {
        try {
            // 计算总大小
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) totalSize += chunk.length;
            }

            // 合并
            ByteArrayOutputStream baos = new ByteArrayOutputStream(totalSize);
            for (byte[] chunk : chunks) {
                if (chunk != null) baos.write(chunk);
            }

            // 显示
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            Image image = new Image(bais);

            if (frameCallback != null && image.getException() == null) {
                Platform.runLater(() -> frameCallback.accept(image));
            }

            // 处理完后立即移除，释放内存
            frameBuffer.remove(frameId);
            frameProgress.remove(frameId);

        } catch (Exception e) {
            log.error("图片重组失败", e);
        }
    }

    // 清理未完成的旧帧（防止内存泄漏）
    private void cleanUpOldFrames() {
        while (running) {
            try {
                Thread.sleep(5000);
                if (frameBuffer.size() > 50) {
                    // 简单粗暴清理：清空所有缓存，等待下一个关键帧
                    frameBuffer.clear();
                    frameProgress.clear();
                    log.debug("清理视频帧缓存");
                }
            } catch (InterruptedException e) { break; }
        }
    }
}