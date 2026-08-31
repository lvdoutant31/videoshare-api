import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AdminShell from './AdminShell.vue';

describe('AdminShell', () => {
  it('collapses the sidebar while preserving the active dashboard item', async () => {
    const wrapper = mount(AdminShell, {
      slots: { default: '<div>内容</div>' },
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          ElIcon: { template: '<span><slot /></span>' }
        }
      }
    });

    await wrapper.get('[data-testid="sidebar-toggle"]').trigger('click');

    expect(wrapper.get('[data-testid="admin-shell"]').classes()).toContain('is-collapsed');
    expect(wrapper.get('[aria-current="page"]').text()).toContain('工作台');
  });
});
