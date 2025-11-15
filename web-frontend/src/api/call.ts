import { apiClient } from './client';
import type {
  StartCallRequest,
  AnswerCallRequest,
  Call,
  CallHistoryItem,
  PageRequest,
  PageResponse,
} from '@/types/api';

export const callApi = {
  // 发起呼叫
  startCall: (data: StartCallRequest) => {
    return apiClient.post<Call>('/calls', data);
  },

  // 接听呼叫
  answerCall: (callId: string, data: AnswerCallRequest) => {
    return apiClient.put<Call>(`/calls/${callId}/answer`, data);
  },

  // 拒绝呼叫
  rejectCall: (callId: string, reason?: string) => {
    return apiClient.put(`/calls/${callId}/reject`, { reason });
  },

  // 挂断呼叫
  hangupCall: (callId: string) => {
    return apiClient.delete<Call>(`/calls/${callId}`);
  },

  // 获取呼叫状态
  getCallStatus: (callId: string) => {
    return apiClient.get<Call>(`/calls/${callId}`);
  },

  // 获取通话历史
  getCallHistory: (params?: PageRequest & { type?: 'ALL' | 'MISSED' | 'OUTGOING' | 'INCOMING' }) => {
    return apiClient.get<PageResponse<CallHistoryItem>>('/calls/history', params);
  },
};
