import '@erp/ui/styles'
import { createErpVuetify } from '@erp/ui'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createApp } from 'vue'
import App from './App.vue'
import { i18n } from './i18n'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

createApp(App).use(createErpVuetify()).use(VueQueryPlugin, { queryClient }).use(i18n).mount('#app')
