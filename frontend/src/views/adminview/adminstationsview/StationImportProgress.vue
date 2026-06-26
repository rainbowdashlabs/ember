/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {ImportProgress} from '@/api/transfer'

defineProps<{
  progress: ImportProgress
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">{{ progress.stationName }}</span>
      <span class="text-sm text-(--text-muted)">
        {{ progress.completedTables }} / {{ progress.totalTables }}
      </span>
    </div>
    <div class="w-full bg-bg-light-accent dark:bg-bg-dark-accent rounded-full h-2.5">
      <div
          class="h-2.5 rounded-full transition-all duration-300"
          :class="progress.status === 'FAILED' ? 'bg-error' : 'bg-primary'"
          :style="{ width: `${(progress.completedTables / progress.totalTables) * 100}%` }"
      />
    </div>
    <Alert v-if="progress.status === 'COMPLETED'" variant="success">
      {{ t('adminStations.importCompleted') }}
    </Alert>
    <Alert v-if="progress.status === 'FAILED'" variant="error">
      {{ t('adminStations.importFailed', { error: progress.error ?? '' }) }}
    </Alert>
    <p v-if="progress.status === 'IN_PROGRESS' && progress.currentTable"
       class="text-xs text-(--text-muted)">
      {{ t('adminStations.importProgress', {
        table: progress.currentTable,
        completed: progress.completedTables,
        total: progress.totalTables,
      }) }}
    </p>
  </NeutralContainer>
</template>
