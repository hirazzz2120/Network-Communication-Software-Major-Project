import { Layout, Menu } from 'antd';
import { MessageOutlined, PhoneOutlined, TeamOutlined, SettingOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth';

const { Sider } = Layout;

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((state) => state.user);

  const menuItems = [
    {
      key: '/chat',
      icon: <MessageOutlined />,
      label: '消息',
    },
    {
      key: '/call',
      icon: <PhoneOutlined />,
      label: '通话',
    },
    {
      key: '/contacts',
      icon: <TeamOutlined />,
      label: '联系人',
    },
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: '设置',
    },
  ];

  return (
    <Sider width={200} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
      <div style={{ padding: '20px', textAlign: 'center', borderBottom: '1px solid #f0f0f0' }}>
        <h3 style={{ margin: 0 }}>SIP 客户端</h3>
        <p style={{ fontSize: 12, color: '#999', margin: '8px 0 0' }}>
          {user?.displayName || 'Guest'}
        </p>
      </div>
      <Menu
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={({ key }) => navigate(key)}
        style={{ borderRight: 0 }}
      />
    </Sider>
  );
}
