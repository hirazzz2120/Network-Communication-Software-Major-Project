package com.example.sipclient.file;

import com.example.sipclient.gui.model.MessageType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * 文件传输服务
 * 处理文件编码、解码和传输
 */
public class FileTransferService {

    // 文件存储目录
    private static final String RECEIVED_FILES_DIR = "received_files";

    // 文件消息前缀
    private static final String FILE_MESSAGE_PREFIX = "FILE:";

    // 文件大小限制 (字节)
    public static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024; // 2MB
    public static final long MAX_AUDIO_SIZE = 1 * 1024 * 1024; // 1MB (约1分钟)
    public static final long MAX_VIDEO_SIZE = 5 * 1024 * 1024; // 5MB
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private Consumer<Double> progressCallback;

    public FileTransferService() {
        // 确保接收目录存在
        File dir = new File(RECEIVED_FILES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 设置进度回调
     */
    public void setProgressCallback(Consumer<Double> callback) {
        this.progressCallback = callback;
    }

    /**
     * 将文件编码为Base64用于SIP MESSAGE传输
     */
    public String encodeFile(File file) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(fileBytes);
    }

    /**
     * 从Base64解码保存文件
     */
    public File decodeAndSave(String base64Data, String fileName) throws IOException {
        byte[] fileBytes = Base64.getDecoder().decode(base64Data);

        // 确保文件名唯一
        String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
        Path filePath = Paths.get(RECEIVED_FILES_DIR, uniqueFileName);

        // 写入文件并报告进度
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            int totalLength = fileBytes.length;
            int chunkSize = 8192;
            int written = 0;

            while (written < totalLength) {
                int remaining = totalLength - written;
                int toWrite = Math.min(chunkSize, remaining);
                fos.write(fileBytes, written, toWrite);
                written += toWrite;

                if (progressCallback != null) {
                    progressCallback.accept((double) written / totalLength);
                }
            }
        }

        return filePath.toFile();
    }

    /**
     * 判断是否为文件消息（通过消息前缀判断）
     */
    public boolean isFileMessage(String messageContent) {
        return messageContent != null && messageContent.startsWith(FILE_MESSAGE_PREFIX);
    }

    /**
     * 解析文件消息内容
     * 格式: FILE:type:filename:size:base64data
     */
    public FileMessageData parseFileMessage(String messageContent) {
        if (!isFileMessage(messageContent)) {
            return null;
        }

        try {
            // 移除前缀
            String content = messageContent.substring(FILE_MESSAGE_PREFIX.length());

            // 分割: type:filename:size:base64data
            int firstColon = content.indexOf(':');
            int secondColon = content.indexOf(':', firstColon + 1);
            int thirdColon = content.indexOf(':', secondColon + 1);

            if (firstColon == -1 || secondColon == -1 || thirdColon == -1) {
                return null;
            }

            String typeStr = content.substring(0, firstColon);
            String fileName = content.substring(firstColon + 1, secondColon);
            String sizeStr = content.substring(secondColon + 1, thirdColon);
            String base64Data = content.substring(thirdColon + 1);

            FileMessageData data = new FileMessageData();
            data.setType(MessageType.fromValue(typeStr));
            data.setFileName(fileName);
            data.setFileSize(Long.parseLong(sizeStr));
            data.setBase64Data(base64Data);

            return data;
        } catch (Exception e) {
            System.err.println("解析文件消息失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 构建文件消息内容
     * 格式: FILE:type:filename:size:base64data
     */
    public String buildFileMessage(MessageType type, File file) throws IOException {
        // 检查文件大小限制
        long fileSize = file.length();
        long maxSize = getMaxSizeForType(type);

        if (fileSize > maxSize) {
            throw new IOException("文件太大! 最大允许: " + formatSize(maxSize) +
                    ", 当前文件: " + formatSize(fileSize));
        }

        String base64Data = encodeFile(file);

        return String.format("%s%s:%s:%d:%s",
                FILE_MESSAGE_PREFIX,
                type.getValue(),
                file.getName(),
                fileSize,
                base64Data);
    }

    /**
     * 带进度回调的文件编码
     */
    public String encodeFileWithProgress(File file, Consumer<Double> progress) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        // 模拟进度（Base64编码很快，主要是读取文件）
        if (progress != null) {
            progress.accept(0.5);
        }

        String encoded = Base64.getEncoder().encodeToString(fileBytes);

        if (progress != null) {
            progress.accept(1.0);
        }

        return encoded;
    }

    /**
     * 获取指定类型的最大文件大小
     */
    public long getMaxSizeForType(MessageType type) {
        return switch (type) {
            case IMAGE -> MAX_IMAGE_SIZE;
            case AUDIO -> MAX_AUDIO_SIZE;
            case VIDEO -> MAX_VIDEO_SIZE;
            default -> MAX_FILE_SIZE;
        };
    }

    /**
     * 格式化文件大小
     */
    public String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * 获取接收文件目录
     */
    public String getReceivedFilesDir() {
        return RECEIVED_FILES_DIR;
    }

    /**
     * 根据文件扩展名判断消息类型
     */
    public MessageType getMessageTypeFromFile(File file) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || name.endsWith(".webp")) {
            return MessageType.IMAGE;
        }

        if (name.endsWith(".wav") || name.endsWith(".mp3") ||
                name.endsWith(".m4a") || name.endsWith(".aac") ||
                name.endsWith(".ogg") || name.endsWith(".flac")) {
            return MessageType.AUDIO;
        }

        if (name.endsWith(".mp4") || name.endsWith(".avi") ||
                name.endsWith(".mov") || name.endsWith(".mkv") ||
                name.endsWith(".wmv") || name.endsWith(".flv")) {
            return MessageType.VIDEO;
        }

        return MessageType.FILE;
    }
}
