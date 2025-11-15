import axios, { AxiosInstance, AxiosError } from 'axios';
import type { ApiResponse } from '@/types/api';

class ApiClient {
  private instance: AxiosInstance;

  constructor() {
    this.instance = axios.create({
      baseURL: '/api',
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.setupInterceptors();
  }

  private setupInterceptors() {
    // 请求拦截器 - 添加 Token
    this.instance.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('token');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // 响应拦截器 - 处理错误
    this.instance.interceptors.response.use(
      (response) => {
        return response.data;
      },
      (error: AxiosError<ApiResponse>) => {
        if (error.response?.status === 401) {
          // Token 过期，清除本地存储并跳转登录
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          window.location.href = '/login';
        }
        
        // 返回标准错误格式
        const errorResponse: ApiResponse = {
          success: false,
          error: {
            code: error.response?.data?.error?.code || 'UNKNOWN_ERROR',
            message: error.response?.data?.error?.message || error.message,
            details: error.response?.data?.error?.details,
          },
        };
        
        return Promise.reject(errorResponse);
      }
    );
  }

  get<T = any>(url: string, params?: any): Promise<ApiResponse<T>> {
    return this.instance.get(url, { params });
  }

  post<T = any>(url: string, data?: any): Promise<ApiResponse<T>> {
    return this.instance.post(url, data);
  }

  put<T = any>(url: string, data?: any): Promise<ApiResponse<T>> {
    return this.instance.put(url, data);
  }

  delete<T = any>(url: string): Promise<ApiResponse<T>> {
    return this.instance.delete(url);
  }
}

export const apiClient = new ApiClient();
