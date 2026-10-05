<script setup lang="ts">
import { ErpShell } from '@erp/shell'
</script>

<template>
  <ErpShell>
    <v-container>
      <h1 class="text-h5">{{ $t('shell.welcome') }}</h1>
    </v-container>
  </ErpShell>
</template>
