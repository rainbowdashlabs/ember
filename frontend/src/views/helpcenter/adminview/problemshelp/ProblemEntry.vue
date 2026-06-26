/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import BaseBadge from '@/components/badge/BaseBadge.vue'

defineProps<{
  level: 'ERROR' | 'WARN'
  source: string
  message: string
  timestamp: string
  expanded?: boolean
  count?: number
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-start justify-between gap-3">
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2 mb-1">
        <span v-if="level === 'ERROR'" class="text-xs font-mono px-1.5 py-0.5 rounded bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400">ERROR</span>
        <span v-else class="text-xs font-mono px-1.5 py-0.5 rounded bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400">WARN</span>
        <span class="text-xs font-mono text-(--text-muted)">{{ source }}</span>
        <BaseBadge v-if="count" bg-class="bg-[var(--bg-accent)]">{{ count }}x</BaseBadge>
      </div>
      <p class="text-sm font-medium truncate">{{ message }}</p>
      <p class="text-xs text-(--text-muted)">{{ timestamp }}</p>
    </div>
    <div class="flex items-center gap-1 shrink-0">
      <IconButton :icon="['fas', 'check']" :label="t('adminProblems.acknowledge')"/>
      <font-awesome-icon :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']" class="text-xs text-(--text-muted)"/>
    </div>
  </div>
</template>
