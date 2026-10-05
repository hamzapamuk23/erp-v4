<script setup lang="ts">
import { useSystemInfo } from '@erp/core'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { data: systemInfo, isError } = useSystemInfo()
</script>

<template>
  <v-app>
    <v-app-bar color="primary" density="compact">
      <v-app-bar-title data-testid="shell-title">{{ t('shell.title') }}</v-app-bar-title>
      <template #append>
        <span v-if="systemInfo" class="text-body-2 me-4">
          {{ t('shell.version') }}:
          <span data-testid="system-version">{{ systemInfo.version }}</span>
        </span>
      </template>
    </v-app-bar>
    <v-main>
      <v-alert
        v-if="isError"
        type="error"
        variant="tonal"
        class="ma-4"
        data-testid="backend-unavailable"
      >
        {{ t('shell.backendUnavailable') }}
      </v-alert>
      <slot />
    </v-main>
  </v-app>
</template>
