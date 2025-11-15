import { apiClient } from './client';
import type {
  LoginRequest,
  LoginResponse,
  User,
  UserStatus,
} from '@/types/api';

export const authApi = {
  // 登录
  login: (data: LoginRequest) => {
    return apiClient.post<LoginResponse>('/auth/login', data);
  },

  // 注销
  logout: () => {
    return apiClient.post('/auth/logout');
  },

  // 获取当前用户信息
  getProfile: () => {
    return apiClient.get<User>('/auth/profile');
  },

  // 更新用户状态
  updateStatus: (status: UserStatus, statusMessage?: string) => {
    return apiClient.put('/auth/status', { status, statusMessage });
  },
};
