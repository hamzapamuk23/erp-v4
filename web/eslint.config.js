import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'

export default defineConfigWithVueTs(
  {
    ignores: [
      '**/dist/**',
      '**/coverage/**',
      'e2e/playwright-report/**',
      'e2e/test-results/**',
      'e2e/reports/**',
    ],
  },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  {
    rules: {
      // doc §10.1: v-html is banned (XSS)
      'vue/no-v-html': 'error',
      'vue/block-lang': ['error', { script: { lang: 'ts' } }],
      'vue/component-api-style': ['error', ['script-setup']],
    },
  },
)
