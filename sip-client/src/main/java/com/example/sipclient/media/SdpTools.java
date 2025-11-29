package com.example.sipclient.media;

public class SdpTools {

    /**
     * 生成 SDP (会话描述协议) 字符串
     * 作用：告诉对方我的 IP 和端口，这样对方才能把声音发给我
     */
    public static String createAudioSdp(String ipAddress, int localPort) {
        long id = System.currentTimeMillis();

        // 这是一个标准的 SDP 格式拼装
        // m=audio 端口 ... -> 这行最重要，告诉对方往这个端口发音频

        StringBuilder sdp = new StringBuilder();
        sdp.append("v=0\r\n");
        sdp.append("o=- ").append(id).append(" ").append(id).append(" IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("s=Talk\r\n");
        sdp.append("c=IN IP4 ").append(ipAddress).append("\r\n");
        sdp.append("t=0 0\r\n");
        sdp.append("m=audio ").append(localPort).append(" RTP/AVP 0\r\n");
        sdp.append("a=rtpmap:0 PCMU/8000\r\n");

        return sdp.toString();
    }
// --- 下面是新增的“解析”功能 ---

    /**
     * 从 SDP 文本中提取对方的 IP 地址
     * 寻找类似 "c=IN IP4 192.168.1.5" 的行
     */
    public static String getRemoteIp(String sdpContent) {
        if (sdpContent == null) return null;
        String[] lines = sdpContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("c=") && line.contains("IP4")) {
                // 格式通常是：c=IN IP4 192.168.1.x
                String[] parts = line.split(" ");
                if (parts.length >= 3) {
                    return parts[2];
                }
            }
        }
        return null;
    }

    /**
     * 从 SDP 文本中提取对方的音频端口
     * 寻找类似 "m=audio 5070 RTP/AVP 0" 的行
     */
    public static int getRemotePort(String sdpContent) {
        if (sdpContent == null) return 0;
        String[] lines = sdpContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("m=audio")) {
                // 格式通常是：m=audio 5070 RTP/AVP 0
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    try {
                        return Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return 0;
    }
}