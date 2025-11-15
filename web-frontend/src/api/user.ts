import { apiClient } from './client';
import type { User, UserStatus, PageRequest, PageResponse } from '@/types/api';

export const userApi = {
  // 获取用户列表
  getUsers: (params?: PageRequest & { status?: UserStatus | 'ALL'; search?: string }) => {
    return apiClient.get<PageResponse<User>>('/users', params);
  },

  // 获取用户详情
  getUserDetails: (userId: string) => {
    return apiClient.get<User>(`/users/${userId}`);
  },
};
