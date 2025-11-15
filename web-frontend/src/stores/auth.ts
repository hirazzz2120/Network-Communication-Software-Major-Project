import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User, UserStatus } from '@/types/api';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  setUser: (user: User) => void;
  setToken: (token: string) => void;
  logout: () => void;
  updateUserStatus: (status: UserStatus, statusMessage?: string) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,

      setUser: (user) =>
        set({
          user,
          isAuthenticated: true,
        }),

      setToken: (token) =>
        set({
          token,
          isAuthenticated: !!token,
        }),

      logout: () =>
        set({
          user: null,
          token: null,
          isAuthenticated: false,
        }),

      updateUserStatus: (status, statusMessage) =>
        set((state) => ({
          user: state.user
            ? {
                ...state.user,
                status,
                statusMessage,
              }
            : null,
        })),
    }),
    {
      name: 'auth-storage',
    }
  )
);
