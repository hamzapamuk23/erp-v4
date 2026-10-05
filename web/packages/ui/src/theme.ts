import type { ThemeDefinition } from 'vuetify'

// Design tokens (doc §9.4). Tenant branding overrides `primary` at runtime in a later phase.
export const erpLight: ThemeDefinition = {
  dark: false,
  colors: {
    primary: '#1F5FAD',
    secondary: '#4A5568',
    error: '#B3261E',
    warning: '#B26A00',
    success: '#2E7D32',
    info: '#0B6BCB',
    background: '#F7F8FA',
    surface: '#FFFFFF',
  },
}

export const erpDark: ThemeDefinition = {
  dark: true,
  colors: {
    primary: '#8AB4F8',
    secondary: '#A0AEC0',
    error: '#F2B8B5',
    warning: '#FFB74D',
    success: '#81C784',
    info: '#64B5F6',
    background: '#121417',
    surface: '#1B1E22',
  },
}
