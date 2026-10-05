import { createErpVuetify } from '@erp/ui'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import ErpShell from './ErpShell.vue'
import { shellMessages } from './i18n'

function mountShell() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const i18n = createI18n({
    legacy: false,
    locale: 'tr',
    fallbackLocale: 'en',
    messages: shellMessages,
  })
  return mount(ErpShell, {
    slots: { default: '<p data-testid="page">içerik</p>' },
    global: { plugins: [createErpVuetify(), [VueQueryPlugin, { queryClient }], i18n] },
  })
}

function stubFetch(body: string, status: number, contentType: string) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(body, { status, headers: { 'content-type': contentType } })),
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ErpShell', () => {
  it('shows the backend version once loaded', async () => {
    stubFetch('{"version":"1.2.3","deploymentMode":"onprem"}', 200, 'application/json')
    const wrapper = mountShell()
    await vi.waitFor(async () => {
      await flushPromises()
      expect(wrapper.get('[data-testid="system-version"]').text()).toBe('1.2.3')
    })
    expect(wrapper.text()).toContain('Sürüm')
    expect(wrapper.find('[data-testid="backend-unavailable"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="page"]').text()).toBe('içerik')
  })

  it('warns instead of rendering a blank page when the backend is unreachable', async () => {
    stubFetch('<html>Bad Gateway</html>', 502, 'text/html')
    const wrapper = mountShell()
    await vi.waitFor(async () => {
      await flushPromises()
      expect(wrapper.find('[data-testid="backend-unavailable"]').exists()).toBe(true)
    })
    expect(wrapper.find('[data-testid="system-version"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="page"]').exists()).toBe(true)
  })
})
