# SIP 通信项目

> 基于 JAIN SIP + Spring Boot 的前后端分离即时通信系统  
> 支持 SIP 注册、消息收发、语音呼叫与后台管理

## 项目简介

本项目是一个 **课程实践项目**，目标是实现类似 Linphone 的 SIP 通信客户端，包含即时消息、语音/视频呼叫、群聊和后台管理功能。采用 **前后端分离架构**：
- **前端**：可以是命令行界面（已实现）、桌面 GUI（JavaFX/Swing）或 Web 前端（React/Vue + WebRTC）
- **后端**：Spring Boot REST API + JAIN SIP 协议栈，对接 MSS（Mobicents SIP Server）

## 技术栈

| 模块 | 技术 | 版本 |
|------|------|------|
| 构建工具 | Maven | 3.x |
| JDK | OpenJDK | 17 |
| SIP 协议栈 | JAIN SIP (jain-sip-ri) | 1.3.0-91 |
| SIP 服务器 | MSS (Mobicents) | - |
| 后端框架 | Spring Boot | 3.2.5 |
| 前端框架 | React + TypeScript | 18.2 / 5.2 ⭐ |
| 前端构建 | Vite | 5.0 ⭐ |
| UI 组件库 | Ant Design | 5.12 ⭐ |
| 状态管理 | Zustand | 4.4 ⭐ |
| 日志框架 | SLF4J + Logback | 2.0.12 / 1.5.6 |
| 测试框架 | JUnit 5 + Mockito | 5.10.2 / 5.11.0 |

## 项目结构

```
Project/
├── pom.xml                    # 父 POM，管理依赖版本
├── sip-client/                # SIP 客户端模块（核心协议实现）
│   ├── src/main/java/
│   │   └── com/example/sipclient/
│   │       ├── sip/           # SIP 协议层（SipUserAgent）
│   │       ├── call/          # 呼叫管理（CallManager, CallSession）
│   │       ├── chat/          # 消息处理（MessageHandler, ChatSession）
│   │       ├── media/         # 媒体会话（AudioSession - 占位实现）
│   │       ├── config/        # 配置类（SipConfig）
│   │       └── ui/            # 用户界面（ConsoleMain - CLI）
│   └── pom.xml
├── admin-server/              # 后台管理模块（REST API）
│   ├── src/main/java/
│   │   └── com/example/admin/
│   │       ├── controller/    # REST 控制器（用户、通话记录、统计）
│   │       ├── service/       # 业务逻辑层
│   │       ├── entity/        # 数据模型（DTO）
│   │       └── repository/    # 数据访问层（当前为内存存储）
│   └── pom.xml
├── web-frontend/              # Web 前端模块（React + TypeScript）⭐ 新增
│   ├── src/
│   │   ├── api/               # API 请求封装
│   │   ├── components/        # React 组件
│   │   ├── pages/             # 页面组件
│   │   ├── stores/            # 状态管理（Zustand）
│   │   ├── services/          # 业务服务（WebSocket）
│   │   └── types/             # TypeScript 类型定义
│   ├── package.json
│   └── README.md
├── docs/
│   ├── guidance.md            # 开发指导文档（SRS）
│   └── requirements-week2.md  # 第2周需求说明
├── API-SPECIFICATION.md       # 前后端 API 接口规范 ⭐ 新增
├── HANDOVER.md                # 项目交接文档（给组员）⭐ 新增
├── WEB-QUICKSTART.md          # Web 前端快速启动 ⭐ 新增
├── QUICKSTART.md              # 快速测试指南
├── run-sip-client.ps1         # PowerShell 启动脚本
└── start-web.ps1              # Web 前端一键启动 ⭐ 新增
```

## 当前功能状态

### ✅ 已完成
- [x] Maven 多模块项目结构
- [x] SIP REGISTER/UNREGISTER（支持摘要认证 401/407）
- [x] SIP MESSAGE 收发（点对点消息）
- [x] SIP INVITE/ACK/BYE 呼叫信令流程
- [x] 呼叫会话状态管理（IDLE/RINGING/ACTIVE/TERMINATED）
- [x] 消息历史记录（最近20条/会话）
- [x] 命令行交互界面（ConsoleMain）
- [x] Spring Boot REST API 骨架（用户、通话记录、统计）
- [x] 单元测试（CallManager、MessageHandler - 4个测试通过）
- [x] 日志框架集成（Logback）

### 🔄 部分完成
- [ ] 媒体处理（AudioSession 仅为占位实现，无真实 RTP/音频）
- [ ] 群聊功能（GroupChatService 结构存在但未完全集成）
- [ ] 后台管理前端界面（仅 REST API，无 Web UI）
- [ ] 数据持久化（当前使用内存存储）

### ❌ 待开发
- [ ] 真实音频采集与播放（Java Sound / WebRTC）
- [ ] 视频通话支持
- [ ] 多方会议/混音
- [ ] NAT 穿透（STUN/TURN/ICE）
- [ ] Web 前端（React/Vue）
- [ ] 数据库集成（MySQL/PostgreSQL）
- [ ] 文件传输
- [ ] 推送通知

---

## 快速开始

> 💡 **组员快速上手**：如果你刚加入项目，请先阅读 [`HANDOVER.md`](HANDOVER.md) 获取完整的交接指南！

### 环境准备

1. **JDK 17**
   ```powershell
   java -version  # 确认输出 "17.x.x"
   ```

2. **Maven 3.x**
   ```powershell
   mvn -v  # 确认 Maven 版本
   ```

3. **Node.js 18+**（用于 Web 前端）⭐
   ```powershell
   node -v  # 确认版本 >= 18
   npm -v   # 确认 npm 可用
   ```

4. **MSS 服务器**（可选，用于真实 SIP 注册测试）
   - 下载并启动 Mobicents SIP Servlets
   - 或使用公网测试 SIP 服务器（如 sip2sip.info）

### 方式 1：一键启动（推荐）

使用 PowerShell 脚本快速启动 SIP 客户端：

```powershell
.\run-sip-client.ps1
```

脚本会自动检查环境并启动命令行界面。

### 方式 2：手动启动

#### 启动 SIP 客户端（命令行模式）

```powershell
mvn -pl sip-client exec:java "-Dexec.mainClass=com.example.sipclient.ui.ConsoleMain" "-Dexec.cleanupDaemonThreads=false"
```

**交互流程：**
1. 输入 SIP URI（例：`sip:alice@192.168.1.100:5060`）
2. 输入密码
3. 输入本地 IP（用于接收 SIP 消息的地址）
4. 输入本地端口（例：`5070`）
5. 等待注册完成
6. 使用命令菜单进行操作

**可用命令：**
- `help` - 显示帮助
- `msg <sip:target@host> <消息内容>` - 发送消息
- `call <sip:target@host>` - 发起语音呼叫
- `hangup <sip:target@host>` - 挂断呼叫
- `unregister` - 注销登录
- `exit` - 退出程序

#### 启动后台管理服务器

```powershell
cd admin-server
mvn spring-boot:run
```

服务器默认运行在 `http://localhost:8081`

**可用接口：**
- `GET /api/users` - 查询用户列表及在线状态
- `GET /api/calls` - 查看通话记录
- `GET /api/stats` - 查看统计数据
- `GET /api/dashboard` - 获取仪表盘快照
- `GET /stream/dashboard` - SSE 实时推送（未完全实现）

示例：
```powershell
curl http://localhost:8081/api/users
curl http://localhost:8081/api/stats
```

### 方式 3：Web 前端（推荐用于演示）⭐

> 💡 **详细教程**：查看 [`WEB-QUICKSTART.md`](WEB-QUICKSTART.md) 或使用一键启动脚本 `.\start-web.ps1`

#### 第一步：启动后端服务器

```powershell
cd admin-server
mvn spring-boot:run
```

#### 第二步：启动 Web 前端

```powershell
cd web-frontend
npm install  # 首次运行需要安装依赖
npm run dev
```

访问 http://localhost:3000 使用 Web 界面。

### 方式 4：打包后运行

```powershell
# 编译打包
mvn clean package

# 运行 SIP 客户端
java -cp sip-client/target/sip-client-1.0.0-SNAPSHOT.jar com.example.sipclient.ui.ConsoleMain

# 运行后台服务器
java -jar admin-server/target/admin-server-1.0.0-SNAPSHOT.jar
```

---

## 前后端分离开发指南

### 前端开发者关注点

如果你负责前端开发（Web UI 或桌面 GUI），应关注以下内容：

#### 需要理解的模块
- **`sip-client/src/main/java/com/example/sipclient/ui/`**
  - `ConsoleMain.java` - 当前 CLI 实现，展示用户交互流程
  - 未来可替换为 JavaFX/Swing GUI 或通过 REST API 对接 Web 前端

- **`sip-client/src/main/java/com/example/sipclient/config/`**
  - `SipConfig.java` - SIP 配置参数（服务器地址、端口、传输协议等）
  - 前端需要提供配置界面让用户输入这些参数

- **`sip-client/src/main/java/com/example/sipclient/chat/`**
  - `MessageHandler.java` - 消息收发接口
  - `ChatSession.java` - 聊天会话模型
  - 前端需要展示消息列表、会话历史

- **`sip-client/src/main/java/com/example/sipclient/call/`**
  - `CallManager.java` - 呼叫管理器
  - `CallSession.java` - 呼叫状态机（RINGING/ACTIVE/TERMINATED）
  - 前端需要显示来电弹窗、通话界面、呼叫状态

#### 与后端的 API 约定

**⭐ 完整的 API 接口规范请查看 `API-SPECIFICATION.md`**

主要接口概览：

**认证与用户**
- `POST /api/auth/login` - 登录（SIP 注册）
- `POST /api/auth/logout` - 注销
- `GET /api/auth/profile` - 获取当前用户信息
- `PUT /api/auth/status` - 更新用户状态

**消息**
- `POST /api/messages` - 发送消息
- `GET /api/messages/sessions` - 获取会话列表
- `GET /api/messages/sessions/{sessionId}` - 获取会话历史
- `PUT /api/messages/{messageId}/read` - 标记已读

**呼叫**
- `POST /api/calls` - 发起呼叫
- `PUT /api/calls/{callId}/answer` - 接听呼叫
- `PUT /api/calls/{callId}/reject` - 拒绝呼叫
- `DELETE /api/calls/{callId}` - 挂断呼叫
- `GET /api/calls/history` - 获取通话历史

**联系人**
- `GET /api/users` - 获取用户列表
- `GET /api/users/{userId}` - 获取用户详情

**WebSocket 实时事件**
- `ws://localhost:8081/ws/events?token={jwt}` - WebSocket 连接
- 事件类型：MESSAGE_RECEIVED, INCOMING_CALL, CALL_STATE_CHANGED, USER_STATUS_CHANGED 等

#### 推荐的前端技术栈
- **Web 前端**：React/Vue + WebRTC（用于媒体流）+ Axios（HTTP）+ WebSocket
- **桌面前端**：JavaFX（与现有 Java 代码集成方便）或 Electron（跨平台）

#### 前端开发起步
1. 阅读 `ConsoleMain.java` 理解交互流程
2. 与后端开发者约定 API 接口格式（可参考上述建议）
3. 使用 Mock 数据或 `admin-server` 提供的示例接口进行前端开发
4. 实现登录、联系人、聊天、来电界面
5. 集成 WebRTC 处理媒体流（如需浏览器端通话）

### 后端开发者关注点

如果你负责后端开发，应关注以下内容：

#### 核心模块
- **`sip-client/src/main/java/com/example/sipclient/sip/`**
  - `SipUserAgent.java` - SIP 协议核心实现
  - 实现 REGISTER、MESSAGE、INVITE/ACK/BYE 等 SIP 方法
  - 处理 401/407 摘要认证
  - 可以将此封装为服务供前端调用

- **`admin-server/src/main/java/com/example/admin/`**
  - `controller/` - REST API 控制器层
  - `service/` - 业务逻辑层
  - `entity/` - 数据模型
  - `repository/` - 数据访问层（当前内存实现，需迁移到数据库）

#### 后端任务清单
1. **完善 REST API**
   - 实现用户认证（JWT token）
   - 实现消息发送/接收接口
   - 实现呼叫控制接口
   - 添加 WebSocket 支持实时事件推送

2. **数据持久化**
   - 集成 Spring Data JPA
   - 配置 MySQL/PostgreSQL 数据库
   - 实现用户、消息、通话记录的持久化

3. **SIP 集成**
   - 将 `SipUserAgent` 封装为 Spring Bean
   - 提供注册、消息、呼叫的服务接口
   - 处理 SIP 事件并通过 WebSocket 推送给前端

4. **媒体处理**
   - 研究 RTP 协议实现真实音频传输
   - 或集成 WebRTC 网关（如 Janus）作为 SIP-WebRTC 桥接

---

## 测试指南

### 单元测试

```powershell
# 运行所有测试
mvn clean test

# 运行特定模块测试
mvn -pl sip-client test
mvn -pl admin-server test
```

当前测试覆盖：
- `CallManagerTest` - 呼叫会话管理测试（2个测试）
- `MessageHandlerTest` - 消息处理测试（2个测试）

### 集成测试

详见 `QUICKSTART.md` 中的完整测试流程。

**快速测试场景：**

1. **注册测试**（需要 MSS 服务器）
   - 启动 SIP 客户端
   - 输入真实 SIP 账号信息
   - 观察注册成功/失败日志

2. **消息测试**（需要两个客户端）
   - 启动两个 SIP 客户端实例（不同端口）
   - 在客户端 A 中执行：`msg sip:bob@server <消息>`
   - 在客户端 B 中观察是否收到消息

3. **呼叫测试**
   - 在客户端 A 中执行：`call sip:bob@server`
   - 观察 INVITE 发送和状态变化
   - 当前媒体为占位实现，只会打印日志

### 故障排查

常见问题请参考 `QUICKSTART.md` 中的 **故障排查** 章节。

---

## 开发路线图

### 第 2-3 周（当前）
- [x] 完善 SIP 注册与消息功能
- [x] 完成呼叫信令流程
- [ ] 设计并约定前后端 API 接口
- [ ] 前端选型与项目初始化

### 第 4 周
- [ ] 实现真实音频采集与播放（Java Sound）
- [ ] 前端实现登录、联系人、聊天界面
- [ ] 后端实现 JWT 认证与 WebSocket 推送

### 第 5 周
- [ ] 集成数据库（MySQL）
- [ ] 实现群聊功能
- [ ] 前端实现来电/通话界面

### 第 6 周
- [ ] 系统集成测试
- [ ] 性能优化与 bug 修复
- [ ] 完善文档与演示

---

## 文档索引

### 🚀 团队协作必读
- **`HANDOVER.md`** ⭐⭐⭐⭐⭐ - **项目交接文档**（给组员的快速上手指南）
- **`API-SPECIFICATION.md`** ⭐⭐⭐⭐⭐ - **前后端 API 接口规范**（前后端开发必读）
- **`web-frontend/README.md`** ⭐⭐⭐⭐⭐ - Web 前端开发文档
- **`WEB-QUICKSTART.md`** ⭐⭐⭐⭐ - Web 前端快速启动教程

### 📚 开发与测试
- **`QUICKSTART.md`** - 快速测试指南（命令、参数、故障排查）
- **`docs/guidance.md`** - 完整的软件需求规格说明（SRS）
- **`docs/requirements-week2.md`** - 第2周需求说明与进度规划

---

## 团队协作建议

### Git 工作流
```powershell
# 前端开发分支
git checkout -b feature/web-frontend

# 后端开发分支
git checkout -b feature/backend-api

# 定期合并到主分支
git checkout main
git merge feature/web-frontend
```

### 代码规范
- 遵循 Java 代码规范（Google Java Style Guide）
- 提交前运行 `mvn clean test` 确保测试通过
- 提交信息格式：`[模块] 简短描述`
  - 例：`[sip-client] 修复注册超时问题`
  - 例：`[admin-server] 添加用户认证接口`

### 沟通协作
- 定期同步前后端 API 接口变更
- 使用 Postman/Swagger 文档化 API
- 遇到阻塞问题及时沟通，避免重复工作

---

## 常见问题 FAQ

### Q: 为什么注册失败？
A: 检查以下几点：
1. MSS 服务器是否启动并可访问
2. SIP URI 格式是否正确（`sip:user@host:port`）
3. 本地 IP 和端口是否被防火墙阻止
4. 查看日志中的错误信息

### Q: 如何在浏览器中实现通话？
A: 需要集成 WebRTC：
1. 后端实现 SIP-WebRTC 网关（或使用 Janus/Kurento）
2. 前端使用 WebRTC API 采集/播放媒体
3. 通过信令服务器（WebSocket）交换 SDP/ICE

### Q: 数据库配置在哪里？
A: 当前使用内存存储，数据库配置需添加到：
- `admin-server/src/main/resources/application.properties`
- 或 `application.yml`

### Q: 如何打包发布？
A: 使用 Maven Shade 插件打成 fat JAR：
```powershell
mvn clean package
# JAR 位于 target/ 目录
```

---

## 贡献者

- 团队成员 1 - 前端开发
- 团队成员 2 - 后端开发  
- 团队成员 3 - SIP 协议与媒体
- 团队成员 4 - 测试与文档

---

## 许可证

本项目仅用于课程学习，未设置开源许可证。

---

## 联系方式

项目仓库：https://github.com/huanxu123/sip  
问题反馈：请通过 GitHub Issues 提交
