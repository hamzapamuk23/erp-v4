import { describe, expect, it } from 'vitest'
import { createErpVuetify } from './vuetify'

describe('createErpVuetify', () => {
  it('defaults to Turkish and the light ERP theme', () => {
    const vuetify = createErpVuetify()
    expect(vuetify.locale.current.value).toBe('tr')
    expect(vuetify.theme.global.name.value).toBe('erpLight')
  })

  it('honours English and dark mode', () => {
    const vuetify = createErpVuetify({ locale: 'en', dark: true })
    expect(vuetify.locale.current.value).toBe('en')
    expect(vuetify.theme.global.name.value).toBe('erpDark')
  })
})
