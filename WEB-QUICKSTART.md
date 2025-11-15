# Web 前端快速启动指南

## 前置准备

确保已安装：
- Node.js 18+ 
- npm 或 yarn

## 一键启动（推荐）

### Windows (PowerShell)

创建并运行启动脚本：

```powershell
# 创建启动脚本 start-web.ps1
@"
Write-Host "=== 启动 SIP Web 客户端 ===" -ForegroundColor Cyan
Write-Host ""

# 检查 Node.js
try {
    `$nodeVersion = node -v
    Write-Host "✓ Node.js: `$nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ 未找到 Node.js，请先安装" -ForegroundColor Red
    exit 1
}

# 进入前端目录
cd web-frontend

# 检查依赖
if (-Not (Test-Path "node_modules")) {
    Write-Host "" 
    Write-Host "首次运行，正在安装依赖..." -ForegroundColor Yellow
    npm install
}

Write-Host ""
Write-Host "正在启动开发服务器..." -ForegroundColor Cyan
Write-Host "访问地址: http://localhost:3000" -ForegroundColor Green
Write-Host ""

npm run dev
"@ | Out-File -FilePath start-web.ps1 -Encoding UTF8

# 运行脚本
.\start-web.ps1
```

## 手动启动步骤

### 1. 安装依赖

```bash
cd web-frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

### 3. 访问应用

打开浏览器访问：http://localhost:3000

## 登录测试

### 默认测试账号

使用以下测试参数登录：

```
SIP URI: sip:alice@192.168.1.100:5060
密码: (任意，如果没有真实 MSS 服务器)
本地 IP: 192.168.1.50
本地端口: 5070
```

### 无 MSS 服务器测试

如果没有运行 MSS 服务器，可以：

1. 输入任意 SIP URI 和密码
2. 登录会失败（正常），但可以测试前端界面
3. 或使用 Mock 模式（需要后端开发者提供 Mock 接口）

## 常见问题

### Q1: 端口 3000 被占用

修改 `vite.config.ts`：

```typescript
export default defineConfig({
  server: {
    port: 3001, // 改为其他端口
  },
})
```

### Q2: 后端连接失败

检查：
1. 后端是否在 http://localhost:8081 运行
2. 查看浏览器控制台网络请求
3. 检查 `vite.config.ts` 中的 proxy 配置

### Q3: npm install 失败

尝试：
```bash
# 清除缓存
npm cache clean --force

# 使用国内镜像
npm install --registry=https://registry.npmmirror.com

# 或使用 cnpm
npm install -g cnpm --registry=https://registry.npmmirror.com
cnpm install
```

### Q4: TypeScript 错误

当前依赖未安装时会有 TS 错误，运行 `npm install` 后会自动解决。

## 开发模式功能

- ✅ 热更新（修改代码自动刷新）
- ✅ TypeScript 类型检查
- ✅ ESLint 代码检查
- ✅ 后端 API 代理（无需配置 CORS）

## 构建生产版本

```bash
npm run build
```

生成的文件在 `dist/` 目录，可部署到任何静态服务器。

## 下一步

1. 查看 `API-SPECIFICATION.md` 了解完整 API
2. 阅读 `web-frontend/README.md` 了解项目结构
3. 开始开发或测试功能

## 与后端开发者协作

### 前端需要后端提供

1. **运行后端服务器**
   ```bash
   cd admin-server
   mvn spring-boot:run
   ```

2. **实现 API 接口**（参考 `API-SPECIFICATION.md`）
   - 用户登录：`POST /api/auth/login`
   - 获取用户列表：`GET /api/users`
   - 发送消息：`POST /api/messages`
   - WebSocket 事件推送

3. **配置 CORS**（如果需要）
   - 或使用 Vite 代理（已配置）

### 联调测试

1. 后端启动：`mvn -pl admin-server spring-boot:run`
2. 前端启动：`npm run dev`
3. 打开浏览器控制台查看网络请求
4. 验证接口返回数据格式是否符合 `API-SPECIFICATION.md`

## 技术支持

遇到问题请查看：
- 浏览器控制台（F12）
- 终端错误信息
- `web-frontend/README.md` 详细文档
