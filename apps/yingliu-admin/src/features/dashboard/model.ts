export type DashboardRange = '7d' | '30d';

export type MetricKey = 'plays' | 'newUsers' | 'pendingReviews' | 'playFailureRate';
export type MetricTone = 'positive' | 'neutral' | 'warning' | 'danger';

export interface DashboardMetric {
  key: MetricKey;
  label: string;
  value: string;
  changeLabel: string;
  tone: MetricTone;
}

export interface TrendPoint {
  date: string;
  plays: number;
  interactions: number;
}

export interface DashboardTrend {
  range: DashboardRange;
  points: TrendPoint[];
}

export type TodoKey = 'videoReview' | 'report' | 'transcodeFailure' | 'storageWarning';

export interface DashboardTodo {
  key: TodoKey;
  label: string;
  count: number;
  tone: Exclude<MetricTone, 'positive'>;
}

export interface DashboardStorage {
  mount: '/media';
  usedPercent: number;
  backupStatus: 'healthy' | 'delayed' | 'failed';
  lastBackupAt: string;
}

export type VideoStatus = 'published' | 'pendingReview' | 'transcoding' | 'transcodeFailed' | 'offShelf';

export interface RecentVideo {
  id: string;
  title: string;
  author: string;
  status: VideoStatus;
  playCount: number | null;
  publishedAt: string | null;
}

export interface DashboardSnapshot {
  updatedAt: string;
  metrics: DashboardMetric[];
  trend: DashboardTrend;
  todos: DashboardTodo[];
  storage: DashboardStorage;
  recentVideos: RecentVideo[];
}

export interface DashboardRepository {
  getSnapshot(range: DashboardRange): Promise<DashboardSnapshot>;
}

