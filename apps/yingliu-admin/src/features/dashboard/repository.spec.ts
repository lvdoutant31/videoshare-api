import { describe, expect, it } from 'vitest';
import { mockDashboardRepository } from './mockRepository';
import { DashboardLoadError, createHttpDashboardRepository } from './repository';

describe('dashboard repository', () => {
  it('loads the PRD dashboard snapshot from the mock repository', async () => {
    const snapshot = await mockDashboardRepository.getSnapshot('7d');

    expect(snapshot.metrics.find((metric) => metric.key === 'plays')?.value).toBe('128,460');
    expect(snapshot.storage.usedPercent).toBe(72);
    expect(snapshot.trend.range).toBe('7d');
  });

  it('preserves the trace id when an HTTP module fails', async () => {
    const fetcher = async () => new Response(JSON.stringify({
      code: 'DASHBOARD_UNAVAILABLE',
      message: 'unavailable',
      traceId: 'trace-dashboard-001'
    }), { status: 503, headers: { 'Content-Type': 'application/json' } });
    const repository = createHttpDashboardRepository('', fetcher);

    await expect(repository.getSnapshot('30d')).rejects.toEqual(
      expect.objectContaining<Partial<DashboardLoadError>>({
        name: 'DashboardLoadError',
        traceId: 'trace-dashboard-001'
      })
    );
  });
});
