package com.example.sipclient.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sound.sampled.*;
import java.net.*;

/**
 * 成员 A 实现的真实音频会话
 * 版本：Little Endian 修正版 (解决杂音问题)
 */
public class AudioSession implements MediaSession {

    private static final Logger log = LoggerFactory.getLogger(AudioSession.class);

    private volatile boolean running = false;
    private DatagramSocket socket;

    // ⚠️ [修改点1] 改为 false (使用 Little Endian 小端序)，适配大多数 PC 声卡
    // 参数：8000Hz, 16bit, 单声道, 有符号, 小端序(false)
    private final AudioFormat format = new AudioFormat(8000, 16, 1, true, false);

    private String remoteIp;
    private int remotePort;

    @Override
    public void start() {
        log.warn("请调用带参数的 start(ip, port, localPort) 来启动真实通话");
    }

    public void start(String targetIp, int targetPort, int localPort) {
        if (running) return;
        this.remoteIp = targetIp;
        this.remotePort = targetPort;
        this.running = true;

        try {
            socket = new DatagramSocket(localPort);
            log.info("音频会话启动 (Little Endian)，本地: {}, 目标: {}:{}", localPort, targetIp, targetPort);

            new Thread(this::captureAndSend, "Audio-Sender").start();
            new Thread(this::receiveAndPlay, "Audio-Receiver").start();

        } catch (SocketException e) {
            log.error("启动失败: {}", e.getMessage());
            running = false;
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        log.info("音频会话已停止");
    }

    public boolean isRunning() {
        return running;
    }

    // --- 发送逻辑 ---
    private void captureAndSend() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(format);
            mic.start();

            byte[] pcmBuffer = new byte[1024];
            InetAddress address = InetAddress.getByName(remoteIp);

            log.info("麦克风已开启...");
            while (running) {
                int bytesRead = mic.read(pcmBuffer, 0, pcmBuffer.length);
                if (bytesRead > 0) {
                    byte[] compressedBuffer = new byte[bytesRead / 2];

                    for (int i = 0; i < bytesRead / 2; i++) {
                        // ⚠️ [修改点2] 小端序拼装：低位在前(2*i)，高位在后(2*i+1)
                        int low = pcmBuffer[2 * i];
                        int high = pcmBuffer[2 * i + 1];
                        // 拼成 16bit 样本
                        short sample = (short) ((high << 8) | (low & 0xFF));

                        compressedBuffer[i] = G711.linear2ulaw(sample);
                    }

                    DatagramPacket packet = new DatagramPacket(compressedBuffer, compressedBuffer.length, address, remotePort);
                    socket.send(packet);
                }
            }
            mic.close();
        } catch (Exception e) {
            log.error("麦克风采集异常: ", e);
        }
    }

    // --- 接收逻辑 ---
    private void receiveAndPlay() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(info);
            speaker.open(format);
            speaker.start();

            byte[] compressedBuffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(compressedBuffer, compressedBuffer.length);

            log.info("扬声器已就绪...");
            while (running) {
                try {
                    socket.receive(packet);

                    int len = packet.getLength();
                    byte[] pcmData = new byte[len * 2];

                    for (int i = 0; i < len; i++) {
                        short sample = G711.ulaw2linear(compressedBuffer[i]);

                        // ⚠️ [修改点3] 小端序拆分：先存低位，再存高位
                        pcmData[2 * i] = (byte) (sample & 0xFF);        // 低位
                        pcmData[2 * i + 1] = (byte) ((sample >> 8) & 0xFF); // 高位
                    }

                    speaker.write(pcmData, 0, pcmData.length);
                } catch (SocketException e) {
                    break;
                }
            }
            speaker.close();
        } catch (Exception e) {
            log.error("播放异常: ", e);
        }
    }
}