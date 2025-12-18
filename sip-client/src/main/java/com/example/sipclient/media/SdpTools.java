package com.example.sipclient.media;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SDP 协议工具类
 * 更新：适配 L16/16000 高清音频格式
 */
public class SdpTools {

    /**
     * 生成 SDP
     */
    public static String createSdp(String ipAddress, int audioPort, int videoPort) {
        long id = System.currentTimeMillis();
        StringBuilder sdp = new StringBuilder();

        // 基础信息 (Session Description)
        sdp.append("v=0\r\n");
        sdp.append("o=- ").append(id).append(" ").append(id).append(" IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("s=Talk\r\n");
        sdp.append("c=IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("t=0 0\r\n");

        // --- 音频部分修改 ---
        if (audioPort > 0) {
            // 使用动态 Payload Type 98
            // 声明编码格式为 L16 (16-bit Linear PCM)，采样率 16000Hz
            sdp.append("m=audio ").append(audioPort).append(" RTP/AVP 98\r\n");
            sdp.append("a=rtpmap:98 L16/16000\r\n");
        }

        // --- 视频部分保持不变 ---
        if (videoPort > 0) {
            // 保持原有的 H264/90000 声明，确保视频通话不受影响
            sdp.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
            sdp.append("a=rtpmap:96 H264/90000\r\n");
        }

        return sdp.toString();
    }

    public static String createAudioSdp(String ip, int port) {
        return createSdp(ip, port, 0);
    }

    /**
     * 提取对方 IP (c=IN IP4 xxx.xxx.xxx.xxx)
     */
    public static String getRemoteIp(String sdpContent) {
        if (sdpContent == null) return null;
        Matcher m = Pattern.compile("c=IN IP4 ([\\d\\.]+)").matcher(sdpContent);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 提取音频端口 (m=audio <port>)
     */
    public static int getRemotePort(String sdpContent) {
        return parsePort(sdpContent, "audio");
    }

    /**
     * 提取视频端口 (m=video <port>)
     */
    public static int getRemoteVideoPort(String sdpContent) {
        return parsePort(sdpContent, "video");
    }

    private static int parsePort(String sdp, String mediaType) {
        if (sdp == null) return 0;
        Matcher m = Pattern.compile("m=" + mediaType + " (\\d+)").matcher(sdp);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}