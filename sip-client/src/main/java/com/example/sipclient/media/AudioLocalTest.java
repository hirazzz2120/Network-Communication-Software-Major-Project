package com.example.sipclient.media;

public class AudioLocalTest {
    public static void main(String[] args) throws InterruptedException {
        // 1. 创建音频会话对象
        AudioSession session = new AudioSession();

        System.out.println("=========================================");
        System.out.println(">>> 音频回环测试 (G.711 压缩版) <<<");
        System.out.println("1. 声音将被压缩成 PCMU 格式发送");
        System.out.println("2. 然后被解压播放");
        System.out.println("3. 请对着麦克风说话，你应该能听到回音（电话音质）");
        System.out.println("=========================================");

        // 2. 启动通话！
        // 参数含义：目标IP(自己), 目标端口(55555), 本地监听端口(55555)
        // 这样声音发出去又会发回给自己
        session.start("127.0.0.1", 55555, 55555);

        // 3. 让程序运行 20 秒，让你有时间说话
        // 如果你能听到声音，说明你的 G.711 压缩和解压逻辑都是对的！
        Thread.sleep(20000);

        // 4. 停止测试
        session.stop();
        System.out.println(">>> 测试结束 <<<");
    }
}