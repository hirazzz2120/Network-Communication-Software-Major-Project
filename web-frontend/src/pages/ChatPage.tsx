import { useState, useEffect } from 'react';
import { Layout, List, Input, Avatar, Badge, Empty } from 'antd';
import { UserOutlined, SearchOutlined } from '@ant-design/icons';
import { useChatStore } from '@/stores/chat';
import { messageApi } from '@/api/message';
import ChatWindow from '@/components/ChatWindow';

const { Sider, Content } = Layout;

export default function ChatPage() {
  const { sessions, currentSessionId, setCurrentSession, setSessions } = useChatStore();
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    // 加载会话列表
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      const response = await messageApi.getSessions();
      if (response.success && response.data) {
        setSessions(response.data);
      }
    } catch (error) {
      console.error('Failed to load sessions:', error);
    }
  };

  const filteredSessions = sessions.filter((session) =>
    session.peer.displayName.toLowerCase().includes(searchText.toLowerCase())
  );

  return (
    <Layout style={{ height: '100%' }}>
      <Sider width={300} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: 16 }}>
          <Input
            placeholder="搜索会话"
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
          />
        </div>
        <List
          dataSource={filteredSessions}
          renderItem={(session) => (
            <List.Item
              key={session.sessionId}
              onClick={() => setCurrentSession(session.sessionId)}
              style={{
                cursor: 'pointer',
                backgroundColor: currentSessionId === session.sessionId ? '#e6f7ff' : 'transparent',
                padding: '12px 16px',
              }}
            >
              <List.Item.Meta
                avatar={
                  <Badge count={session.unreadCount} size="small">
                    <Avatar icon={<UserOutlined />} />
                  </Badge>
                }
                title={session.peer.displayName}
                description={
                  <div style={{ fontSize: 12, color: '#999' }}>
                    {session.lastMessage?.content || '暂无消息'}
                  </div>
                }
              />
            </List.Item>
          )}
          locale={{ emptyText: <Empty description="暂无会话" /> }}
        />
      </Sider>
      <Content>
        {currentSessionId ? (
          <ChatWindow sessionId={currentSessionId} />
        ) : (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
            <Empty description="请选择一个会话开始聊天" />
          </div>
        )}
      </Content>
    </Layout>
  );
}
