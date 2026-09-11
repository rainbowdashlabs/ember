/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {formatDateTime} from '@/util/format'

/**
 * The line a problem report opens with, whether it was written on this instance or forwarded here
 * by another one: who it comes from, when it was written and what it says.
 */
defineProps<{
  reporter: string
  reportedAt: string
  message: string
  acknowledged: boolean
  expanded: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-start justify-between gap-3 cursor-pointer">
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2 mb-1 flex-wrap">
        <span class="text-sm font-semibold">{{ reporter }}</span>
        <SuccessBadge v-if="acknowledged">{{ t('problemReport.acknowledged') }}</SuccessBadge>
        <slot name="badges"/>
        <MutedText size="xs">{{ formatDateTime(reportedAt) }}</MutedText>
      </div>
      <p class="text-sm break-words whitespace-pre-wrap">{{ message }}</p>
    </div>
    <div class="flex items-center gap-1 shrink-0">
      <slot name="actions"/>
      <font-awesome-icon
          :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
          class="h-3 w-3 text-(--text-muted) ml-1"
      />
    </div>
  </div>
</template>
