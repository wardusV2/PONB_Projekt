import React, { useEffect, useState } from 'react';
import axios from 'axios';
import {
  Card,
  Row,
  Col,
  message,
  Typography,
  Skeleton,
  Empty,
  Tag,
  Button,
  Space,
  Divider,
  Avatar,
  Tooltip,
  Badge,
  Affix,
  BackTop
} from 'antd';
import {
  PlayCircleOutlined,
  EyeOutlined,
  ClockCircleOutlined,
  HeartOutlined,
  ShareAltOutlined,
  FilterOutlined,
  ThunderboltOutlined,
  FireOutlined,
  StarOutlined,
  VerticalAlignTopOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import './Recommendation.css';

const { Title, Text, Paragraph } = Typography;
const { Meta } = Card;

const Recommendation = () => {
  const [videos, setVideos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filteredVideos, setFilteredVideos] = useState([]);
  const [activeFilter, setActiveFilter] = useState('all');
  const navigate = useNavigate();

  useEffect(() => {
    const fetchUserAndVideos = async () => {
      try {
        setLoading(true);

        // 1. Sprawdź autoryzację i pobierz userId
        const authRes = await fetch('http://localhost:8080/check-auth', {
          method: 'GET',
          credentials: 'include',
        });
        if (!authRes.ok) {
          throw new Error('Nieautoryzowany użytkownik');
        }
        const userData = await authRes.json();
        const userId = userData.id;

        // 2. Pobierz filmy z subskrypcji
        let response = await fetch(`http://localhost:8080/subscribedVideos/${userId}`, {
          method: 'GET',
          credentials: 'include',
        });
        if (!response.ok) {
          throw new Error('Nie udało się pobrać filmów z subskrypcji');
        }
        let data = await response.json();

        // 3. Fallback, jeśli brak subskrypcji
        if (Array.isArray(data) && data.length === 0) {
          const fallbackRes = await fetch('http://localhost:8080/AllVideos', {
            method: 'GET',
            credentials: 'include',
          });
          if (!fallbackRes.ok) {
            throw new Error('Nie udało się pobrać wszystkich filmów');
          }
          data = await fallbackRes.json();
        }

        setVideos(data);
        setFilteredVideos(data);

      } catch (error) {
        console.error(error);
        message.error(error.message || 'Błąd podczas pobierania filmów');
      } finally {
        setLoading(false);
      }
    };

    fetchUserAndVideos();
  }, []);

  const getThumbnail = (video) => {
    if (video.thumbnail) return video.thumbnail;
    if (video.url.includes('youtube.com')) {
      try {
        const videoId = new URLSearchParams(new URL(video.url).search).get('v');
        return `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`;
      } catch {
        return 'https://via.placeholder.com/320x180?text=Video';
      }
    }
    return video.url;
  };

  const getRandomViews = () =>
    Math.floor(Math.random() * 50000) + 1000;

  const getRandomDuration = () => {
    const minutes = Math.floor(Math.random() * 20) + 1;
    const seconds = Math.floor(Math.random() * 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  const handleFilter = (filter) => {
    setActiveFilter(filter);
    if (filter === 'all') {
      setFilteredVideos(videos);
    } else if (filter === 'popular') {
      setFilteredVideos([...videos]
        .sort(() => Math.random() - 0.5)
        .slice(0, Math.ceil(videos.length * 0.7)));
    } else if (filter === 'recent') {
      setFilteredVideos([...videos]
        .reverse()
        .slice(0, Math.ceil(videos.length * 0.8)));
    }
  };

  const filterButtons = [
    { key: 'all', label: 'Wszystkie', icon: <ThunderboltOutlined /> },
    { key: 'popular', label: 'Popularne', icon: <FireOutlined /> },
    { key: 'recent', label: 'Najnowsze', icon: <ClockCircleOutlined /> }
  ];

  if (loading) {
    return (
      <div className="recommendation-container">
        <div className="recommendation-header">
          <Skeleton.Input style={{ width: 300, height: 40 }} active />
          <Skeleton.Button style={{ width: 100, height: 32 }} active />
        </div>
        <Row gutter={[24, 24]} style={{ marginTop: 32 }}>
          {Array.from({ length: 8 }).map((_, index) => (
            <Col xs={24} sm={12} md={8} lg={6} key={index}>
              <Card>
                <Skeleton active avatar paragraph={{ rows: 2 }} />
              </Card>
            </Col>
          ))}
        </Row>
      </div>
    );
  }

  return (
    <div className="recommendation-container">
      <div className="recommendation-header">
        <div className="header-content">
          <div className="title-section">
            <Title level={1} className="main-title">
              <ThunderboltOutlined className="title-icon" />
              Rekomendacje dla Ciebie
            </Title>
            <Paragraph className="subtitle">
              Odkryj filmy dopasowane do Twoich zainteresowań
            </Paragraph>
          </div>
          
          <div className="stats-section">
            <Space size="large">
              <div className="stat-item">
                <Text type="secondary">Znaleziono</Text>
                <Title level={3} style={{ margin: 0, color: '#1890ff' }}>
                  {filteredVideos.length}
                </Title>
                <Text type="secondary">filmów</Text>
              </div>
            </Space>
          </div>
        </div>
      </div>

      <Affix offsetTop={20}>
        <Card className="filter-card" bodyStyle={{ padding: '16px 24px' }}>
          <Space size="middle" align="center">
            <FilterOutlined style={{ color: '#1890ff' }} />
            <Text strong>Filtruj:</Text>
            <Space size="small">
              {filterButtons.map(filter => (
                <Button
                  key={filter.key}
                  type={activeFilter === filter.key ? 'primary' : 'default'}
                  icon={filter.icon}
                  onClick={() => handleFilter(filter.key)}
                  className="filter-button"
                >
                  {filter.label}
                </Button>
              ))}
            </Space>
            <Divider type="vertical" />
            <Badge count={filteredVideos.length} style={{ backgroundColor: '#52c41a' }}>
              <Button icon={<StarOutlined />}>
                Ulubione
              </Button>
            </Badge>
          </Space>
        </Card>
      </Affix>

      <div className="videos-section">
        {filteredVideos.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Brak rekomendowanych filmów"
            imageStyle={{ height: 120 }}
          >
            <Button type="primary" icon={<ThunderboltOutlined />}>
              Odśwież rekomendacje
            </Button>
          </Empty>
        ) : (
          <Row gutter={[24, 32]}>
            {filteredVideos.map((video, index) => (
              <Col xs={24} sm={12} md={8} lg={6} key={video.id}>
                <Card
                  hoverable
                  className="video-card"
                  cover={
                    <div className="video-cover">
                      <img
                        alt={video.title}
                        src={getThumbnail(video)}
                        className="video-thumbnail"
                      />
                      <div className="video-overlay">
                        <PlayCircleOutlined className="play-button" />
                        <div className="video-duration">
                          {getRandomDuration()}
                        </div>
                      </div>
                      {index < 3 && (
                        <div className="trending-badge">
                          <FireOutlined /> Trending
                        </div>
                      )}
                    </div>
                  }
                  actions={[
                    <Tooltip title="Polub"><HeartOutlined key="like" /></Tooltip>,
                    <Tooltip title="Udostępnij"><ShareAltOutlined key="share" /></Tooltip>,
                    <Tooltip title="Dodaj do ulubionych"><StarOutlined key="favorite" /></Tooltip>
                  ]}
                  onClick={() => navigate(`/video/${video.id}`)}
                >
                  <Meta
                    avatar={
                      <Avatar size="small" style={{ backgroundColor: '#1890ff' }}>
                        {video.title?.charAt(0)?.toUpperCase()}
                      </Avatar>
                    }
                    title={
                      <Tooltip title={video.title}>
                        <Text ellipsis className="video-title">{video.title}</Text>
                      </Tooltip>
                    }
                    description={
                      <Space direction="vertical" size="small" style={{ width: '100%' }}>
                        <Text type="secondary" className="channel-name">
                          Kanał Autora
                        </Text>
                        <Space size="middle" className="video-stats">
                          <Space size="small"><EyeOutlined /><Text type="secondary">{getRandomViews().toLocaleString()}</Text></Space>
                          <Space size="small"><ClockCircleOutlined /><Text type="secondary">{Math.floor(Math.random() * 7) + 1}d temu</Text></Space>
                        </Space>
                        <div className="video-tags">
                          {index % 3 === 0 && <Tag color="blue">Popularne</Tag>}
                          {index % 4 === 0 && <Tag color="green">HD</Tag>}
                          {index % 5 === 0 && <Tag color="orange">Nowe</Tag>}
                        </div>
                      </Space>
                    }
                  />
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </div>

      {filteredVideos.length > 0 && (
        <div className="load-more-section">
          <Button type="default" size="large" icon={<ThunderboltOutlined />} className="load-more-button">
            Załaduj więcej rekomendacji
          </Button>
        </div>
      )}

      <BackTop>
        <div className="back-to-top"><VerticalAlignTopOutlined /></div>
      </BackTop>
    </div>
  );
};

export default Recommendation;
