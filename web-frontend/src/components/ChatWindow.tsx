import { useState, useEffect, useRef } from 'react';
import { Input, Button, List, Avatar, message as antMessage } from 'antd';
import { SendOutlined, UserOutlined } from '@ant-design/icons';
import { useChatStore } from '@/stores/chat';
import { messageApi } from '@/api/message';
import type { Message } from '@/types/api';
import dayjs from 'dayjs';

interface ChatWindowProps {
  sessionId: string;
}

export default function ChatWindow({ sessionId }: ChatWindowProps) {
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const { messages, setMessages, addMessage } = useChatStore();

  useEffect(() => {
    loadMessages();
  }, [sessionId]);

  useEffect(() => {
    scrollToBottom();
  }, [messages[sessionId]]);

  const loadMessages = async () => {
    try {
      const response = await messageApi.getSessionHistory(sessionId);
      if (response.success && response.data) {
        setMessages(sessionId, response.data.messages);
      }
    } catch (error) {
      console.error('Failed to load messages:', error);
    }
  };

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const handleSend = async () => {
    if (!inputText.trim()) return;

    const tempMessage: Message = {
      messageId: `temp-${Date.now()}`,
      from: 'me',
      to: sessionId,
      type: 'TEXT',
      content: inputText,
      timestamp: new Date().toISOString(),
      status: 'SENDING',
      isOwn: true,
    };

    addMessage(sessionId, tempMessage);
    setInputText('');
    setLoading(true);

    try {
      const response = await messageApi.sendMessage({
        to: sessionId,
        type: 'TEXT',
        content: inputText,
        metadata: {
          clientMsgId: tempMessage.messageId,
        },
      });

      if (response.success) {
        // Update temp message with server response
        antMessage.success('消息已发送');
      }
    } catch (error) {
      antMessage.error('发送失败');
    } finally {
      setLoading(false);
    }
  };

  const currentMessages = messages[sessionId] || [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: 16, borderBottom: '1px solid #f0f0f0', background: '#fff' }}>
        <h3 style={{ margin: 0 }}>会话: {sessionId}</h3>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: 16, background: '#f5f5f5' }}>
        <List
          dataSource={currentMessages}
          renderItem={(msg) => (
            <div
              key={msg.messageId}
              style={{
                display: 'flex',
                justifyContent: msg.isOwn ? 'flex-end' : 'flex-start',
                marginBottom: 16,
              }}
            >
              <div style={{ display: 'flex', maxWidth: '70%', flexDirection: msg.isOwn ? 'row-reverse' : 'row' }}>
                <Avatar icon={<UserOutlined />} style={{ margin: '0 8px' }} />
                <div>
                  <div
                    style={{
                      padding: '8px 12px',
                      borderRadius: 8,
                      background: msg.isOwn ? '#1890ff' : '#fff',
                      color: msg.isOwn ? '#fff' : '#000',
                    }}
                  >
                    {msg.content}
                  </div>
                  <div style={{ fontSize: 12, color: '#999', marginTop: 4, textAlign: msg.isOwn ? 'right' : 'left' }}>
                    {dayjs(msg.timestamp).format('HH:mm')}
                  </div>
                </div>
              </div>
            </div>
          )}
        />
        <div ref={messagesEndRef} />
      </div>

      <div style={{ padding: 16, background: '#fff', borderTop: '1px solid #f0f0f0' }}>
        <Input.Search
          placeholder="输入消息..."
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          onSearch={handleSend}
          onPressEnter={handleSend}
          loading={loading}
          enterButton={<SendOutlined />}
          size="large"
        />
      </div>
    </div>
  );
}
