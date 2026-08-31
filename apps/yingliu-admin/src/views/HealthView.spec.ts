import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import HealthView from './HealthView.vue';

vi.mock('@yingliu/shared', () => ({
  getHealth: vi.fn().mockResolvedValue({ data: { service: 'admin-api', status: 'UP' } })
}));

describe('HealthView', () => {
  it('shows the service after a successful health check', async () => {
    const wrapper = mount(HealthView);
    await wrapper.get('button').trigger('click');
    await Promise.resolve();

    expect(wrapper.text()).toContain('admin-api 已就绪');
  });
});
