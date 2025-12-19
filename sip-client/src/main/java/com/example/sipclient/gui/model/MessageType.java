package com.example.sipclient.gui.model;

/**
 * 消息类型枚举
 */
public enum MessageType {
    TEXT("text"), // 文字消息
    IMAGE("image"), // 图片消息
    AUDIO("audio"), // 语音消息
    VIDEO("video"), // 视频消息
    FILE("file"); // 其他文件

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageType fromValue(String value) {
        for (MessageType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return TEXT;
    }
}
