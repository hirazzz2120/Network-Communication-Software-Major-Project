Write-Host ""
Write-Host "=== 启动 SIP Web 客户端 ===" -ForegroundColor Cyan
Write-Host ""

# 检查 Node.js
try {
    $nodeVersion = node -v
    Write-Host "✓ Node.js: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ 未找到 Node.js，请先安装 Node.js 18+" -ForegroundColor Red
    Write-Host "  下载地址: https://nodejs.org/" -ForegroundColor Yellow
    pause
    exit 1
}

# 检查 npm
try {
    $npmVersion = npm -v
    Write-Host "✓ npm: $npmVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ npm 不可用" -ForegroundColor Red
    pause
    exit 1
}

Write-Host ""

# 进入前端目录
if (-Not (Test-Path "web-frontend")) {
    Write-Host "✗ 未找到 web-frontend 目录" -ForegroundColor Red
    Write-Host "  请在项目根目录运行此脚本" -ForegroundColor Yellow
    pause
    exit 1
}

cd web-frontend

# 检查依赖
if (-Not (Test-Path "node_modules")) {
    Write-Host "首次运行，正在安装依赖..." -ForegroundColor Yellow
    Write-Host "这可能需要几分钟时间，请耐心等待..." -ForegroundColor Yellow
    Write-Host ""
    npm install
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "✗ 依赖安装失败" -ForegroundColor Red
        Write-Host "  尝试使用国内镜像:" -ForegroundColor Yellow
        Write-Host "  npm install --registry=https://registry.npmmirror.com" -ForegroundColor Cyan
        pause
        exit 1
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   Web 前端已启动！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "访问地址:" -ForegroundColor Cyan
Write-Host "  http://localhost:3000" -ForegroundColor Green
Write-Host ""
Write-Host "提示:" -ForegroundColor Yellow
Write-Host "  - 确保后端服务器已启动 (端口 8081)" -ForegroundColor Yellow
Write-Host "  - 按 Ctrl+C 可停止服务器" -ForegroundColor Yellow
Write-Host ""

npm run dev
