import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import StoragePanel from './StoragePanel.vue';
import TrendPanel from './TrendPanel.vue';
import type { DashboardStorage, DashboardTrend } from '../model';

vi.mock('echarts/core', () => ({
  init: () => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }),
  use: vi.fn()
}));

const trend: DashboardTrend = {
  range: '7d',
  points: [
    { date: '08-26', plays: 100, interactions: 20 },
    { date: '08-27', plays: 120, interactions: 26 }
  ]
};

const storage: DashboardStorage = {
  mount: '/media',
  usedPercent: 72,
  backupStatus: 'healthy',
  lastBackupAt: '2026-08-27T02:12:00.000Z'
};

describe('dashboard components', () => {
  it('emits the selected trend range', async () => {
    const wrapper = mount(TrendPanel, { props: { trend } });

    await wrapper.get('[data-range="30d"]').trigger('click');

    expect(wrapper.emitted('update:range')).toEqual([['30d']]);
  });

  it('shows upload protection at ninety percent', () => {
    const wrapper = mount(StoragePanel, {
      props: { storage: { ...storage, usedPercent: 91 } },
      global: { stubs: { ElIcon: { template: '<span><slot /></span>' } } }
    });

    expect(wrapper.text()).toContain('已暂停新上传');
  });
});
