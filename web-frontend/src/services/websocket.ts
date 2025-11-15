import { io, Socket } from 'socket.io-client';
import type {
  WebSocketEvent,
  WebSocketEventType,
  MessageReceivedEvent,
  IncomingCallEvent,
  CallStateChangedEvent,
  UserStatusChangedEvent,
  IceCandidateEvent,
} from '@/types/api';

type EventCallback<T = any> = (data: T) => void;

class WebSocketClient {
  private socket: Socket | null = null;
  private eventHandlers: Map<WebSocketEventType, Set<EventCallback>> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  connect(token: string) {
    if (this.socket?.connected) {
      console.warn('WebSocket already connected');
      return;
    }

    this.socket = io('ws://localhost:8081', {
      path: '/ws/events',
      query: { token },
      transports: ['websocket'],
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionAttempts: this.maxReconnectAttempts,
    });

    this.setupEventListeners();
  }

  private setupEventListeners() {
    if (!this.socket) return;

    this.socket.on('connect', () => {
      console.log('WebSocket connected');
      this.reconnectAttempts = 0;
    });

    this.socket.on('disconnect', () => {
      console.log('WebSocket disconnected');
    });

    this.socket.on('error', (error: Error) => {
      console.error('WebSocket error:', error);
    });

    this.socket.on('reconnect_attempt', (attempt: number) => {
      console.log(`WebSocket reconnection attempt ${attempt}`);
      this.reconnectAttempts = attempt;
    });

    this.socket.on('reconnect_failed', () => {
      console.error('WebSocket reconnection failed');
    });

    // 监听服务器事件
    this.socket.on('message', (event: WebSocketEvent) => {
      this.handleServerEvent(event);
    });
  }

  private handleServerEvent(event: WebSocketEvent) {
    console.log('Received WebSocket event:', event.type, event.data);

    const handlers = this.eventHandlers.get(event.type as WebSocketEventType);
    if (handlers) {
      handlers.forEach((callback) => callback(event.data));
    }
  }

  on<T = any>(eventType: WebSocketEventType, callback: EventCallback<T>) {
    if (!this.eventHandlers.has(eventType)) {
      this.eventHandlers.set(eventType, new Set());
    }
    this.eventHandlers.get(eventType)!.add(callback);
  }

  off<T = any>(eventType: WebSocketEventType, callback: EventCallback<T>) {
    const handlers = this.eventHandlers.get(eventType);
    if (handlers) {
      handlers.delete(callback);
    }
  }

  emit(eventType: WebSocketEventType, data: any) {
    if (this.socket?.connected) {
      this.socket.emit('message', {
        type: eventType,
        timestamp: new Date().toISOString(),
        data,
      });
    } else {
      console.warn('Cannot emit event: WebSocket not connected');
    }
  }

  disconnect() {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
    }
    this.eventHandlers.clear();
  }

  isConnected(): boolean {
    return this.socket?.connected ?? false;
  }

  // 便捷方法
  onMessageReceived(callback: EventCallback<MessageReceivedEvent>) {
    this.on(WebSocketEventType.MESSAGE_RECEIVED, callback);
  }

  onIncomingCall(callback: EventCallback<IncomingCallEvent>) {
    this.on(WebSocketEventType.INCOMING_CALL, callback);
  }

  onCallStateChanged(callback: EventCallback<CallStateChangedEvent>) {
    this.on(WebSocketEventType.CALL_STATE_CHANGED, callback);
  }

  onUserStatusChanged(callback: EventCallback<UserStatusChangedEvent>) {
    this.on(WebSocketEventType.USER_STATUS_CHANGED, callback);
  }

  onIceCandidateReceived(callback: EventCallback<IceCandidateEvent>) {
    this.on(WebSocketEventType.ICE_CANDIDATE_RECEIVED, callback);
  }

  sendIceCandidate(callId: string, candidate: RTCIceCandidateInit) {
    this.emit(WebSocketEventType.ICE_CANDIDATE_RECEIVED, {
      callId,
      candidate,
    });
  }
}

export const wsClient = new WebSocketClient();
