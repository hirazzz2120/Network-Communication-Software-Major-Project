package com.example.sipclient.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javax.sound.sampled.*;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * 设置界面控制器
 */
public class SettingsController {

    @FXML private CheckBox notificationCheckBox;
    @FXML private CheckBox soundCheckBox;
    @FXML private Slider volumeSlider;
    @FXML private Label volumeLabel;
    @FXML private ComboBox<String> inputDeviceComboBox;   // 输入设备（麦克风）
    @FXML private ComboBox<String> outputDeviceComboBox;  // 输出设备（扬声器）
    @FXML private CheckBox autoStartCheckBox;
    @FXML private CheckBox saveHistoryCheckBox;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Preferences preferences;
    private Stage stage;
    private Runnable onSettingsChanged;
    private Runnable onAudioDeviceChanged;

    @FXML
    public void initialize() {
        preferences = Preferences.userNodeForPackage(SettingsController.class);
        
        // 检测并初始化音频设备
        loadAudioDevices();
        
        // 输入设备切换监听
        inputDeviceComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                switchInputDevice(newVal);
            }
        });
        
        // 输出设备切换监听
        outputDeviceComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                switchOutputDevice(newVal);
            }
        });
        
        // 音量滑块监听
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            volumeLabel.setText(String.format("%.0f%%", newVal.doubleValue()));
        });
        
        // 加载保存的设置
        loadSettings();
    }

    /**
     * 加载保存的设置
     */
    private void loadSettings() {
        notificationCheckBox.setSelected(preferences.getBoolean("notification", true));
        soundCheckBox.setSelected(preferences.getBoolean("sound", true));
        volumeSlider.setValue(preferences.getDouble("volume", 80.0));
        volumeLabel.setText(String.format("%.0f%%", volumeSlider.getValue()));
        
        // 加载输入和输出设备配置
        String savedInputDevice = preferences.get("inputDevice", "");
        String savedOutputDevice = preferences.get("outputDevice", "");
        
        if (inputDeviceComboBox.getItems().contains(savedInputDevice)) {
            inputDeviceComboBox.setValue(savedInputDevice);
        } else if (!inputDeviceComboBox.getItems().isEmpty()) {
            inputDeviceComboBox.setValue(inputDeviceComboBox.getItems().get(0));
        }
        
        if (outputDeviceComboBox.getItems().contains(savedOutputDevice)) {
            outputDeviceComboBox.setValue(savedOutputDevice);
        } else if (!outputDeviceComboBox.getItems().isEmpty()) {
            outputDeviceComboBox.setValue(outputDeviceComboBox.getItems().get(0));
        }
        
        autoStartCheckBox.setSelected(preferences.getBoolean("autoStart", false));
        saveHistoryCheckBox.setSelected(preferences.getBoolean("saveHistory", true));
    }

    /**
     * 保存设置
     */
    @FXML
    private void handleSave() {
        preferences.putBoolean("notification", notificationCheckBox.isSelected());
        preferences.putBoolean("sound", soundCheckBox.isSelected());
        preferences.putDouble("volume", volumeSlider.getValue());
        
        // 保存输入和输出设备配置
        if (inputDeviceComboBox.getValue() != null) {
            preferences.put("inputDevice", inputDeviceComboBox.getValue());
        }
        if (outputDeviceComboBox.getValue() != null) {
            preferences.put("outputDevice", outputDeviceComboBox.getValue());
        }
        
        preferences.putBoolean("autoStart", autoStartCheckBox.isSelected());
        preferences.putBoolean("saveHistory", saveHistoryCheckBox.isSelected());
        
        // 触发设置变更回调
        if (onSettingsChanged != null) {
            onSettingsChanged.run();
        }
        
        showAlert("保存成功", "设置已保存", Alert.AlertType.INFORMATION);
        closeWindow();
    }

    /**
     * 取消设置
     */
    @FXML
    private void handleCancel() {
        closeWindow();
    }
    
    /**
     * 加载系统音频设备
     */
    private void loadAudioDevices() {
        List<String> inputDevices = getInputDevices();
        List<String> outputDevices = getOutputDevices();
        
        // 加载输入设备
        inputDeviceComboBox.getItems().clear();
        if (inputDevices.isEmpty()) {
            inputDeviceComboBox.getItems().add("系统默认麦克风");
        } else {
            inputDeviceComboBox.getItems().addAll(inputDevices);
        }
        
        // 加载输出设备
        outputDeviceComboBox.getItems().clear();
        if (outputDevices.isEmpty()) {
            outputDeviceComboBox.getItems().add("系统默认扬声器");
        } else {
            outputDeviceComboBox.getItems().addAll(outputDevices);
        }
        
        System.out.println("检测到 " + inputDevices.size() + " 个输入设备，" + outputDevices.size() + " 个输出设备");
    }
    
    /**
     * 获取输入设备（麦克风）列表
     */
    private List<String> getInputDevices() {
        List<String> devices = new ArrayList<>();
        
        try {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, false);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
            
            for (Mixer.Info info : mixerInfos) {
                Mixer mixer = AudioSystem.getMixer(info);
                
                // 检查是否支持目标数据线（可以录音的设备）
                if (mixer.isLineSupported(dataLineInfo)) {
                    String deviceName = info.getName();
                    if (!devices.contains(deviceName) && !deviceName.toLowerCase().contains("port")) {
                        devices.add(deviceName);
                        System.out.println("[输入设备] " + deviceName);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("检测输入设备失败: " + e.getMessage());
        }
        
        return devices;
    }
    
    /**
     * 获取输出设备（扬声器）列表
     */
    private List<String> getOutputDevices() {
        List<String> devices = new ArrayList<>();
        
        try {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, false);
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
            
            for (Mixer.Info info : mixerInfos) {
                Mixer mixer = AudioSystem.getMixer(info);
                
                // 检查是否支持源数据线（可以播放的设备）
                if (mixer.isLineSupported(dataLineInfo)) {
                    String deviceName = info.getName();
                    if (!devices.contains(deviceName) && !deviceName.toLowerCase().contains("port")) {
                        devices.add(deviceName);
                        System.out.println("[输出设备] " + deviceName);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("检测输出设备失败: " + e.getMessage());
        }
        
        return devices;
    }
    
    /**
     * 切换输入设备（麦克风）
     */
    private void switchInputDevice(String deviceName) {
        System.out.println("切换输入设备（麦克风）: " + deviceName);
        preferences.put("inputDevice", deviceName);
        
        // 触发音频设备切换回调
        if (onAudioDeviceChanged != null) {
            onAudioDeviceChanged.run();
        }
    }
    
    /**
     * 切换输出设备（扬声器）
     */
    private void switchOutputDevice(String deviceName) {
        System.out.println("切换输出设备（扬声器）: " + deviceName);
        preferences.put("outputDevice", deviceName);
        
        // 触发音频设备切换回调
        if (onAudioDeviceChanged != null) {
            onAudioDeviceChanged.run();
        }
    }

    /**
     * 重置为默认设置
     */
    @FXML
    private void handleReset() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认重置");
        alert.setHeaderText("重置所有设置");
        alert.setContentText("确定要将所有设置恢复为默认值吗？");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                notificationCheckBox.setSelected(true);
                soundCheckBox.setSelected(true);
                volumeSlider.setValue(80.0);
                
                // 重置为第一个可用设备
                if (!inputDeviceComboBox.getItems().isEmpty()) {
                    inputDeviceComboBox.setValue(inputDeviceComboBox.getItems().get(0));
                }
                if (!outputDeviceComboBox.getItems().isEmpty()) {
                    outputDeviceComboBox.setValue(outputDeviceComboBox.getItems().get(0));
                }
                
                autoStartCheckBox.setSelected(false);
                saveHistoryCheckBox.setSelected(true);
            }
        });
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setOnSettingsChanged(Runnable callback) {
        this.onSettingsChanged = callback;
    }
    
    public void setOnAudioDeviceChanged(Runnable callback) {
        this.onAudioDeviceChanged = callback;
    }

    /**
     * 获取当前输入设备（麦克风）
     */
    public static String getCurrentInputDevice() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        return prefs.get("inputDevice", "");
    }
    
    /**
     * 获取当前输出设备（扬声器）
     */
    public static String getCurrentOutputDevice() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        return prefs.get("outputDevice", "");
    }

    /**
     * 获取音量设置
     */
    public static double getVolume() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        return prefs.getDouble("volume", 80.0);
    }

    /**
     * 是否启用通知
     */
    public static boolean isNotificationEnabled() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        return prefs.getBoolean("notification", true);
    }

    /**
     * 是否启用声音
     */
    public static boolean isSoundEnabled() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        return prefs.getBoolean("sound", true);
    }

    /**
     * 是否保存聊天记录
     */
    public static boolean isHistorySaveEnabled() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        return prefs.getBoolean("saveHistory", true);
    }

    private void closeWindow() {
        if (stage != null) {
            stage.close();
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
