import { shellMessages } from '@erp/shell'
import { createI18n } from 'vue-i18n'

// Module message bundles are merged here as modules arrive (doc §9.8).
export const i18n = createI18n({
  legacy: false,
  locale: 'tr',
  fallbackLocale: 'en',
  messages: shellMessages,
})
