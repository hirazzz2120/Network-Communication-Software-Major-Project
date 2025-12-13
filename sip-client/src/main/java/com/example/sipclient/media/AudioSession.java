package com.example.sipclient.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sound.sampled.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音频会话 - G.711 PCMU 格式
 */
public class AudioSession implements MediaSession {

    private static final Logger log = LoggerFactory.getLogger(AudioSession.class);

    private volatile boolean running = false;
    private DatagramSocket socket;

    // 统一音频格式：8000Hz, 16bit, 单声道, Signed, Little Endian (大部分PC的标准)
    private static final AudioFormat FORMAT = new AudioFormat(8000, 16, 1, true, false);
    // 数据包大小
    private static final int BUFFER_SIZE = 1024;

    private String remoteIp;
    private int remotePort;

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
            socket = new DatagramSocket(localPort);
            log.info(">>> [Audio] 启动! 本地:{} -> 目标:{}:{}", localPort, targetIp, targetPort);

            executor.submit(this::captureAndSend);
            executor.submit(this::receiveAndPlay);

        } catch (SocketException e) {
            log.error("Audio Socket启动失败", e);
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
        log.info(">>> [Audio] 停止");
    }

    public boolean isRunning() {
        return running;
    }

    // --- 采集并发送 ---
    private void captureAndSend() {
        TargetDataLine mic = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                log.error("当前系统不支持此音频格式");
                return;
            }

            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(FORMAT);
            mic.start();

            byte[] pcmBuffer = new byte[BUFFER_SIZE]; // 16-bit PCM
            InetAddress address = InetAddress.getByName(remoteIp);

            log.info("麦克风采集开始...");
            while (running && !socket.isClosed()) {
                int bytesRead = mic.read(pcmBuffer, 0, pcmBuffer.length);
                if (bytesRead > 0) {
                    // 压缩 PCM -> G.711 u-law (体积减半)
                    byte[] ulawData = new byte[bytesRead / 2];
                    for (int i = 0; i < bytesRead / 2; i++) {
                        // 16bit 转 short (Little Endian)
                        int low = pcmBuffer[2 * i] & 0xFF;
                        int high = pcmBuffer[2 * i + 1];
                        short sample = (short) ((high << 8) | low);

                        ulawData[i] = G711.linear2ulaw(sample);
                    }

                    // 发送 UDP 包
                    DatagramPacket packet = new DatagramPacket(ulawData, ulawData.length, address, remotePort);
                    socket.send(packet);
                }
            }
        } catch (Exception e) {
            log.error("麦克风采集异常", e);
        } finally {
            if (mic != null) {
                mic.stop();
                mic.close();
            }
        }
    }

    // --- 接收并播放 ---
    private void receiveAndPlay() {
        SourceDataLine speaker = null;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
            speaker = (SourceDataLine) AudioSystem.getLine(info);
            speaker.open(FORMAT);
            speaker.start();

            // 接收缓冲区 (G.711 数据)
            byte[] receiveBuffer = new byte[BUFFER_SIZE / 2];
            DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            log.info("扬声器播放就绪...");
            while (running && !socket.isClosed()) {
                try {
                    socket.receive(packet);
                    int len = packet.getLength();
                    if (len > 0) {
                        // 解压 G.711 u-law -> PCM
                        byte[] pcmData = new byte[len * 2];
                        for (int i = 0; i < len; i++) {
                            short sample = G711.ulaw2linear(receiveBuffer[i]);
                            // short 转 16bit bytes (Little Endian)
                            pcmData[2 * i] = (byte) (sample & 0xFF);
                            pcmData[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
                        }

                        // 写入扬声器
                        speaker.write(pcmData, 0, pcmData.length);
                    }
                } catch (SocketException se) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("音频播放异常", e);
        } finally {
            if (speaker != null) {
                speaker.drain();
                speaker.stop();
                speaker.close();
            }
        }
    }
}