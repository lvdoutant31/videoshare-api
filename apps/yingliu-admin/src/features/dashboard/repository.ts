import type {
  DashboardMetric,
  DashboardRange,
  DashboardRepository,
  DashboardSnapshot,
  DashboardStorage,
  DashboardTodo,
  RecentVideo,
  TrendPoint
} from './model';

interface ApiEnvelope<T> {
  code: string;
  message?: string;
  data: T;
  traceId?: string;
}

interface DashboardSummaryData {
  updatedAt: string;
  metrics: DashboardMetric[];
  recentVideos?: RecentVideo[];
}

interface DashboardTrendData {
  points: TrendPoint[];
}

interface DashboardTodosData {
  items: DashboardTodo[];
}

interface DashboardStorageData extends DashboardStorage {}

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export class DashboardLoadError extends Error {
  readonly traceId?: string;
  readonly code?: string;

  constructor(message: string, options: { traceId?: string; code?: string } = {}) {
    super(message);
    this.name = 'DashboardLoadError';
    this.traceId = options.traceId;
    this.code = options.code;
  }
}

async function requestData<T>(fetcher: Fetcher, url: string): Promise<T> {
  const response = await fetcher(url, { headers: { Accept: 'application/json' } });
  const envelope = await response.json() as ApiEnvelope<T>;

  if (!response.ok || envelope.code !== 'OK') {
    throw new DashboardLoadError(envelope.message || '工作台数据加载失败', {
      traceId: envelope.traceId,
      code: envelope.code
    });
  }

  return envelope.data;
}

export function createHttpDashboardRepository(
  baseUrl = '',
  fetcher: Fetcher = globalThis.fetch.bind(globalThis)
): DashboardRepository {
  const endpoint = (path: string) => `${baseUrl}/api/v1/admin/dashboard/${path}`;

  return {
    async getSnapshot(range: DashboardRange): Promise<DashboardSnapshot> {
      const [summary, trend, todos, storage] = await Promise.all([
        requestData<DashboardSummaryData>(fetcher, endpoint('summary')),
        requestData<DashboardTrendData>(fetcher, endpoint(`trends?range=${range}`)),
        requestData<DashboardTodosData>(fetcher, endpoint('todos')),
        requestData<DashboardStorageData>(fetcher, endpoint('storage'))
      ]);

      return {
        updatedAt: summary.updatedAt,
        metrics: summary.metrics,
        trend: { range, points: trend.points },
        todos: todos.items,
        storage,
        recentVideos: summary.recentVideos ?? []
      };
    }
  };
}

export const dashboardRepositoryKey = Symbol('dashboard-repository');

