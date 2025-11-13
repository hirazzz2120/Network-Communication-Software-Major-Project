# SIP 通信项目需求说明（第 2 周版）

> 适用场景：面向 4 人大学生团队，在当前（第 2 周）开发进度基础上继续规划 6 周内完成的“即时消息 + 呼叫 + 后台管理”系统。

## 1. 产品愿景与目标
- 提供一个桌面端 SIP 客户端，支持注册、即时消息、群聊、语音/视频通话，并能通过后台监控用户与通话状态。
- 服务器采用 MSS（Mobicents SIP Server）处理 SIP 信令，客户端使用 JAIN SIP 协议栈；后台以 Spring Boot 构建 REST API 和简单监控界面。
- 目标交付包括：
  1. 可运行的 SIP 客户端（CLI 起步，可演进到 JavaFX）。
  2. 呼叫/群聊/媒体占位能力。
  3. 管理端 REST 服务与报表。
  4. 基础文档、测试与部署脚本。

## 2. 当前进度回顾（第 2 周）
- Maven 多模块骨架完成，包含 `sip-client` 与 `admin-server`。【F:README.md†L5-L35】
- 客户端具备 REGISTER/UNREGISTER、SIP MESSAGE、控制台 UI、群聊广播、呼叫信令以及占位音频会话。后台可返回示例用户、通话与统计数据。 【F:README.md†L5-L52】
- 指导文档 `docs/guidance.md` 给出阶段性目标和技术栈，后续迭代需与其一致。 【F:docs/guidance.md†L1-L118】
- 本周目标：在既有能力上完善真实场景所需的可靠性、媒体和管理能力，并规划剩余 4 周交付。

## 3. 详细需求
### 3.1 功能性需求
1. **账号与注册**
   - 在 CLI/未来 GUI 中录入 SIP URI、密码、本地地址，完成 REGISTER/UNREGISTER。
   - 支持注册状态提示、失败重试及超时反馈。
2. **即时消息**
   - 点对点文本消息：消息列表、历史查看、状态提示（已发送/失败）。
   - 群聊：创建/解散群组、维护成员列表、群发文本；未来可扩展文件/图片占位能力。
3. **呼叫管理**
   - 发起、接听、挂断语音通话，完整 INVITE/ACK/BYE 流程。
   - 呼叫状态机（IDLE/RINGING/ACTIVE/ENDED），控制媒体会话的启动/停止。
   - 视频通话先实现信令流程，媒体可用占位或日志。
4. **媒体层**
   - 现阶段：AudioSession 占位实现，打印或播放提示音。
   - 第 4 周起：集成 Java Sound 或第三方库完成真实音频流，支持输入设备切换。
5. **后台管理**
   - REST API：用户、通话记录、统计数据，支持分页和简单过滤。
   - 后台页面：展示在线用户、通话列表、统计图（可用简单 HTML/JS）。
   - 长期：把内存数据源替换为数据库（MySQL/PostgreSQL）。
6. **文档与测试**
   - README 保持最新，补充部署步骤。
   - 为核心模块（SipUserAgent、CallManager、StatsService 等）编写单元测试。
   - 提供端到端演示脚本或操作指南。

### 3.2 非功能性需求
- **性能**：并发 20+ 注册/呼叫保持稳定；消息处理 < 1s 延迟。
- **可靠性**：失败重试、异常日志、最少 80% 的核心逻辑覆盖率。
- **可维护性**：模块边界清晰（UI、SIP、业务、后台）；遵循 Maven 多模块规范。
- **可用性**：CLI 菜单清楚，未来 GUI 需支持联系人和呼叫窗口。

## 4. 技术方案
- **客户端**：Java 17、JAIN SIP、Maven。控制台 UI 过渡到 JavaFX。模块结构遵循 `sip/`, `chat/`, `call/`, `media/`, `ui/` 包。 【F:docs/guidance.md†L48-L90】
- **后台**：Spring Boot、内存存储 -> 数据库（MySQL/Redis）。REST API + 轻量前端（可用 Thymeleaf 或 React/Vite）。
- **服务器**：MSS 作为 SIP Registrar/Proxy。未来可接入 RTP/媒体服务器。
- **工具**：JUnit、Mock frameworks、GitHub Actions（CI）、Docker（可选）。

## 5. 里程碑与交付
| 周次 | 目标 | 验收内容 |
| --- | --- | --- |
| 第 1 周（完成） | 项目初始化、最小客户端注册/消息 | Maven 多模块、REGISTER/MESSAGE 流程 Demo |
| 第 2 周（当前） | 呼叫信令、群聊骨架、后台 REST 样例 | ConsoleUI 支持群聊+呼叫，admin-server 返回示例数据 |
| 第 3 周 | 增强消息可靠性、完善群聊 UI、后台 CRUD | 消息重试/状态、群聊管理命令、REST 可增删改查 |
| 第 4 周 | 实现真实音频流、完善呼叫异常处理 | Java Sound 媒体链路、呼叫超时/重连策略 |
| 第 5 周 | 引入数据库与后台报表、初步 GUI | MySQL/ORM 读写、统计图、JavaFX 联系人/聊天窗 |
| 第 6 周 | 测试 + 文档 + 发布 | 完整 README、安装脚本、单元+集成测试通过 |

## 6. 四人六周分工（当前为第 2 周）
> 建议角色：客户端-SIP、客户端-UI&体验、媒体/通话、后台&运维。每周列出主要任务，默认所有人保持 code review 与周会同步。

| 周次 | Alice（SIP/协议） | Bob（UI/聊天体验） | Carol（媒体/通话） | Dave（后台/运维） |
| --- | --- | --- | --- | --- |
| 第 1 周 | 初始化 `SipUserAgent`、REGISTER/MESSAGE | 搭建 `ConsoleUI` 菜单、消息历史 | 协助 CallManager 设计 | 建立 `admin-server`、示例数据 |
| **第 2 周** | 完成 INVITE/ACK/BYE 封装、错误码处理 | 集成群聊命令、消息格式化 | 设计 `CallSession` 状态机、AudioSession 占位 | 暴露 `/api/users` `/api/calls` `/api/stats`、Mock 数据 |
| 第 3 周 | 实现消息可靠性（ACK/重发）、SIP 日志 | 完善群聊管理（增删成员）、命令行验证 | 呼叫异常处理（超时、失败提示） | 后台 CRUD、分页过滤、为后续 DB 做接口抽象 |
| 第 4 周 | 对接真实 MSS 环境、调试 REGISTER | 设计 JavaFX 原型（联系人/聊天窗） | 集成 Java Sound 真实音频、设备管理 | 设计数据库模型，准备迁移脚本 |
| 第 5 周 | 支持多设备登录策略、SIP Keepalive | JavaFX MVP（聊天、群聊、呼叫弹窗） | 视频信令（预留/模拟） | 接入 MySQL/ORM，完成统计报表与简单前端页面 |
| 第 6 周 | 整体稳定性测试、编写 SIP 故障指南 | 完善 UI 细节、用户文档/截图 | 媒体回归测试、音频性能调优 | 搭建 CI/CD（GitHub Actions）、部署脚本、运维手册 |

## 7. 风险与对策
| 风险 | 影响 | 应对 |
| --- | --- | --- |
| MSS 环境/网络不稳定 | 注册、呼叫测试受阻 | 本地搭建可模拟的 SIP 服务器，提前预约实验室资源 |
| 媒体链路调试难度高 | 第 4~5 周进度延迟 | 先实现最小可用音频（Java Sound），必要时降级为录音回放 |
| JavaFX 学习曲线 | GUI 进展缓慢 | 第 2 周开始安排 UI 负责人预研 Demo，必要时保留 CLI 兜底 |
| 数据库迁移时间不足 | 后台统计功能不完整 | 第 3 周先抽象 Repository 接口，第 4 周完成表设计，第 5 周集中实现 |

## 8. 后续学习建议
- 深读 `docs/guidance.md` 与 README，以确保阶段目标一致。 【F:docs/guidance.md†L1-L118】【F:README.md†L1-L73】
- 熟悉 MSS 配置、JAIN SIP 示例，准备 Wireshark/Tcpdump 辅助调试。
- 学习 Java Sound/JavaFX 官方教程，为第 4~5 周做技术储备。
- 关注测试与文档积累：每周至少补充一次 README 或操作指南，保证新人能快速上手。
