import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, message, Space } from 'antd';
import { UserOutlined, LockOutlined, GlobalOutlined, NumberOutlined } from '@ant-design/icons';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import { wsClient } from '@/services/websocket';
import type { LoginRequest } from '@/types/api';

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setUser, setToken } = useAuthStore();

  const onFinish = async (values: LoginRequest) => {
    setLoading(true);
    try {
      const response = await authApi.login(values);
      
      if (response.success && response.data) {
        // 保存 token 和用户信息
        setToken(response.data.token);
        localStorage.setItem('token', response.data.token);
        
        // 保存用户信息
        setUser({
          userId: response.data.userId,
          sipUri: values.sipUri,
          displayName: response.data.displayName,
          status: 'ONLINE',
          registered: true,
        } as any);

        // 连接 WebSocket
        wsClient.connect(response.data.token);

        message.success('登录成功！');
        navigate('/');
      }
    } catch (error: any) {
      message.error(error?.error?.message || '登录失败，请检查网络连接');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card
        title="SIP 通信客户端"
        style={{ width: 450, boxShadow: '0 8px 16px rgba(0,0,0,0.1)' }}
      >
        <Form
          name="login"
          onFinish={onFinish}
          autoComplete="off"
          layout="vertical"
          initialValues={{
            sipUri: 'sip:alice@192.168.1.100:5060',
            password: '',
            localIp: '192.168.1.50',
            localPort: 5070,
          }}
        >
          <Form.Item
            label="SIP URI"
            name="sipUri"
            rules={[
              { required: true, message: '请输入 SIP URI' },
              { pattern: /^sip:/, message: '请输入有效的 SIP URI (如: sip:user@host:port)' },
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="sip:alice@192.168.1.100:5060"
              size="large"
            />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请输入密码"
              size="large"
            />
          </Form.Item>

          <Space direction="horizontal" style={{ width: '100%' }} size="large">
            <Form.Item
              label="本地 IP"
              name="localIp"
              rules={[{ required: true, message: '请输入本地 IP' }]}
              style={{ flex: 1, marginBottom: 0 }}
            >
              <Input
                prefix={<GlobalOutlined />}
                placeholder="192.168.1.50"
                size="large"
              />
            </Form.Item>

            <Form.Item
              label="本地端口"
              name="localPort"
              rules={[
                { required: true, message: '请输入端口' },
                { type: 'number', min: 1024, max: 65535, message: '端口范围: 1024-65535' },
              ]}
              style={{ width: 150, marginBottom: 0 }}
            >
              <Input
                prefix={<NumberOutlined />}
                type="number"
                placeholder="5070"
                size="large"
              />
            </Form.Item>
          </Space>

          <Form.Item style={{ marginTop: 24, marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={loading} size="large" block>
              登录
            </Button>
          </Form.Item>
        </Form>

        <div style={{ marginTop: 16, fontSize: 12, color: '#999', textAlign: 'center' }}>
          <p>提示：首次使用请确保 MSS 服务器已启动</p>
          <p>本地 IP 需要是 SIP 服务器可访问的地址</p>
        </div>
      </Card>
    </div>
  );
}
