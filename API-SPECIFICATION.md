# SIP 通信项目 - 前后端 API 接口规范

> 版本：v1.0  
> 更新日期：2025-11-15

## 基础信息

- **Base URL**: `http://localhost:8081/api`
- **WebSocket URL**: `ws://localhost:8081/ws`
- **认证方式**: JWT Token (Bearer Token)
- **数据格式**: JSON
- **字符编码**: UTF-8

---

## 1. 认证与用户管理

### 1.1 用户登录（SIP 注册）

**POST** `/auth/login`

注册到 SIP 服务器并获取访问令牌。

**请求体：**
```json
{
  "sipUri": "sip:alice@192.168.1.100:5060",
  "password": "secret123",
  "localIp": "192.168.1.50",
  "localPort": 5070
}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "alice@192.168.1.100",
    "displayName": "Alice",
    "expiresIn": 3600,
    "sipConfig": {
      "registered": true,
      "expires": 3600,
      "localAddress": "192.168.1.50:5070"
    }
  },
  "message": "登录成功"
}
```

**错误响应（401 Unauthorized）：**
```json
{
  "success": false,
  "error": {
    "code": "AUTH_FAILED",
    "message": "SIP 注册失败：认证错误"
  }
}
```

### 1.2 用户注销

**POST** `/auth/logout`

注销 SIP 注册并清除会话。

**请求头：**
```
Authorization: Bearer {token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "message": "注销成功"
}
```

### 1.3 获取当前用户信息

**GET** `/auth/profile`

**请求头：**
```
Authorization: Bearer {token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "userId": "alice@192.168.1.100",
    "sipUri": "sip:alice@192.168.1.100:5060",
    "displayName": "Alice",
    "status": "ONLINE",
    "registered": true,
    "lastActivity": "2025-11-15T10:30:00Z"
  }
}
```

### 1.4 更新用户状态

**PUT** `/auth/status`

**请求头：**
```
Authorization: Bearer {token}
```

**请求体：**
```json
{
  "status": "ONLINE|AWAY|BUSY|OFFLINE",
  "statusMessage": "忙碌中，稍后回复"
}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "message": "状态已更新"
}
```

---

## 2. 联系人与用户列表

### 2.1 获取在线用户列表

**GET** `/users?status=ONLINE&page=0&size=20`

**请求头：**
```
Authorization: Bearer {token}
```

**查询参数：**
- `status` (可选): 过滤状态 (ONLINE, AWAY, BUSY, ALL)
- `search` (可选): 搜索关键词（用户名/SIP URI）
- `page` (可选): 页码，默认 0
- `size` (可选): 每页数量，默认 20

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "userId": "bob@192.168.1.100",
        "sipUri": "sip:bob@192.168.1.100:5060",
        "displayName": "Bob",
        "status": "ONLINE",
        "statusMessage": "在线",
        "avatar": null,
        "lastSeen": "2025-11-15T10:28:00Z"
      },
      {
        "userId": "carol@192.168.1.100",
        "sipUri": "sip:carol@192.168.1.100:5060",
        "displayName": "Carol",
        "status": "AWAY",
        "statusMessage": "离开",
        "avatar": null,
        "lastSeen": "2025-11-15T10:15:00Z"
      }
    ],
    "totalElements": 15,
    "totalPages": 1,
    "currentPage": 0,
    "pageSize": 20
  }
}
```

### 2.2 获取联系人详情

**GET** `/users/{userId}`

**请求头：**
```
Authorization: Bearer {token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "userId": "bob@192.168.1.100",
    "sipUri": "sip:bob@192.168.1.100:5060",
    "displayName": "Bob",
    "status": "ONLINE",
    "statusMessage": "在线",
    "avatar": null,
    "email": "bob@example.com",
    "phone": "+86 138 0000 0000",
    "lastSeen": "2025-11-15T10:28:00Z"
  }
}
```

---

## 3. 即时消息

### 3.1 发送消息

**POST** `/messages`

**请求头：**
```
Authorization: Bearer {token}
```

**请求体：**
```json
{
  "to": "sip:bob@192.168.1.100:5060",
  "type": "TEXT",
  "content": "你好，收到了吗？",
  "metadata": {
    "clientMsgId": "msg-uuid-12345"
  }
}
```

**消息类型：**
- `TEXT` - 文本消息
- `FILE` - 文件（未实现）
- `IMAGE` - 图片（未实现）

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "messageId": "msg-server-67890",
    "clientMsgId": "msg-uuid-12345",
    "from": "sip:alice@192.168.1.100:5060",
    "to": "sip:bob@192.168.1.100:5060",
    "type": "TEXT",
    "content": "你好，收到了吗？",
    "timestamp": "2025-11-15T10:30:15Z",
    "status": "SENT"
  },
  "message": "消息已发送"
}
```

### 3.2 获取会话历史

**GET** `/messages/sessions/{sessionId}?page=0&size=50`

**请求头：**
```
Authorization: Bearer {token}
```

**查询参数：**
- `page` (可选): 页码，默认 0
- `size` (可选): 每页数量，默认 50
- `before` (可选): 获取指定时间戳之前的消息

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "sessionId": "alice@192.168.1.100-bob@192.168.1.100",
    "peer": {
      "userId": "bob@192.168.1.100",
      "displayName": "Bob",
      "status": "ONLINE"
    },
    "messages": [
      {
        "messageId": "msg-server-67890",
        "from": "sip:alice@192.168.1.100:5060",
        "to": "sip:bob@192.168.1.100:5060",
        "type": "TEXT",
        "content": "你好，收到了吗？",
        "timestamp": "2025-11-15T10:30:15Z",
        "status": "READ",
        "isOwn": true
      },
      {
        "messageId": "msg-server-67889",
        "from": "sip:bob@192.168.1.100:5060",
        "to": "sip:alice@192.168.1.100:5060",
        "type": "TEXT",
        "content": "收到了，谢谢！",
        "timestamp": "2025-11-15T10:29:45Z",
        "status": "READ",
        "isOwn": false
      }
    ],
    "totalMessages": 42,
    "hasMore": true
  }
}
```

### 3.3 获取会话列表

**GET** `/messages/sessions?unreadOnly=false`

**请求头：**
```
Authorization: Bearer {token}
```

**查询参数：**
- `unreadOnly` (可选): 仅显示有未读消息的会话，默认 false

**响应（200 OK）：**
```json
{
  "success": true,
  "data": [
    {
      "sessionId": "alice@192.168.1.100-bob@192.168.1.100",
      "peer": {
        "userId": "bob@192.168.1.100",
        "displayName": "Bob",
        "status": "ONLINE",
        "avatar": null
      },
      "lastMessage": {
        "content": "收到了，谢谢！",
        "timestamp": "2025-11-15T10:29:45Z",
        "type": "TEXT"
      },
      "unreadCount": 0,
      "updatedAt": "2025-11-15T10:29:45Z"
    },
    {
      "sessionId": "alice@192.168.1.100-carol@192.168.1.100",
      "peer": {
        "userId": "carol@192.168.1.100",
        "displayName": "Carol",
        "status": "AWAY",
        "avatar": null
      },
      "lastMessage": {
        "content": "晚点聊",
        "timestamp": "2025-11-15T09:15:30Z",
        "type": "TEXT"
      },
      "unreadCount": 3,
      "updatedAt": "2025-11-15T09:15:30Z"
    }
  ]
}
```

### 3.4 标记消息为已读

**PUT** `/messages/{messageId}/read`

**请求头：**
```
Authorization: Bearer {token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "message": "已标记为已读"
}
```

---

## 4. 语音/视频呼叫

### 4.1 发起呼叫

**POST** `/calls`

**请求头：**
```
Authorization: Bearer {token}
```

**请求体：**
```json
{
  "to": "sip:bob@192.168.1.100:5060",
  "type": "AUDIO",
  "sdp": "v=0\r\no=- 123456 123456 IN IP4 192.168.1.50\r\n..."
}
```

**呼叫类型：**
- `AUDIO` - 语音通话
- `VIDEO` - 视频通话（未实现）

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "callId": "call-uuid-abc123",
    "from": "sip:alice@192.168.1.100:5060",
    "to": "sip:bob@192.168.1.100:5060",
    "type": "AUDIO",
    "state": "RINGING",
    "createdAt": "2025-11-15T10:31:00Z"
  },
  "message": "呼叫已发起"
}
```

### 4.2 接听呼叫

**PUT** `/calls/{callId}/answer`

**请求头：**
```
Authorization: Bearer {token}
```

**请求体：**
```json
{
  "sdp": "v=0\r\no=- 789012 789012 IN IP4 192.168.1.60\r\n..."
}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "callId": "call-uuid-abc123",
    "state": "ACTIVE",
    "answeredAt": "2025-11-15T10:31:05Z"
  },
  "message": "通话已接通"
}
```

### 4.3 拒绝呼叫

**PUT** `/calls/{callId}/reject`

**请求头：**
```
Authorization: Bearer {token}
```

**请求体（可选）：**
```json
{
  "reason": "忙碌中"
}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "message": "已拒绝呼叫"
}
```

### 4.4 挂断呼叫

**DELETE** `/calls/{callId}`

**请求头：**
```
Authorization: Bearer {token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "callId": "call-uuid-abc123",
    "state": "ENDED",
    "duration": 125,
    "endedAt": "2025-11-15T10:33:10Z"
  },
  "message": "通话已结束"
}
```

### 4.5 获取当前呼叫状态

**GET** `/calls/{callId}`

**请求头：**
```
Authorization: Bearer {token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "callId": "call-uuid-abc123",
    "from": "sip:alice@192.168.1.100:5060",
    "to": "sip:bob@192.168.1.100:5060",
    "type": "AUDIO",
    "state": "ACTIVE",
    "createdAt": "2025-11-15T10:31:00Z",
    "answeredAt": "2025-11-15T10:31:05Z",
    "duration": 65
  }
}
```

### 4.6 获取通话历史

**GET** `/calls/history?page=0&size=20&type=ALL`

**请求头：**
```
Authorization: Bearer {token}
```

**查询参数：**
- `type` (可选): ALL, MISSED, OUTGOING, INCOMING
- `page` (可选): 页码，默认 0
- `size` (可选): 每页数量，默认 20

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "callId": "call-uuid-abc123",
        "peer": {
          "userId": "bob@192.168.1.100",
          "displayName": "Bob"
        },
        "type": "AUDIO",
        "direction": "OUTGOING",
        "state": "ENDED",
        "duration": 125,
        "createdAt": "2025-11-15T10:31:00Z",
        "answeredAt": "2025-11-15T10:31:05Z",
        "endedAt": "2025-11-15T10:33:10Z"
      }
    ],
    "totalElements": 8,
    "totalPages": 1,
    "currentPage": 0
  }
}
```

---

## 5. WebSocket 实时事件

### 5.1 连接 WebSocket

**URL**: `ws://localhost:8081/ws/events?token={jwt_token}`

连接成功后，服务器会推送实时事件。

### 5.2 事件类型

#### 5.2.1 收到新消息

```json
{
  "type": "MESSAGE_RECEIVED",
  "timestamp": "2025-11-15T10:30:15Z",
  "data": {
    "messageId": "msg-server-67891",
    "from": "sip:bob@192.168.1.100:5060",
    "to": "sip:alice@192.168.1.100:5060",
    "sessionId": "alice@192.168.1.100-bob@192.168.1.100",
    "type": "TEXT",
    "content": "你在吗？",
    "timestamp": "2025-11-15T10:30:15Z"
  }
}
```

#### 5.2.2 来电通知

```json
{
  "type": "INCOMING_CALL",
  "timestamp": "2025-11-15T10:35:00Z",
  "data": {
    "callId": "call-uuid-def456",
    "from": "sip:bob@192.168.1.100:5060",
    "fromUser": {
      "userId": "bob@192.168.1.100",
      "displayName": "Bob",
      "avatar": null
    },
    "type": "AUDIO",
    "sdp": "v=0\r\no=- 456789 456789 IN IP4 192.168.1.60\r\n..."
  }
}
```

#### 5.2.3 呼叫状态变化

```json
{
  "type": "CALL_STATE_CHANGED",
  "timestamp": "2025-11-15T10:35:05Z",
  "data": {
    "callId": "call-uuid-def456",
    "oldState": "RINGING",
    "newState": "ACTIVE",
    "peer": "sip:bob@192.168.1.100:5060"
  }
}
```

**状态枚举：**
- `RINGING` - 振铃中
- `ACTIVE` - 通话中
- `ENDED` - 已结束
- `REJECTED` - 已拒绝
- `CANCELLED` - 已取消
- `FAILED` - 失败

#### 5.2.4 用户状态变化

```json
{
  "type": "USER_STATUS_CHANGED",
  "timestamp": "2025-11-15T10:40:00Z",
  "data": {
    "userId": "bob@192.168.1.100",
    "oldStatus": "ONLINE",
    "newStatus": "AWAY",
    "statusMessage": "开会中"
  }
}
```

#### 5.2.5 消息状态更新

```json
{
  "type": "MESSAGE_STATUS_UPDATED",
  "timestamp": "2025-11-15T10:30:20Z",
  "data": {
    "messageId": "msg-server-67890",
    "oldStatus": "SENT",
    "newStatus": "READ",
    "readAt": "2025-11-15T10:30:20Z"
  }
}
```

### 5.3 客户端发送事件

客户端可以通过 WebSocket 发送心跳或状态更新：

#### 心跳

```json
{
  "type": "PING",
  "timestamp": "2025-11-15T10:45:00Z"
}
```

**服务器响应：**
```json
{
  "type": "PONG",
  "timestamp": "2025-11-15T10:45:00Z"
}
```

---

## 6. 配置管理

### 6.1 获取 SIP 配置

**GET** `/config/sip`

**请求头：**
```
Authorization: Bearer {token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "sipServer": "192.168.1.100:5060",
    "transport": "UDP",
    "expires": 3600,
    "outboundProxy": "192.168.1.100:5060",
    "stunServer": null,
    "turnServer": null
  }
}
```

### 6.2 更新 SIP 配置

**PUT** `/config/sip`

**请求头：**
```
Authorization: Bearer {token}
```

**请求体：**
```json
{
  "sipServer": "192.168.1.100:5060",
  "transport": "UDP",
  "expires": 3600,
  "stunServer": "stun.example.com:3478"
}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "message": "配置已更新，重启后生效"
}
```

---

## 7. 统计与监控（管理端）

### 7.1 获取统计数据

**GET** `/stats/summary`

**请求头：**
```
Authorization: Bearer {admin_token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "totalUsers": 150,
    "onlineUsers": 45,
    "totalCalls": 1250,
    "activeCalls": 8,
    "totalMessages": 5600,
    "messagesLast24h": 320,
    "serverUptime": 86400,
    "timestamp": "2025-11-15T10:50:00Z"
  }
}
```

### 7.2 获取通话记录（管理端）

**GET** `/admin/calls?page=0&size=50`

**请求头：**
```
Authorization: Bearer {admin_token}
```

**响应（200 OK）：**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "callId": "call-uuid-abc123",
        "from": "alice@192.168.1.100",
        "to": "bob@192.168.1.100",
        "type": "AUDIO",
        "duration": 125,
        "startTime": "2025-11-15T10:31:00Z",
        "endTime": "2025-11-15T10:33:05Z",
        "status": "COMPLETED"
      }
    ],
    "totalElements": 1250,
    "totalPages": 25,
    "currentPage": 0
  }
}
```

---

## 8. 错误码说明

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| `AUTH_FAILED` | 401 | 认证失败 |
| `TOKEN_EXPIRED` | 401 | Token 过期 |
| `INVALID_TOKEN` | 401 | 无效的 Token |
| `FORBIDDEN` | 403 | 无权限访问 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `USER_OFFLINE` | 400 | 用户离线 |
| `CALL_BUSY` | 400 | 对方忙碌 |
| `CALL_REJECTED` | 400 | 呼叫被拒绝 |
| `INVALID_SDP` | 400 | SDP 格式错误 |
| `SERVER_ERROR` | 500 | 服务器内部错误 |
| `SIP_ERROR` | 500 | SIP 协议错误 |

**错误响应格式：**
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "详细错误信息",
    "details": {
      "field": "具体字段错误信息"
    }
  },
  "timestamp": "2025-11-15T10:50:00Z"
}
```

---

## 9. 数据模型

### 用户状态枚举
```typescript
enum UserStatus {
  ONLINE = "ONLINE",
  AWAY = "AWAY",
  BUSY = "BUSY",
  OFFLINE = "OFFLINE"
}
```

### 消息状态枚举
```typescript
enum MessageStatus {
  SENDING = "SENDING",
  SENT = "SENT",
  DELIVERED = "DELIVERED",
  READ = "READ",
  FAILED = "FAILED"
}
```

### 呼叫状态枚举
```typescript
enum CallState {
  RINGING = "RINGING",
  ACTIVE = "ACTIVE",
  ENDED = "ENDED",
  REJECTED = "REJECTED",
  CANCELLED = "CANCELLED",
  FAILED = "FAILED"
}
```

---

## 10. WebRTC 信令流程

### 发起呼叫流程
1. 前端创建 RTCPeerConnection
2. 生成本地 SDP Offer
3. 调用 `POST /calls` 发送 Offer
4. 通过 WebSocket 接收对方的 Answer
5. 设置远端 SDP
6. 交换 ICE Candidates

### ICE Candidate 交换（通过 WebSocket）

**发送 ICE Candidate:**
```json
{
  "type": "ICE_CANDIDATE",
  "data": {
    "callId": "call-uuid-abc123",
    "candidate": {
      "candidate": "candidate:1 1 UDP 2130706431 192.168.1.50 54321 typ host",
      "sdpMLineIndex": 0,
      "sdpMid": "audio"
    }
  }
}
```

**接收 ICE Candidate:**
```json
{
  "type": "ICE_CANDIDATE_RECEIVED",
  "timestamp": "2025-11-15T10:35:02Z",
  "data": {
    "callId": "call-uuid-abc123",
    "candidate": {
      "candidate": "candidate:2 1 UDP 2130706431 192.168.1.60 54322 typ host",
      "sdpMLineIndex": 0,
      "sdpMid": "audio"
    }
  }
}
```

---

## 附录：前端集成示例

### TypeScript 类型定义

```typescript
// API 响应基础类型
interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: Record<string, any>;
  };
  message?: string;
  timestamp?: string;
}

// 登录请求
interface LoginRequest {
  sipUri: string;
  password: string;
  localIp: string;
  localPort: number;
}

// 发送消息请求
interface SendMessageRequest {
  to: string;
  type: 'TEXT' | 'FILE' | 'IMAGE';
  content: string;
  metadata?: {
    clientMsgId: string;
  };
}

// WebSocket 事件
interface WebSocketEvent {
  type: string;
  timestamp: string;
  data: any;
}
```

### Axios 配置示例

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8081/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器 - 添加 Token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器 - 处理错误
api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      // Token 过期，跳转登录
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```
