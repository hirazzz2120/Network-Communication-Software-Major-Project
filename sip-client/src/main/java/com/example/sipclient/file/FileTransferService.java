package com.example.sipclient.file;

import com.example.sipclient.gui.model.MessageType;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * 文件传输服务
 * 通过HTTP上传文件到服务器，通过SIP发送下载链接
 */
public class FileTransferService {

    // 文件存储目录
    private static final String RECEIVED_FILES_DIR = "received_files";

    // 文件消息前缀
    private static final String FILE_MESSAGE_PREFIX = "FILE:";

    // 服务器地址
    private String serverUrl = "http://localhost:8081";

    // 文件大小限制 (字节)
    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final long MAX_AUDIO_SIZE = 5 * 1024 * 1024; // 5MB
    public static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50MB
    public static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    private Consumer<Double> progressCallback;

    public FileTransferService() {
        // 确保接收目录存在
        File dir = new File(RECEIVED_FILES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * 设置进度回调
     */
    public void setProgressCallback(Consumer<Double> callback) {
        this.progressCallback = callback;
    }

    /**
     * 上传文件到服务器
     * 
     * @return 下载URL
     */
    public String uploadFile(File file, MessageType type) throws IOException {
        String uploadUrl = serverUrl + "/api/files/upload";
        String boundary = "===" + System.currentTimeMillis() + "===";

        HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Accept", "application/json");

        try (OutputStream outputStream = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {

            // 添加type参数
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"type\"\r\n\r\n");
            writer.append(type.getValue()).append("\r\n");

            // 添加文件
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(file.getName()).append("\"\r\n");
            writer.append("Content-Type: ").append(getContentType(file)).append("\r\n\r\n");
            writer.flush();

            // 写入文件内容并报告进度
            try (FileInputStream inputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                long fileSize = file.length();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (progressCallback != null) {
                        progressCallback.accept((double) totalRead / fileSize * 0.8); // 80% for upload
                    }
                }
            }

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
        }

        // 读取响应
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // 简单解析JSON获取downloadUrl
                String responseStr = response.toString();
                int urlStart = responseStr.indexOf("\"downloadUrl\":\"") + 15;
                int urlEnd = responseStr.indexOf("\"", urlStart);
                if (urlStart > 14 && urlEnd > urlStart) {
                    String downloadUrl = responseStr.substring(urlStart, urlEnd);
                    if (progressCallback != null) {
                        progressCallback.accept(1.0);
                    }
                    return downloadUrl;
                }
            }
        }

        // 读取错误信息
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getErrorStream()))) {
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line);
            }
            throw new IOException("上传失败 (" + responseCode + "): " + error);
        }
    }

    /**
     * 从URL下载文件
     */
    public File downloadFile(String downloadUrl, String fileName) throws IOException {
        URL url = new URL(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // 确保文件名唯一
            String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
            Path filePath = Paths.get(RECEIVED_FILES_DIR, uniqueFileName);

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            return filePath.toFile();
        } else {
            throw new IOException("下载失败: HTTP " + responseCode);
        }
    }

    /**
     * 判断是否为文件消息（通过消息前缀判断）
     */
    public boolean isFileMessage(String messageContent) {
        return messageContent != null && messageContent.startsWith(FILE_MESSAGE_PREFIX);
    }

    /**
     * 解析文件消息内容
     * 格式: FILE:type:filename:size:downloadUrl
     */
    public FileMessageData parseFileMessage(String messageContent) {
        if (!isFileMessage(messageContent)) {
            return null;
        }

        try {
            // 移除前缀
            String content = messageContent.substring(FILE_MESSAGE_PREFIX.length());

            // 分割: type:filename:size:downloadUrl
            int firstColon = content.indexOf(':');
            int secondColon = content.indexOf(':', firstColon + 1);
            int thirdColon = content.indexOf(':', secondColon + 1);

            if (firstColon == -1 || secondColon == -1 || thirdColon == -1) {
                return null;
            }

            String typeStr = content.substring(0, firstColon);
            String fileName = content.substring(firstColon + 1, secondColon);
            String sizeStr = content.substring(secondColon + 1, thirdColon);
            String downloadUrl = content.substring(thirdColon + 1);

            FileMessageData data = new FileMessageData();
            data.setType(MessageType.fromValue(typeStr));
            data.setFileName(fileName);
            data.setFileSize(Long.parseLong(sizeStr));
            data.setBase64Data(downloadUrl); // 复用此字段存储下载URL

            return data;
        } catch (Exception e) {
            System.err.println("解析文件消息失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 构建文件消息内容（使用HTTP上传）
     * 格式: FILE:type:filename:size:downloadUrl
     */
    public String buildFileMessage(MessageType type, File file) throws IOException {
        // 检查文件大小限制
        long fileSize = file.length();
        long maxSize = getMaxSizeForType(type);

        if (fileSize > maxSize) {
            throw new IOException("文件太大! 最大允许: " + formatSize(maxSize) +
                    ", 当前文件: " + formatSize(fileSize));
        }

        // 上传文件获取下载URL
        String downloadUrl = uploadFile(file, type);

        return String.format("%s%s:%s:%d:%s",
                FILE_MESSAGE_PREFIX,
                type.getValue(),
                file.getName(),
                fileSize,
                downloadUrl);
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

    /**
     * 获取文件的Content-Type
     */
    private String getContentType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            return "image/jpeg";
        if (name.endsWith(".png"))
            return "image/png";
        if (name.endsWith(".gif"))
            return "image/gif";
        if (name.endsWith(".mp4"))
            return "video/mp4";
        if (name.endsWith(".avi"))
            return "video/x-msvideo";
        if (name.endsWith(".mov"))
            return "video/quicktime";
        if (name.endsWith(".wav"))
            return "audio/wav";
        if (name.endsWith(".mp3"))
            return "audio/mpeg";
        return "application/octet-stream";
    }
}
