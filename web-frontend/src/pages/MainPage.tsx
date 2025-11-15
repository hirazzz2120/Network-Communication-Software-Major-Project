import { useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from 'antd';
import Sidebar from '@/components/Sidebar';
import ChatPage from './ChatPage';
import CallPage from './CallPage';
import ContactsPage from './ContactsPage';
import { wsClient } from '@/services/websocket';
import { useAuthStore } from '@/stores/auth';
import { useChatStore } from '@/stores/chat';
import { useCallStore } from '@/stores/call';

const { Content } = Layout;

export default function MainPage() {
  const token = useAuthStore((state) => state.token);
  const addMessage = useChatStore((state) => state.addMessage);
  const { setIncomingCall, updateCallState } = useCallStore();

  useEffect(() => {
    if (!token) return;

    // 设置 WebSocket 事件监听
    wsClient.onMessageReceived((data) => {
      console.log('New message received:', data);
      addMessage(data.sessionId, {
        messageId: data.messageId,
        from: data.from,
        to: data.to,
        type: data.type,
        content: data.content,
        timestamp: data.timestamp,
        status: 'DELIVERED',
        isOwn: false,
      });
    });

    wsClient.onIncomingCall((data) => {
      console.log('Incoming call:', data);
      setIncomingCall({
        callId: data.callId,
        from: data.from,
        to: '', // Will be filled by backend
        type: data.type,
        state: 'RINGING',
        createdAt: new Date().toISOString(),
        sdp: data.sdp,
      });
    });

    wsClient.onCallStateChanged((data) => {
      console.log('Call state changed:', data);
      updateCallState(data.callId, data.newState);
    });

    return () => {
      // Cleanup event listeners
      wsClient.disconnect();
    };
  }, [token, addMessage, setIncomingCall, updateCallState]);

  return (
    <Layout style={{ height: '100vh' }}>
      <Sidebar />
      <Content style={{ background: '#f0f2f5' }}>
        <Routes>
          <Route path="/" element={<Navigate to="/chat" replace />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/call" element={<CallPage />} />
          <Route path="/contacts" element={<ContactsPage />} />
        </Routes>
      </Content>
    </Layout>
  );
}
