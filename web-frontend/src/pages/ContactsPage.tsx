import { useState, useEffect } from 'react';
import { List, Avatar, Input, Tag, Empty } from 'antd';
import { UserOutlined, SearchOutlined } from '@ant-design/icons';
import { userApi } from '@/api/user';
import type { User } from '@/types/api';

export default function ContactsPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [searchText, setSearchText] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadUsers();
  }, []);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const response = await userApi.getUsers({ status: 'ALL' });
      if (response.success && response.data) {
        setUsers(response.data.content);
      }
    } catch (error) {
      console.error('Failed to load users:', error);
    } finally {
      setLoading(false);
    }
  };

  const filteredUsers = users.filter((user) =>
    user.displayName.toLowerCase().includes(searchText.toLowerCase()) ||
    user.userId.toLowerCase().includes(searchText.toLowerCase())
  );

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ONLINE': return 'green';
      case 'AWAY': return 'orange';
      case 'BUSY': return 'red';
      default: return 'default';
    }
  };

  return (
    <div style={{ height: '100%', background: '#fff' }}>
      <div style={{ padding: 16, borderBottom: '1px solid #f0f0f0' }}>
        <h2 style={{ margin: '0 0 16px' }}>联系人</h2>
        <Input
          placeholder="搜索联系人"
          prefix={<SearchOutlined />}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
      </div>

      <List
        loading={loading}
        dataSource={filteredUsers}
        renderItem={(user) => (
          <List.Item
            key={user.userId}
            style={{ padding: '12px 16px' }}
          >
            <List.Item.Meta
              avatar={<Avatar icon={<UserOutlined />} />}
              title={user.displayName}
              description={
                <div>
                  <div style={{ fontSize: 12, color: '#999' }}>{user.sipUri}</div>
                  {user.statusMessage && (
                    <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>
                      {user.statusMessage}
                    </div>
                  )}
                </div>
              }
            />
            <Tag color={getStatusColor(user.status)}>{user.status}</Tag>
          </List.Item>
        )}
        locale={{ emptyText: <Empty description="暂无联系人" /> }}
      />
    </div>
  );
}
