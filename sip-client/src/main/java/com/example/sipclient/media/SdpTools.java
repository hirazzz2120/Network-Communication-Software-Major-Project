package com.example.sipclient.media;

public class SdpTools {

    /**
     * 生成 SDP (兼容旧代码)
     */
    public static String createAudioSdp(String ipAddress, int localPort) {
        return createSdp(ipAddress, localPort, 0);
    }

    /**
     * [核心] 生成包含 音频 + 视频 的 SDP
     * @param ipAddress 本地IP
     * @param audioPort 音频端口
     * @param videoPort 视频端口 (如果为0，则不生成视频描述)
     */
    public static String createSdp(String ipAddress, int audioPort, int videoPort) {
        long id = System.currentTimeMillis();
        StringBuilder sdp = new StringBuilder();

        // 1. 基础信息
        sdp.append("v=0\r\n");
        sdp.append("o=- ").append(id).append(" ").append(id).append(" IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("s=Talk\r\n");
        sdp.append("c=IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("t=0 0\r\n");

        // 2. 音频部分
        if (audioPort > 0) {
            sdp.append("m=audio ").append(audioPort).append(" RTP/AVP 0\r\n");
            sdp.append("a=rtpmap:0 PCMU/8000\r\n");
        }

        // 3. 视频部分
        if (videoPort > 0) {
            sdp.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
            sdp.append("a=rtpmap:96 H264/90000\r\n");
        }

        return sdp.toString();
    }

    /**
     * 提取对方 IP
     */
    public static String getRemoteIp(String sdpContent) {
        if (sdpContent == null) return null;
        String[] lines = sdpContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("c=") && line.contains("IP4")) {
                String[] parts = line.split(" ");
                if (parts.length >= 3) return parts[2];
            }
        }
        return null;
    }

    /**
     * 提取对方音频端口
     */
    public static int getRemotePort(String sdpContent) {
        if (sdpContent == null) return 0;
        String[] lines = sdpContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("m=audio")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    try { return Integer.parseInt(parts[1]); } catch (Exception e) {}
                }
            }
        }
        return 0;
    }

    /**
     * 提取对方视频端口
     */
    public static int getRemoteVideoPort(String sdpContent) {
        if (sdpContent == null) return 0;
        String[] lines = sdpContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("m=video")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    try { return Integer.parseInt(parts[1]); } catch (Exception e) {}
                }
            }
        }
        return 0;
    }
}