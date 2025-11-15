import { create } from 'zustand';
import type { ChatSession, Message } from '@/types/api';

interface ChatState {
  sessions: ChatSession[];
  currentSessionId: string | null;
  messages: Record<string, Message[]>; // sessionId -> messages
  
  setSessions: (sessions: ChatSession[]) => void;
  setCurrentSession: (sessionId: string) => void;
  addMessage: (sessionId: string, message: Message) => void;
  setMessages: (sessionId: string, messages: Message[]) => void;
  updateMessageStatus: (messageId: string, status: Message['status']) => void;
  incrementUnreadCount: (sessionId: string) => void;
  clearUnreadCount: (sessionId: string) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  sessions: [],
  currentSessionId: null,
  messages: {},

  setSessions: (sessions) => set({ sessions }),

  setCurrentSession: (sessionId) => set({ currentSessionId: sessionId }),

  addMessage: (sessionId, message) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: [...(state.messages[sessionId] || []), message],
      },
    })),

  setMessages: (sessionId, messages) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: messages,
      },
    })),

  updateMessageStatus: (messageId, status) =>
    set((state) => {
      const newMessages = { ...state.messages };
      Object.keys(newMessages).forEach((sessionId) => {
        newMessages[sessionId] = newMessages[sessionId].map((msg) =>
          msg.messageId === messageId ? { ...msg, status } : msg
        );
      });
      return { messages: newMessages };
    }),

  incrementUnreadCount: (sessionId) =>
    set((state) => ({
      sessions: state.sessions.map((session) =>
        session.sessionId === sessionId
          ? { ...session, unreadCount: session.unreadCount + 1 }
          : session
      ),
    })),

  clearUnreadCount: (sessionId) =>
    set((state) => ({
      sessions: state.sessions.map((session) =>
        session.sessionId === sessionId
          ? { ...session, unreadCount: 0 }
          : session
      ),
    })),
}));
