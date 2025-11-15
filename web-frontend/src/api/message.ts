import { apiClient } from './client';
import type {
  SendMessageRequest,
  Message,
  ChatSession,
  MessageHistory,
  PageRequest,
} from '@/types/api';

export const messageApi = {
  // 发送消息
  sendMessage: (data: SendMessageRequest) => {
    return apiClient.post<Message>('/messages', data);
  },

  // 获取会话列表
  getSessions: (unreadOnly = false) => {
    return apiClient.get<ChatSession[]>('/messages/sessions', { unreadOnly });
  },

  // 获取会话历史
  getSessionHistory: (sessionId: string, params?: PageRequest & { before?: string }) => {
    return apiClient.get<MessageHistory>(`/messages/sessions/${sessionId}`, params);
  },

  // 标记消息为已读
  markAsRead: (messageId: string) => {
    return apiClient.put(`/messages/${messageId}/read`);
  },
};
