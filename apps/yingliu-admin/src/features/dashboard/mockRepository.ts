import type {
  DashboardRange,
  DashboardRepository,
  DashboardSnapshot,
  TrendPoint
} from './model';

const trend7d: TrendPoint[] = [
  { date: '08-21', plays: 84200, interactions: 10840 },
  { date: '08-22', plays: 91600, interactions: 12120 },
  { date: '08-23', plays: 88900, interactions: 11940 },
  { date: '08-24', plays: 103400, interactions: 13780 },
  { date: '08-25', plays: 111800, interactions: 14920 },
  { date: '08-26', plays: 113900, interactions: 15480 },
  { date: '08-27', plays: 128460, interactions: 17860 }
];

const trend30d: TrendPoint[] = [
  { date: '07-29', plays: 62100, interactions: 7840 },
  { date: '08-01', plays: 68400, interactions: 8560 },
  { date: '08-04', plays: 73100, interactions: 9180 },
  { date: '08-07', plays: 77900, interactions: 9820 },
  { date: '08-10', plays: 82600, interactions: 10640 },
  { date: '08-13', plays: 91800, interactions: 12120 },
  { date: '08-16', plays: 97400, interactions: 12880 },
  { date: '08-19', plays: 104200, interactions: 13940 },
  { date: '08-22', plays: 91600, interactions: 12120 },
  { date: '08-25', plays: 111800, interactions: 14920 },
  { date: '08-27', plays: 128460, interactions: 17860 }
];

function buildSnapshot(range: DashboardRange): DashboardSnapshot {
  return {
    updatedAt: '2026-08-27T06:32:00.000Z',
    metrics: [
      { key: 'plays', label: '今日播放', value: '128,460', changeLabel: '较昨日 +12.8%', tone: 'positive' },
      { key: 'newUsers', label: '今日新增用户', value: '1,284', changeLabel: '较昨日 +6.2%', tone: 'positive' },
      { key: 'pendingReviews', label: '待审核视频', value: '18', changeLabel: '其中 4 项高风险', tone: 'warning' },
      { key: 'playFailureRate', label: '播放失败率', value: '0.7%', changeLabel: '低于 1% 目标线', tone: 'neutral' }
    ],
    trend: { range, points: range === '7d' ? trend7d : trend30d },
    todos: [
      { key: 'videoReview', label: '视频待审核', count: 18, tone: 'warning' },
      { key: 'report', label: '举报待处置', count: 12, tone: 'warning' },
      { key: 'transcodeFailure', label: '转码失败任务', count: 3, tone: 'danger' },
      { key: 'storageWarning', label: '存储容量预警', count: 0, tone: 'neutral' }
    ],
    storage: {
      mount: '/media',
      usedPercent: 72,
      backupStatus: 'healthy',
      lastBackupAt: '2026-08-27T02:12:00.000Z'
    },
    recentVideos: [
      {
        id: '90001',
        title: '客户现场安全操作要点',
        author: '安全培训组',
        status: 'published',
        playCount: 24860,
        publishedAt: '2026-08-27T01:00:00.000Z'
      },
      {
        id: '90002',
        title: '新员工入职培训',
        author: '人力资源中心',
        status: 'pendingReview',
        playCount: null,
        publishedAt: null
      },
      {
        id: '90003',
        title: '设备巡检标准流程',
        author: '设备管理部',
        status: 'transcoding',
        playCount: null,
        publishedAt: null
      },
      {
        id: '90004',
        title: '企业文化开放日纪实',
        author: '品牌中心',
        status: 'offShelf',
        playCount: 18240,
        publishedAt: '2026-08-25T03:30:00.000Z'
      }
    ]
  };
}

export const mockDashboardRepository: DashboardRepository = {
  async getSnapshot(range) {
    return structuredClone(buildSnapshot(range));
  }
};

