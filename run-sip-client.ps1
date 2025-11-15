# 快速启动脚本
# 使用方法: .\run-sip-client.ps1

Write-Host ""
Write-Host "=== SIP 客户端快速启动 ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示：" -ForegroundColor Yellow
Write-Host "  - 如果没有真实的 MSS 服务器，可以输入任意值测试界面" -ForegroundColor Yellow
Write-Host "  - 注册会超时失败（正常现象），但可以测试命令交互" -ForegroundColor Yellow
Write-Host "  - 输入 'help' 查看所有可用命令" -ForegroundColor Yellow
Write-Host ""

# 检查 JDK
try {
    $javaVersion = java -version 2>&1 | Select-String "version" | Select-Object -First 1
    Write-Host "✓ Java: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ 未找到 Java，请安装 JDK 17" -ForegroundColor Red
    exit 1
}

# 检查 Maven
try {
    $mvnVersion = mvn -v 2>&1 | Select-String "Apache Maven" | Select-Object -First 1
    Write-Host "✓ Maven: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ 未找到 Maven，请先安装" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "正在启动 SIP 客户端..." -ForegroundColor Cyan
Write-Host ""

# 启动应用
& mvn -pl sip-client -q exec:java "-Dexec.mainClass=com.example.sipclient.ui.ConsoleMain" "-Dexec.cleanupDaemonThreads=false"
