// ============================================
// API 响应基础类型
// ============================================

export interface ApiResponse<T = any> {
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

// ============================================
// 用户相关类型
// ============================================

export enum UserStatus {
  ONLINE = 'ONLINE',
  AWAY = 'AWAY',
  BUSY = 'BUSY',
  OFFLINE = 'OFFLINE',
}

export interface User {
  userId: string;
  sipUri: string;
  displayName: string;
  status: UserStatus;
  statusMessage?: string;
  avatar?: string;
  email?: string;
  phone?: string;
  lastSeen?: string;
}

export interface LoginRequest {
  sipUri: string;
  password: string;
  localIp: string;
  localPort: number;
}

export interface LoginResponse {
  token: string;
  userId: string;
  displayName: string;
  expiresIn: number;
  sipConfig: {
    registered: boolean;
    expires: number;
    localAddress: string;
  };
}

// ============================================
// 消息相关类型
// ============================================

export enum MessageType {
  TEXT = 'TEXT',
  FILE = 'FILE',
  IMAGE = 'IMAGE',
}

export enum MessageStatus {
  SENDING = 'SENDING',
  SENT = 'SENT',
  DELIVERED = 'DELIVERED',
  READ = 'READ',
  FAILED = 'FAILED',
}

export interface Message {
  messageId: string;
  clientMsgId?: string;
  from: string;
  to: string;
  type: MessageType;
  content: string;
  timestamp: string;
  status: MessageStatus;
  isOwn?: boolean;
}

export interface SendMessageRequest {
  to: string;
  type: MessageType;
  content: string;
  metadata?: {
    clientMsgId: string;
  };
}

export interface ChatSession {
  sessionId: string;
  peer: {
    userId: string;
    displayName: string;
    status: UserStatus;
    avatar?: string;
  };
  lastMessage?: {
    content: string;
    timestamp: string;
    type: MessageType;
  };
  unreadCount: number;
  updatedAt: string;
}

export interface MessageHistory {
  sessionId: string;
  peer: {
    userId: string;
    displayName: string;
    status: UserStatus;
  };
  messages: Message[];
  totalMessages: number;
  hasMore: boolean;
}

// ============================================
// 呼叫相关类型
// ============================================

export enum CallType {
  AUDIO = 'AUDIO',
  VIDEO = 'VIDEO',
}

export enum CallState {
  RINGING = 'RINGING',
  ACTIVE = 'ACTIVE',
  ENDED = 'ENDED',
  REJECTED = 'REJECTED',
  CANCELLED = 'CANCELLED',
  FAILED = 'FAILED',
}

export enum CallDirection {
  INCOMING = 'INCOMING',
  OUTGOING = 'OUTGOING',
}

export interface Call {
  callId: string;
  from: string;
  to: string;
  type: CallType;
  state: CallState;
  direction?: CallDirection;
  createdAt: string;
  answeredAt?: string;
  endedAt?: string;
  duration?: number;
  sdp?: string;
}

export interface StartCallRequest {
  to: string;
  type: CallType;
  sdp: string;
}

export interface AnswerCallRequest {
  sdp: string;
}

export interface CallHistoryItem {
  callId: string;
  peer: {
    userId: string;
    displayName: string;
  };
  type: CallType;
  direction: CallDirection;
  state: CallState;
  duration?: number;
  createdAt: string;
  answeredAt?: string;
  endedAt?: string;
}

// ============================================
// WebSocket 事件类型
// ============================================

export enum WebSocketEventType {
  MESSAGE_RECEIVED = 'MESSAGE_RECEIVED',
  INCOMING_CALL = 'INCOMING_CALL',
  CALL_STATE_CHANGED = 'CALL_STATE_CHANGED',
  USER_STATUS_CHANGED = 'USER_STATUS_CHANGED',
  MESSAGE_STATUS_UPDATED = 'MESSAGE_STATUS_UPDATED',
  ICE_CANDIDATE_RECEIVED = 'ICE_CANDIDATE_RECEIVED',
  PING = 'PING',
  PONG = 'PONG',
}

export interface WebSocketEvent<T = any> {
  type: WebSocketEventType;
  timestamp: string;
  data: T;
}

export interface MessageReceivedEvent {
  messageId: string;
  from: string;
  to: string;
  sessionId: string;
  type: MessageType;
  content: string;
  timestamp: string;
}

export interface IncomingCallEvent {
  callId: string;
  from: string;
  fromUser: {
    userId: string;
    displayName: string;
    avatar?: string;
  };
  type: CallType;
  sdp: string;
}

export interface CallStateChangedEvent {
  callId: string;
  oldState: CallState;
  newState: CallState;
  peer: string;
}

export interface UserStatusChangedEvent {
  userId: string;
  oldStatus: UserStatus;
  newStatus: UserStatus;
  statusMessage?: string;
}

export interface IceCandidateEvent {
  callId: string;
  candidate: RTCIceCandidateInit;
}

// ============================================
// 分页类型
// ============================================

export interface PageRequest {
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize?: number;
}
