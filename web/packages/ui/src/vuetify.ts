import { createVuetify } from 'vuetify'
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg'
import { en, tr } from 'vuetify/locale'
import { erpDark, erpLight } from './theme'

export type ErpLocale = 'tr' | 'en'

export interface ErpVuetifyOptions {
  locale?: ErpLocale
  dark?: boolean
}

/** The only place Vuetify is configured; apps and modules never call createVuetify themselves. */
export function createErpVuetify(options: ErpVuetifyOptions = {}) {
  return createVuetify({
    theme: {
      defaultTheme: options.dark ? 'erpDark' : 'erpLight',
      themes: { erpLight, erpDark },
    },
    icons: { defaultSet: 'mdi', aliases, sets: { mdi } },
    locale: { locale: options.locale ?? 'tr', fallback: 'en', messages: { tr, en } },
  })
}
