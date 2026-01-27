import React, { useState,useEffect } from 'react';
import {
  Row,
  Col,
  Card,
  Typography,
  Form,
  Input,
  Button,
  message,
  Space,
  Divider,
  Image,
  Alert,
  Steps,
} from 'antd';
import {
  VideoCameraOutlined,
  LinkOutlined,
  SaveOutlined,
  ArrowLeftOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import './AddVideo.css';
import { useNavigate, Navigate } from 'react-router-dom';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const AddVideo = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [user, setUser] = useState(null);
  const [csrfToken, setCsrfToken] = useState(null);
  const [previewData, setPreviewData] = useState(null);
  const [currentStep, setCurrentStep] = useState(0);
  const [urlLoading, setUrlLoading] = useState(false);
  const navigate = useNavigate();

    useEffect(() => {
        fetch('http://localhost:8080/csrf-token', {
        credentials: 'include',
        })
        .then(res => res.json())
        .then(data => setCsrfToken(data.csrfToken))
        .catch(() => message.error('Nie udało się pobrać CSRF tokena'));
    }, []);

    useEffect(() => {
    fetch('http://localhost:8080/check-auth', {
        credentials: 'include',
    })
        .then(res => {
        if (!res.ok) throw new Error('Błąd autoryzacji');
        return res.json();
        })
        .then(data => setUser(data))
        .catch(err => {
        console.error('Błąd uwierzytelnienia:', err);
        message.error('Nie jesteś zalogowany. Zaloguj się, aby dodać film.');
        });
    }, []);

  const generatePreview = (url, title) => {
    if (!url) return null;
    let thumbnail = 'https://via.placeholder.com/320x180?text=Video';
    let videoId = null;
    if (url.includes('youtube.com') || url.includes('youtu.be')) {
      try {
        if (url.includes('youtube.com')) {
          const urlParams = new URLSearchParams(new URL(url).search);
          videoId = urlParams.get('v');
        } else if (url.includes('youtu.be')) {
          videoId = url.split('/').pop().split('?')[0];
        }
        if (videoId) {
          thumbnail = `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`;
        }
      } catch (error) {
        console.log('Error parsing YouTube URL');
      }
    }
    return {
      thumbnail,
      videoId,
      platform: videoId ? 'YouTube' : 'Inne',
      title: title || 'Nowy film',
    };
  };

  const handleUrlChange = (e) => {
    const url = e.target.value;
    if (url) {
      setUrlLoading(true);
      setTimeout(() => {
        const preview = generatePreview(url, form.getFieldValue('title'));
        setPreviewData(preview);
        setUrlLoading(false);
        if (preview.videoId) {
          setCurrentStep(1);
        }
      }, 500);
    } else {
      setPreviewData(null);
      setCurrentStep(0);
    }
  };

  const handleTitleChange = (e) => {
    const title = e.target.value;
    const url = form.getFieldValue('url');
    if (url) {
      const preview = generatePreview(url, title);
      setPreviewData(preview);
    }
  };

  const onFinish = async (values) => {
        if (!user) {
            message.error('Brak danych użytkownika');
            return;
        }

        if (!csrfToken) {
            message.error('Brak CSRF tokena. Odśwież stronę.');
            return;
        }

        const payload = {
            title: values.title,
            url: values.url,
            ownerId: user.id
        };

  setLoading(true);
  try {
    const res = await fetch('http://localhost:8080/addVideo', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken,
      },
      body: JSON.stringify(payload),
    });

    if (!res.ok) throw new Error('Nie udało się dodać filmu');

    message.success('Film został dodany pomyślnie!');
     navigate('/profile');
    setTimeout(() => {
      form.resetFields();
      setPreviewData(null);
      setCurrentStep(0);
    }, 3000);
  } catch (error) {
    console.error(error);
    message.error('Nie udało się dodać filmu. Spróbuj ponownie.');
  } finally {
    setLoading(false);
  }
};


  const steps = [
    { title: 'Podstawowe informacje', description: 'Dodaj URL i tytuł filmu', icon: <VideoCameraOutlined /> },
    { title: 'Podgląd', description: 'Sprawdź jak będzie wyglądać', icon: <EyeOutlined /> },
    { title: 'Gotowe', description: 'Film został dodany', icon: <CheckCircleOutlined /> },
  ];
  return (
    <div className="add-video-container">
      <Card className="header-card">
        <Row align="middle" justify="space-between">
          <Col>
            <Space>
              <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/profile')}>
                Powrót do profilu
              </Button>
              <Divider type="vertical" />
              <Title level={2} className="title-no-margin">
                <VideoCameraOutlined /> Dodaj nowy film
              </Title>
            </Space>
          </Col>
          <Col>
            <Text type="secondary">
              
            </Text>
          </Col>
        </Row>
      </Card>

      <Card className="steps-card">
        <Steps current={currentStep} size="small">
          {steps.map((step, index) => (
            <Steps.Step key={index} title={step.title} description={step.description} icon={step.icon} />
          ))}
        </Steps>
      </Card>

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={14}>
          <Card title="Szczegóły filmu" className="form-card">
            <Form form={form} layout="vertical" onFinish={onFinish} size="large">
              <Form.Item
                label="URL filmu"
                name="url"
                rules={[
                  { required: true, message: 'Podaj URL filmu' },
                  { type: 'url', message: 'Podaj prawidłowy URL' },
                ]}
              >
                <Input
                  prefix={<LinkOutlined />}
                  placeholder="https://www.youtube.com/watch?v=..."
                  onChange={handleUrlChange}
                  suffix={urlLoading && <LoadingOutlined />}
                />
              </Form.Item>

              <Form.Item
                label="Tytuł filmu"
                name="title"
                rules={[
                  { required: true, message: 'Podaj tytuł filmu' },
                  { min: 3, message: 'Tytuł musi mieć co najmniej 3 znaki' },
                  { max: 100, message: 'Tytuł może mieć maksymalnie 100 znaków' },
                ]}
              >
                <Input placeholder="Wpisz tytuł filmu..." onChange={handleTitleChange} showCount maxLength={100} />
              </Form.Item>

              <Form.Item label="Opis (opcjonalnie)" name="description">
                <TextArea rows={4} placeholder="Dodaj opis filmu..." showCount maxLength={500} />
              </Form.Item>

              <Divider />

              <Form.Item>
                <Space>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={loading}
                    size="large"
                    icon={<SaveOutlined />}
                    disabled={!previewData}  
                  >
                    {loading ? 'Dodawanie...' : 'Dodaj film'}
                  </Button>
                  <Button
                    size="large"
                    onClick={() => {
                      form.resetFields();
                      setPreviewData(null);
                      setCurrentStep(0);
                    }}
                  >
                    Wyczyść
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card title="Podgląd" className="preview-card">
            {previewData ? (
              <div>
                <div className="preview-thumbnail-wrapper">
                  <Image
                    src={previewData.thumbnail}
                    alt="Miniatura"
                    width="100%"
                    height={200}
                    style={{ objectFit: 'cover', borderRadius: 8 }}
                    fallback="https://via.placeholder.com/320x180?text=Błąd+ładowania"
                  />
                  <div className="preview-thumbnail-overlay">
                    <PlayCircleOutlined />
                  </div>
                </div>

                <Title level={4} ellipsis={{ tooltip: true }}>
                  {previewData.title}
                </Title>

                <Space direction="vertical" style={{ width: '100%' }}>
                  <Text type="secondary">
                    <strong>Platforma:</strong> {previewData.platform}
                  </Text>
                  <Text type="secondary">
                    
                  </Text>
                  {previewData.videoId && (
                    <Text type="secondary">
                      <strong>ID wideo:</strong> {previewData.videoId}
                    </Text>
                  )}
                </Space>

                <Alert
                  message="Podgląd wygenerowany"
                  description="Sprawdź, czy wszystkie informacje są poprawne przed dodaniem filmu."
                  type="info"
                  showIcon
                  className="preview-alert"
                />
              </div>
            ) : (
              <div className="preview-placeholder">
                <VideoCameraOutlined className="preview-icon" />
                <Paragraph type="secondary">Wprowadź URL filmu, aby zobaczyć podgląd</Paragraph>
              </div>
            )}
          </Card>

          <Card title="Wskazówki" className="tips-card">
            <Space direction="vertical">
              <Text>
                <CheckCircleOutlined style={{ color: '#52c41a' }} /> Obsługiwane platformy: YouTube, Vimeo i inne
              </Text>
              <Text>
                <CheckCircleOutlined style={{ color: '#52c41a' }} /> Tytuł powinien być opisowy i przyciągający uwagę
              </Text>
              <Text>
                <CheckCircleOutlined style={{ color: '#52c41a' }} /> Upewnij się, że URL jest publiczny i dostępny
              </Text>
            </Space>
          </Card>
        </Col>
      </Row>

      {currentStep === 2 && (
        <Card className="success-card">
          <CheckCircleOutlined className="success-icon" />
          <Title level={3} className="success-title">
            Film został dodany pomyślnie!
          </Title>
          <Paragraph>
            Twój film jest teraz dostępny w Twoim profilu. Możesz dodać kolejny film lub wrócić do profilu.
          </Paragraph>
        </Card>
      )}
    </div>
  );
};

export default AddVideo;
