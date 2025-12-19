package com.example.sipclient.file;

import com.example.sipclient.gui.model.MessageType;

/**
 * 文件消息数据包装类
 */
public class FileMessageData {
    private MessageType type;
    private String fileName;
    private long fileSize;
    private String base64Data;

    public FileMessageData() {
    }

    public FileMessageData(MessageType type, String fileName, long fileSize, String base64Data) {
        this.type = type;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.base64Data = base64Data;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getBase64Data() {
        return base64Data;
    }

    public void setBase64Data(String base64Data) {
        this.base64Data = base64Data;
    }
}
