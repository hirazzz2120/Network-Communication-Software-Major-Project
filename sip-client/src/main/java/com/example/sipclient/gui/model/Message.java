package com.example.sipclient.gui.model;

import java.time.LocalDateTime;

/**
 * 消息模型 - 支持多媒体消息
 */
public class Message {
    private String content;
    private boolean fromMe;
    private LocalDateTime timestamp;
    private MessageType messageType;
    private String fileName;
    private String filePath;
    private long fileSize;

    /**
     * 文字消息构造函数（兼容现有代码）
     */
    public Message(String content, boolean fromMe, LocalDateTime timestamp) {
        this.content = content;
        this.fromMe = fromMe;
        this.timestamp = timestamp;
        this.messageType = MessageType.TEXT;
    }

    /**
     * 多媒体消息构造函数
     */
    public Message(String content, boolean fromMe, LocalDateTime timestamp,
            MessageType messageType, String fileName, String filePath, long fileSize) {
        this.content = content;
        this.fromMe = fromMe;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }

    public String getContent() {
        return content;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * 判断是否为文件类型消息
     */
    public boolean isFileMessage() {
        return messageType != null && messageType != MessageType.TEXT;
    }

    /**
     * 获取格式化的文件大小
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
}
