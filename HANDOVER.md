# 🎉 前后端项目交接文档

> 更新时间：2025-11-15  
> 适用对象：前端开发者、后端开发者

---

## 📦 项目概览

本项目已完成 **前后端分离架构** 的基础搭建：

- ✅ **完整的 API 接口规范**（`API-SPECIFICATION.md`）
- ✅ **Web 前端项目骨架**（`web-frontend/`）
- ✅ **前端核心功能**（登录、聊天、联系人、通话）
- ✅ **状态管理与 WebSocket 集成**
- ✅ **TypeScript 类型定义完整**

---

## 🚀 快速开始（3分钟上手）

### 给前端开发者

1. **安装 Node.js 18+**
   - 下载：https://nodejs.org/

2. **启动 Web 前端**
   ```powershell
   .\start-web.ps1
   ```
   或手动：
   ```powershell
   cd web-frontend
   npm install
   npm run dev
   ```

3. **访问应用**
   - 打开浏览器：http://localhost:3000

4. **阅读文档**
   - 前端详细文档：`web-frontend/README.md`
   - API 接口规范：`API-SPECIFICATION.md`
   - 快速启动指南：`WEB-QUICKSTART.md`

### 给后端开发者

1. **启动后端服务**
   ```powershell
   cd admin-server
   mvn spring-boot:run
   ```

2. **实现 API 接口**
   - 参考 `API-SPECIFICATION.md` 中的接口定义
   - 主要接口：
     - `POST /api/auth/login` - 用户登录
     - `GET /api/users` - 用户列表
     - `POST /api/messages` - 发送消息
     - `POST /api/calls` - 发起呼叫
     - WebSocket `/ws/events` - 实时推送

3. **测试接口**
   - 使用 Postman 或浏览器测试
   - 检查返回格式是否符合规范

---

## 📄 核心文档索引

| 文档 | 路径 | 说明 | 优先级 |
|------|------|------|--------|
| **API 接口规范** | `API-SPECIFICATION.md` | 前后端 API 完整定义 | ⭐⭐⭐⭐⭐ |
| **Web 前端文档** | `web-frontend/README.md` | 前端项目结构与开发指南 | ⭐⭐⭐⭐⭐ |
| **Web 快速启动** | `WEB-QUICKSTART.md` | Web 前端启动教程 | ⭐⭐⭐⭐ |
| **项目 README** | `README.md` | 项目总体介绍 | ⭐⭐⭐⭐ |
| **CLI 快速启动** | `QUICKSTART.md` | 命令行客户端测试指南 | ⭐⭐⭐ |
| **需求文档** | `docs/guidance.md` | 软件需求规格说明 | ⭐⭐⭐ |

---

## 🎯 前端开发者任务清单

### 已完成 ✅
- [x] React + TypeScript 项目初始化
- [x] Ant Design UI 组件库集成
- [x] Zustand 状态管理
- [x] Axios API 请求封装
- [x] WebSocket 客户端封装
- [x] 登录页面（`LoginPage.tsx`）
- [x] 主页面布局（`MainPage.tsx`）
- [x] 侧边栏导航（`Sidebar.tsx`）
- [x] 聊天页面（`ChatPage.tsx`）
- [x] 聊天窗口组件（`ChatWindow.tsx`）
- [x] 联系人页面（`ContactsPage.tsx`）
- [x] TypeScript 类型定义（`types/api.ts`）

### 待开发 🔄
- [ ] **优化 UI 样式**（当前为基础样式）
- [ ] **WebRTC 音频通话功能**（通话页面占位中）
- [ ] **消息状态更新**（已读/未读状态同步）
- [ ] **文件上传**（图片、文件消息）
- [ ] **表情包支持**
- [ ] **消息撤回**
- [ ] **群聊功能**
- [ ] **通知提示音**
- [ ] **桌面通知**
- [ ] **用户设置页面**

### 开发建议
1. 先确保后端 API 可用
2. 使用浏览器开发者工具调试
3. 查看 `API-SPECIFICATION.md` 确认接口格式
4. 遇到问题查看控制台错误信息

---

## 🔧 后端开发者任务清单

### 已完成 ✅
- [x] Spring Boot 项目结构
- [x] Controller 骨架（`UserController`, `StatsController` 等）
- [x] Service 层示例
- [x] 内存数据存储（`InMemoryStore`）
- [x] SIP 协议实现（`SipUserAgent`）
- [x] 呼叫管理（`CallManager`）
- [x] 消息处理（`MessageHandler`）

### 待实现 🔄
- [ ] **实现认证 API**
  - `POST /api/auth/login` - SIP 登录 + JWT 生成
  - `POST /api/auth/logout` - 注销
  - `GET /api/auth/profile` - 获取用户信息
  - `PUT /api/auth/status` - 更新状态

- [ ] **实现消息 API**
  - `POST /api/messages` - 发送消息
  - `GET /api/messages/sessions` - 会话列表
  - `GET /api/messages/sessions/{id}` - 会话历史

- [ ] **实现呼叫 API**
  - `POST /api/calls` - 发起呼叫
  - `PUT /api/calls/{id}/answer` - 接听
  - `DELETE /api/calls/{id}` - 挂断

- [ ] **实现用户 API**
  - `GET /api/users` - 用户列表（支持分页、搜索）
  - `GET /api/users/{id}` - 用户详情

- [ ] **WebSocket 实时推送**
  - 连接管理
  - 消息推送（MESSAGE_RECEIVED）
  - 来电推送（INCOMING_CALL）
  - 状态变化推送（CALL_STATE_CHANGED, USER_STATUS_CHANGED）

- [ ] **数据持久化**
  - 集成 Spring Data JPA
  - 配置 MySQL/PostgreSQL
  - 实现实体映射

- [ ] **CORS 配置**（如果不使用代理）

### 开发建议
1. 优先实现登录接口，让前端能连上
2. 实现 WebSocket 基础连接
3. 按功能模块逐步实现（消息 → 呼叫 → 用户）
4. 使用 Postman 测试每个接口
5. 确保返回格式符合 `API-SPECIFICATION.md`

---

## 🔗 API 接口快速参考

### 基础信息
- **Base URL**: `http://localhost:8081/api`
- **WebSocket**: `ws://localhost:8081/ws/events?token={jwt}`
- **认证方式**: JWT Bearer Token
- **数据格式**: JSON

### 核心接口

#### 1. 用户登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "sipUri": "sip:alice@192.168.1.100:5060",
  "password": "secret123",
  "localIp": "192.168.1.50",
  "localPort": 5070
}
```

**响应：**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGc...",
    "userId": "alice@192.168.1.100",
    "displayName": "Alice",
    "expiresIn": 3600
  }
}
```

#### 2. 发送消息
```http
POST /api/messages
Authorization: Bearer {token}
Content-Type: application/json

{
  "to": "sip:bob@192.168.1.100:5060",
  "type": "TEXT",
  "content": "你好！"
}
```

#### 3. WebSocket 事件
```javascript
// 连接
const ws = new WebSocket('ws://localhost:8081/ws/events?token=' + token);

// 接收消息
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  if (data.type === 'MESSAGE_RECEIVED') {
    console.log('新消息:', data.data);
  }
};
```

**完整接口列表请查看 `API-SPECIFICATION.md`**

---

## 🛠️ 技术栈对照

| 层级 | 前端 | 后端 |
|------|------|------|
| 框架 | React 18 | Spring Boot 3.2.5 |
| 语言 | TypeScript 5.2 | Java 17 |
| 构建 | Vite 5 | Maven 3.x |
| 状态管理 | Zustand 4.4 | - |
| UI 组件 | Ant Design 5.12 | - |
| HTTP 客户端 | Axios 1.6 | - |
| WebSocket | Socket.IO Client 4.6 | - |
| 数据请求 | React Query 5.12 | - |
| 协议栈 | - | JAIN SIP 1.3.0-91 |
| 日志 | - | SLF4J + Logback |
| 测试 | - | JUnit 5 + Mockito |

---

## 📞 协作流程

### 接口联调步骤

1. **后端开发者**
   - 实现接口（参考 API 规范）
   - 本地启动服务器（端口 8081）
   - 使用 Postman 自测

2. **前端开发者**
   - 启动前端开发服务器（端口 3000）
   - 在浏览器中测试功能
   - 查看 Network 面板确认请求

3. **联调**
   - 前后端同时运行
   - 前端发起请求
   - 后端检查日志
   - 调整接口格式（如有问题）

### 遇到问题怎么办？

| 问题 | 前端检查 | 后端检查 |
|------|----------|----------|
| 登录失败 | 请求参数是否正确 | SIP 注册是否成功 |
| 接口 404 | URL 路径是否正确 | Controller 路由是否配置 |
| CORS 错误 | 是否启用代理 | CORS 配置是否正确 |
| WebSocket 连接失败 | Token 是否携带 | WebSocket 端点是否启用 |
| 数据格式错误 | 类型定义是否匹配 | 返回格式是否符合规范 |

---

## 🎓 学习资源

### 前端
- React 官方文档：https://react.dev/
- Ant Design：https://ant.design/
- Zustand：https://github.com/pmndrs/zustand
- WebRTC：https://webrtc.org/

### 后端
- Spring Boot：https://spring.io/projects/spring-boot
- JAIN SIP：https://github.com/RestComm/jain-sip
- WebSocket：https://docs.spring.io/spring-framework/reference/web/websocket.html

---

## 📌 重要提示

1. **API 规范是唯一真理**：所有接口以 `API-SPECIFICATION.md` 为准
2. **先跑通登录**：这是最基础的功能，优先完成
3. **使用 Git 分支**：前后端各自开发分支，定期合并
4. **接口变更要通知**：修改 API 定义要同步更新文档并通知对方
5. **保持沟通**：遇到问题及时沟通，避免重复工作

---

## ✅ 验收标准

### 前端完成标准
- [ ] 登录页面可用
- [ ] 能显示联系人列表
- [ ] 能发送和接收消息
- [ ] WebSocket 实时更新
- [ ] UI 美观，响应式布局

### 后端完成标准
- [ ] 所有 API 按规范实现
- [ ] WebSocket 实时推送工作正常
- [ ] 与 SIP 服务器集成成功
- [ ] 数据持久化完成
- [ ] 单元测试通过

---

## 🎁 项目亮点

- ✨ 完整的前后端分离架构
- ✨ TypeScript 类型安全
- ✨ WebSocket 实时通信
- ✨ 模块化的代码组织
- ✨ 详细的 API 文档
- ✨ 一键启动脚本

---

## 📧 联系方式

- 项目仓库：https://github.com/huanxu123/sip
- 前端负责人：[你的名字]
- 后端负责人：[组员名字]

---

**祝开发顺利！💪**
