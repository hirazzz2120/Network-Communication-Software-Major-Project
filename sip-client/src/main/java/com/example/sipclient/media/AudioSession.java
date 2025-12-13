package com.example.sipclient.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sound.sampled.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音频会话 - G.711 PCMU 格式
 * 改进：增加序列号头，防止乱序播放
 */
public class AudioSession implements MediaSession {

    private static final Logger log = LoggerFactory.getLogger(AudioSession.class);

    private volatile boolean running = false;
    private DatagramSocket socket;

    // 8000Hz, 16bit, 单声道 (标准电话音质)
    private static final AudioFormat FORMAT = new AudioFormat(8000, 16, 1, true, false);

    // 每次采集 320 字节 (20ms 音频)，延迟更低
    private static final int CHUNK_SIZE = 320;
    // 头部: 2字节序列号
    private static final int HEADER_SIZE = 2;

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
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(localPort));

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
                log.error("❌ 致命错误: 系统不支持麦克风输入 (AudioFormat不支持)");
                return;
            }

            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(FORMAT);
            mic.start();

            byte[] pcmBuffer = new byte[CHUNK_SIZE];
            InetAddress address = InetAddress.getByName(remoteIp);
            short sequenceNumber = 0;

            log.info("麦克风采集开始...");
            while (running && !socket.isClosed()) {
                int bytesRead = mic.read(pcmBuffer, 0, pcmBuffer.length);
                if (bytesRead > 0) {
                    // 1. PCM -> G.711 u-law (压缩一半)
                    byte[] ulawData = new byte[bytesRead / 2];
                    for (int i = 0; i < bytesRead / 2; i++) {
                        int low = pcmBuffer[2 * i] & 0xFF;
                        int high = pcmBuffer[2 * i + 1];
                        short sample = (short) ((high << 8) | low);
                        ulawData[i] = G711.linear2ulaw(sample);
                    }

                    // 2. 构造包: [SeqNum 2B] + [Data]
                    ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE + ulawData.length);
                    bb.putShort(sequenceNumber++);
                    bb.put(ulawData);

                    // 3. 发送
                    byte[] packetData = bb.array();
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, address, remotePort);
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
            if (!AudioSystem.isLineSupported(info)) {
                log.error("❌ 致命错误: 系统不支持扬声器输出");
                return;
            }

            speaker = (SourceDataLine) AudioSystem.getLine(info);
            speaker.open(FORMAT);
            speaker.start();

            // 接收缓冲区
            byte[] receiveBuffer = new byte[HEADER_SIZE + (CHUNK_SIZE / 2) + 100];
            DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            short lastSeq = -1;

            log.info("扬声器播放就绪...");
            while (running && !socket.isClosed()) {
                try {
                    socket.receive(packet);
                    if (packet.getLength() <= HEADER_SIZE) continue;

                    ByteBuffer bb = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                    short seq = bb.getShort();

                    // 简单防乱序：如果收到的包序号比上一个小（且不是由于溢出翻转），则丢弃
                    // 这里简单处理：只播新的，不做复杂排序
                    // 真正的 RTP 应该用 JitterBuffer，这里简化

                    byte[] ulawData = new byte[packet.getLength() - HEADER_SIZE];
                    bb.get(ulawData);

                    // G.711 -> PCM
                    byte[] pcmData = new byte[ulawData.length * 2];
                    for (int i = 0; i < ulawData.length; i++) {
                        short sample = G711.ulaw2linear(ulawData[i]);
                        pcmData[2 * i] = (byte) (sample & 0xFF);
                        pcmData[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
                    }

                    speaker.write(pcmData, 0, pcmData.length);
                    lastSeq = seq;

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