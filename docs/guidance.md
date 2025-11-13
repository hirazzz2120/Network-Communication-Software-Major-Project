# SIP 即时通信与音视频通话系统 - 软件需求规格说明书（SRS）

**项目名称**：基于 MSS + JAIN SIP 的即时通信与通话系统  
**文档版本**：1.0  
**编制日期**：2025-11-13  
**目标 JDK**：Java 17  
**构建工具**：Maven 3.9+  

---

## 1. 引言

### 1.1 文档目的
本文档是项目的软件需求规格说明书（Software Requirements Specification, SRS），用于：
- 明确系统功能需求、非功能需求与约束条件
- 指导开发团队（含 AI 编程助手）进行设计、编码与测试
- 作为项目验收与质量评审的依据

### 1.2 项目背景
本项目旨在开发一个**类似 Linphone** 的基于 **SIP 协议**的即时通信与音视频通话软件，满足课程实训或企业内部通信需求。系统采用客户端-服务器架构，核心组件包括：
- **SIP 信令服务器**：Mobicents SIP Server（MSS）
- **客户端**：基于 Java 的桌面应用（支持命令行与图形界面）
- **管理后台**：Spring Boot Web 应用（用户管理、统计报表）

### 1.3 术语与缩略语
| 术语 | 含义 |
|------|------|
| SIP | Session Initiation Protocol，会话初始协议 |
| MSS | Mobicents SIP Server，开源 SIP 服务器 |
| JAIN SIP | Java API for Integrated Networks - SIP 协议栈 |
| UAC/UAS | User Agent Client / User Agent Server（SIP 用户代理） |
| REGISTER | SIP 注册请求方法 |
| INVITE | SIP 会话邀请请求方法 |
| MESSAGE | SIP 即时消息请求方法 |
| RTP | Real-time Transport Protocol，实时传输协议 |
| SDP | Session Description Protocol，会话描述协议 |
| NAT | Network Address Translation，网络地址转换 |
| SSE | Server-Sent Events，服务器推送事件 |

### 1.4 参考资料
- RFC 3261: SIP - Session Initiation Protocol
- RFC 3264: SDP Offer/Answer Model
- JAIN SIP API Specification v1.2
- Mobicents SIP Servlets Documentation

---

## 2. 项目概述

### 2.1 产品定位
本系统为**企业级即时通信与音视频通话平台**，提供：
- 用户间点对点文本消息与语音/视频通话
- 多方群聊与会议通话（可简化实现）
- 管理后台监控与统计

### 2.2 目标用户
- **终端用户**：企业员工、学生、需要内部通信的组织成员
- **管理员**：IT 管理员、系统运维人员

### 2.3 使用场景
1. **用户注册与登录**：新用户通过 SIP URI 与密码在 MSS 上注册
2. **即时消息**：用户 A 向用户 B 发送文本消息并接收回复
3. **语音通话**：用户 A 呼叫用户 B，双方建立语音通道并通话
4. **管理监控**：管理员通过 Web 查看在线用户、通话记录、消息统计

---

## 3. 功能需求

### 3.1 用户管理模块（FR-UM）

#### FR-UM-01：用户注册
- **描述**：用户通过 SIP REGISTER 请求向 MSS 注册账号
- **输入**：SIP URI（如 sip:alice@example.com）、密码、本地 IP/端口
- **处理**：
  1. 客户端构建 REGISTER 请求并发送至 MSS
  2. 处理 401/407 认证挑战（Digest Authentication）
  3. 接收 200 OK 确认注册成功
- **输出**：注册状态（成功/失败）、Expires 时间
- **验收标准**：能成功注册并在 MSS 上保持在线状态

#### FR-UM-02：用户注销
- **描述**：用户主动注销或客户端关闭时发送 REGISTER（Expires=0）
- **输入**：已注册的 SIP URI
- **处理**：发送 Expires=0 的 REGISTER 并等待 200 OK
- **输出**：注销确认
- **验收标准**：MSS 移除该用户的注册记录

#### FR-UM-03：注册状态维护
- **描述**：客户端在 Expires 到期前自动刷新注册
- **处理**：在过期前 30 秒重新发送 REGISTER
- **验收标准**：长时间运行不会因注册过期而离线

### 3.2 即时消息模块（FR-MSG）

#### FR-MSG-01：发送文本消息
- **描述**：用户向目标 SIP URI 发送文本消息
- **输入**：目标 URI、消息内容（UTF-8）
- **处理**：
  1. 构建 SIP MESSAGE 请求
  2. 设置 Content-Type 为 text/plain
  3. 通过 SipProvider 发送
- **输出**：200 OK 或错误响应
- **验收标准**：对方能接收并显示消息

#### FR-MSG-02：接收文本消息
- **描述**：客户端接收并处理入站 MESSAGE 请求
- **输入**：SIP MESSAGE 请求
- **处理**：
  1. 解析 From 和消息体
  2. 回复 200 OK
  3. 触发 MessageHandler 回调
- **输出**：在 UI 显示发送者与消息内容
- **验收标准**：能实时接收并展示消息

#### FR-MSG-03：消息持久化（可选）
- **描述**：将消息保存到本地或服务器数据库
- **优先级**：低（初期可省略）

### 3.3 语音/视频通话模块（FR-CALL）

#### FR-CALL-01：发起呼叫
- **描述**：用户 A 向用户 B 发起语音通话
- **输入**：目标 SIP URI
- **处理**：
  1. 创建 CallSession（状态为 IDLE）
  2. 构建 INVITE 请求（包含 SDP offer）
  3. 发送 INVITE 并等待响应
  4. 收到 180 Ringing → 状态变为 RINGING
  5. 收到 200 OK → 发送 ACK，状态变为 ACTIVE
- **输出**：建立的 Dialog 与媒体会话
- **验收标准**：能成功发起并建立呼叫

#### FR-CALL-02：接听呼叫
- **描述**：用户 B 接收并接听来自用户 A 的呼叫
- **输入**：入站 INVITE 请求
- **处理**：
  1. CallManager 创建 CallSession（incoming=true）
  2. 发送 180 Ringing 临时响应
  3. 用户确认接听后发送 200 OK（包含 SDP answer）
  4. 等待 ACK，状态变为 ACTIVE
- **输出**：建立的 Dialog 与媒体会话
- **验收标准**：能自动或手动接听来电

#### FR-CALL-03：挂断呼叫
- **描述**：任一方主动发送 BYE 结束通话
- **输入**：目标 SIP URI 或 Dialog
- **处理**：
  1. 发送 BYE 请求
  2. 等待 200 OK
  3. 调用 CallSession.terminate() 并释放媒体资源
- **输出**：Dialog 销毁
- **验收标准**：通话正常结束，资源释放

#### FR-CALL-04：媒体处理（占位实现）
- **描述**：初期实现占位 SDP 与媒体接口，后续集成 RTP/音频编解码
- **处理**：
  1. MediaSession 接口定义 start() / stop()
  2. AudioSession 占位实现（打印日志或播放提示音）
- **验收标准**：信令流程正常，媒体为占位

### 3.4 会话管理模块（FR-SESSION）

#### FR-SESSION-01：CallSession 状态管理
- **状态**：IDLE → RINGING → ACTIVE → TERMINATED
- **操作**：markRinging() / markActive() / terminate()
- **验收标准**：状态迁移符合 SIP 状态机

#### FR-SESSION-02：CallManager 会话注册与查询
- **功能**：
  - startOutgoing(uri) / acceptIncoming(uri)
  - markActive(uri) / terminateLocal(uri) / terminateByRemote(uri)
  - findByRemote(uri) / listSessions()
- **并发**：线程安全的 ConcurrentHashMap
- **验收标准**：多并发呼叫不会产生竞态

#### FR-SESSION-03：Dialog 与 CallSession 绑定
- **描述**：SIP Dialog 与业务层 CallSession 一对一关联
- **处理**：在 INVITE 200 OK / ACK 后将 Dialog 存储到 CallSession
- **验收标准**：能通过 Dialog 查找到对应会话

### 3.5 管理后台模块（FR-ADMIN）

#### FR-ADMIN-01：用户管理 API
- **端点**：GET /api/users、POST /api/users/{id}/disable
- **功能**：查询用户列表、禁用/启用用户
- **验收标准**：返回 JSON 格式用户数据

#### FR-ADMIN-02：在线状态监控
- **端点**：GET /api/dashboard
- **功能**：返回当前在线用户数、活跃会话数
- **验收标准**：实时或近实时数据

#### FR-ADMIN-03：通话记录查询
- **端点**：GET /api/calls?startTime=&endTime=
- **功能**：按时间范围查询通话记录（呼叫方、被叫方、时长）
- **验收标准**：支持分页与筛选

#### FR-ADMIN-04：SSE 实时推送（可选）
- **端点**：GET /api/stream
- **功能**：通过 Server-Sent Events 推送实时事件（用户上线/下线、新通话）
- **验收标准**：前端能接收并展示事件流

---

## 4. 非功能需求

### 4.1 性能需求（NFR-PERF）
- **NFR-PERF-01**：单客户端支持至少 10 个并发呼叫会话
- **NFR-PERF-02**：REGISTER 请求响应时间 < 500ms（本地网络）
- **NFR-PERF-03**：MESSAGE 传递延迟 < 200ms（本地网络）

### 4.2 可靠性需求（NFR-REL）
- **NFR-REL-01**：客户端异常退出后能自动重连并重新注册
- **NFR-REL-02**：SIP 认证失败时提供明确错误提示
- **NFR-REL-03**：媒体传输中断时能检测并提示用户

### 4.3 可用性需求（NFR-USE）
- **NFR-USE-01**：命令行界面提供 help 命令与清晰的交互提示
- **NFR-USE-02**：管理后台提供简洁直观的 Web 界面

### 4.4 可维护性需求（NFR-MAIN）
- **NFR-MAIN-01**：核心模块（SipUserAgent、CallManager）有单元测试覆盖
- **NFR-MAIN-02**：代码遵循 Java 命名规范与 Maven 多模块结构
- **NFR-MAIN-03**：日志使用 SLF4J，支持级别配置与文件输出

### 4.5 安全性需求（NFR-SEC）
- **NFR-SEC-01**：SIP 认证使用 Digest Authentication（RFC 2617）
- **NFR-SEC-02**：密码不以明文存储或传输
- **NFR-SEC-03**：管理后台需登录认证（可简化为 HTTP Basic Auth）

### 4.6 兼容性需求（NFR-COMPAT）
- **NFR-COMPAT-01**：支持 Windows / Linux / macOS（JDK 17+）
- **NFR-COMPAT-02**：符合 RFC 3261（SIP）与 RFC 3264（SDP）标准

---

## 5. 系统架构设计

### 5.1 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户（终端设备）                        │
└────────────────────┬────────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
   ┌────▼─────┐            ┌─────▼──────┐
   │ SIP 客户端│            │  Web 浏览器 │
   │(sip-client)│           │ (管理后台) │
   └────┬─────┘            └─────┬──────┘
        │                         │
        │ SIP (UDP/5060)          │ HTTP (8081)
        │                         │
   ┌────▼─────────────────────────▼──────┐
   │     Mobicents SIP Server (MSS)      │
   │  (信令路由 + 用户注册 + 认证)       │
   └────────────┬────────────────────────┘
                │
                │ SIP + RTP
                │
   ┌────────────▼────────────┐
   │   媒体网关 / RTP 中继   │
   │   (初期可省略)          │
   └─────────────────────────┘
```

### 5.2 技术栈

| 层次 | 技术 |
|------|------|
| SIP 协议栈 | JAIN SIP (javax.sip:jain-sip-ri) |
| 业务逻辑 | Java 17、并发工具（CountDownLatch、ConcurrentHashMap） |
| 管理后台 | Spring Boot 3.x、Spring MVC、SSE |
| 日志 | SLF4J + Logback |
| 构建 | Maven 3.9+ |
| 测试 | JUnit 5 |
| 媒体（后期）| JMF / libjitsi / WebRTC |

### 5.3 模块划分

```
project-parent (pom)
├── sip-client (jar)
│   ├── sip/           # SIP 协议封装 (SipUserAgent)
│   ├── call/          # 呼叫管理 (CallManager, CallSession)
│   ├── chat/          # 消息处理 (MessageHandler)
│   ├── media/         # 媒体抽象 (MediaSession, AudioSession)
│   └── ui/            # 用户界面 (ConsoleMain, 未来 JavaFX)
└── admin-server (jar)
    ├── controller/    # REST 控制器
    ├── service/       # 业务逻辑
    ├── repository/    # 数据访问（可选）
    └── entity/        # 数据模型
```

---

## 6. 开发计划与里程碑

### 6.1 阶段划分

| 阶段 | 目标 | 交付物 | 工期（估算） |
|------|------|--------|-------------|
| **阶段 1** | 基础环境与最小 SIP 客户端 | SipUserAgent、ConsoleMain、REGISTER/UNREGISTER | 1 周 |
| **阶段 2** | 即时消息 | MESSAGE 发送/接收、MessageHandler | 3 天 |
| **阶段 3** | 通话信令 | CallManager、CallSession、INVITE/BYE 流程 | 1 周 |
| **阶段 4** | 媒体占位 | MediaSession 接口、占位实现 | 2 天 |
| **阶段 5** | 管理后台 | Spring Boot 应用、REST API、SSE | 1 周 |
| **阶段 6** | 测试与优化 | 单元测试、集成测试、性能调优 | 3 天 |
| **阶段 7**（可选） | 真实媒体 | 集成 RTP/音频编解码、NAT 穿透 | 2 周 |

### 6.2 当前进度（2025-11-13）

**已完成**：
- ✅ Maven 多模块项目结构（父 pom、sip-client、admin-server）
- ✅ SipUserAgent 核心实现（REGISTER、认证、MESSAGE、INVITE 信令骨架）
- ✅ ConsoleMain 交互式控制台
- ✅ CallManager / CallSession 骨架（部分方法待完善）
- ✅ 基础文档（guidance.md）

**进行中**：
- 🔄 CallManager 并发安全与完整实现
- 🔄 CallSession 状态管理与媒体绑定

**待开始**：
- ❌ 单元测试覆盖
- ❌ admin-server Spring Boot 主类与 API
- ❌ 日志配置优化（logback.xml）
- ❌ 真实媒体实现（RTP）

---

## 7. 测试策略

### 7.1 单元测试（UT）
- **目标**：覆盖核心业务逻辑，代码覆盖率 > 70%
- **工具**：JUnit 5、Mockito
- **测试用例示例**：
  - CallManager：startOutgoing → markActive → terminateLocal
  - SipUserAgent：认证处理、REGISTER 重试逻辑
  - CallSession：状态迁移（IDLE → RINGING → ACTIVE → TERMINATED）

### 7.2 集成测试（IT）
- **目标**：验证客户端与 MSS 的端到端交互
- **环境**：本地搭建 MSS 或使用测试服务器
- **测试场景**：
  1. 用户 A 注册成功
  2. 用户 A 向用户 B 发送 MESSAGE 并收到 200 OK
  3. 用户 A 呼叫用户 B，双方建立 Dialog

### 7.3 系统测试（ST）
- **目标**：验证非功能需求（性能、可靠性）
- **测试项**：
  - 并发 10 个呼叫会话的稳定性
  - REGISTER 刷新机制的长期运行测试
  - NAT 环境下的连通性测试

### 7.4 验收测试（AT）
- **目标**：按功能需求逐项验收
- **执行人**：项目负责人或客户代表
- **通过标准**：所有 FR 需求的验收标准满足

---

## 8. 部署与运行说明

### 8.1 环境准备

#### 前置条件
- JDK 17（推荐 Eclipse Temurin 或 Amazon Corretto）
- Maven 3.9+
- Mobicents SIP Server（可用 Docker 部署）

#### 验证命令
```powershell
java -version
mvn -v
```

### 8.2 构建项目

#### 编译所有模块
```powershell
mvn clean package
```

#### 仅编译 sip-client
```powershell
mvn -pl sip-client -am package
```

### 8.3 运行 SIP 客户端

#### 方式 1：Maven Exec 插件（开发调试）
```powershell
mvn -pl sip-client -am org.codehaus.mojo:exec-maven-plugin:3.1.0:java `
  -Dexec.mainClass=com.example.sipclient.ui.ConsoleMain `
  -Dexec.cleanupDaemonThreads=false
```

#### 方式 2：IDE 运行
- 导入 Maven 项目到 IntelliJ IDEA / Eclipse
- 设置 Project SDK 为 JDK 17
- 运行 `com.example.sipclient.ui.ConsoleMain`

#### 方式 3：打包为可执行 JAR（待实现）
```powershell
# 需在 sip-client pom.xml 添加 Maven Shade 插件
java -jar sip-client/target/sip-client-1.0.0-SNAPSHOT-shaded.jar
```

### 8.4 运行管理后台（待实现主类）

```powershell
mvn -pl admin-server spring-boot:run
```

访问：http://localhost:8081

### 8.5 配置说明

#### SIP 配置
- **MSS 地址**：在 ConsoleMain 运行时手动输入 SIP URI（如 sip:alice@192.168.1.100）
- **OUTBOUND_PROXY**：SipUserAgent 自动从 SIP URI 提取并配置
- **本地端口**：默认绑定用户输入的端口（建议 5070-5090）

#### 日志配置
- 日志框架：SLF4J + slf4j-simple（当前）或 logback-classic（推荐）
- 日志级别：通过 `gov.nist.javax.sip.TRACE_LEVEL` 控制 JAIN SIP 详细日志（0=关闭，32=详细）

---

## 9. 风险与约束

### 9.1 技术风险
| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| NAT 穿透失败 | 无法建立媒体连接 | 使用 STUN/TURN 服务器或部署在同一局域网 |
| JAIN SIP 版本兼容性 | 依赖冲突 | 统一使用 1.3.0-91 版本并测试 |
| 媒体实现复杂度高 | 开发周期延长 | 阶段性交付，先实现信令再集成媒体库 |

### 9.2 业务约束
- **时间约束**：项目需在 4 周内完成基础功能（不含真实媒体）
- **资源约束**：单人或小团队开发，优先保证核心功能
- **测试环境**：依赖本地 MSS 部署，需提前准备

### 9.3 设计约束
- **必须符合 SIP RFC 标准**（RFC 3261）
- **必须使用 JAIN SIP 作为协议栈**（课程要求）
- **必须支持 Digest Authentication**

---

## 10. 附录

### 10.1 SIP 消息流程示例

#### REGISTER 流程
```
Client                    MSS
  |                        |
  |------ REGISTER ------->|
  |                        |
  |<--- 401 Unauthorized --|
  |                        |
  |--- REGISTER (Auth) --->|
  |                        |
  |<------ 200 OK ---------|
```

#### INVITE 流程
```
Caller (A)                Callee (B)
  |                          |
  |------- INVITE ---------->|
  |                          |
  |<--- 180 Ringing ---------|
  |                          |
  |<------ 200 OK -----------|
  |                          |
  |-------- ACK ------------>|
  |                          |
  |<====== RTP Media =======>|
  |                          |
  |-------- BYE ------------>|
  |                          |
  |<------ 200 OK -----------|
```

### 10.2 关键类与接口清单

| 类/接口 | 模块 | 职责 |
|---------|------|------|
| SipUserAgent | sip-client/sip | SIP 协议栈封装、REGISTER/MESSAGE/INVITE |
| CallManager | sip-client/call | 会话管理、并发安全 |
| CallSession | sip-client/call | 单个呼叫会话状态 |
| MessageHandler | sip-client/chat | 消息回调接口 |
| MediaSession | sip-client/media | 媒体抽象接口 |
| ConsoleMain | sip-client/ui | 命令行交互入口 |
| DashboardController | admin-server | 管理后台 REST API |

### 10.3 故障排查指南

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| REGISTER 超时 | 本地 IP 不可达 MSS | 检查防火墙、NAT、OUTBOUND_PROXY 配置 |
| 401/407 认证失败循环 | 密码错误或 realm 不匹配 | 确认密码正确，查看 MSS 日志 |
| INVITE 无响应 | 对方不在线或端口被占用 | 确认对方已注册，检查端口冲突 |
| 无日志输出 | 缺少 SLF4J 绑定 | 添加 logback-classic 依赖 |
| 编译错误 | JDK 版本不匹配 | 确保使用 JDK 17 |

---

## 11. 变更历史

| 版本 | 日期 | 变更内容 | 编制人 |
|------|------|---------|--------|
| 1.0 | 2025-11-13 | 初始版本，完整需求规格说明书 | AI 编程助手 |

---

**文档结束**
