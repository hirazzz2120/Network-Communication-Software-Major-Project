package com.example.sipclient.media;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SdpTools {

    /**
     * 生成 SDP
     */
    public static String createSdp(String ipAddress, int audioPort, int videoPort) {
        long id = System.currentTimeMillis();
        StringBuilder sdp = new StringBuilder();

        // 基础信息
        sdp.append("v=0\r\n");
        sdp.append("o=- ").append(id).append(" ").append(id).append(" IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("s=Talk\r\n");
        sdp.append("c=IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("t=0 0\r\n");

        // 音频 m=audio <port> RTP/AVP 0
        if (audioPort > 0) {
            sdp.append("m=audio ").append(audioPort).append(" RTP/AVP 0\r\n");
            sdp.append("a=rtpmap:0 PCMU/8000\r\n");
        }

        // 视频 m=video <port> RTP/AVP 96
        if (videoPort > 0) {
            sdp.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
            sdp.append("a=rtpmap:96 H264/90000\r\n"); // 虽然我们发的是 JPG, 但信令里写 H264 是为了兼容某些客户端逻辑
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
        // 优先找全局连接信息 c=
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
        // 匹配 m=video 12345 ...
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