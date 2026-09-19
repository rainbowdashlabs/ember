/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import WaitingListAnswerBadge from '@/components/badge/WaitingListAnswerBadge.vue'
import type { WaitingListEntryWithScore } from '@/api/waitingList'

/** Where somebody on the list stands: their status, their answer to an invitation, and whether they are still too young. */
defineProps<{
  item: WaitingListEntryWithScore
}>()

const { t } = useI18n()
</script>

<template>
  <span class="whitespace-nowrap">
    <WaitingListStatusBadge :status="item.entry.status" />
    <WaitingListAnswerBadge v-if="item.entry.answer" :answer="item.entry.answer.answer" class="ml-1" />
    <span v-if="item.belowJoinAge" :title="t('waitingList.belowJoinAgeHint')"
          class="ml-1 rounded bg-warning/15 px-1.5 py-0.5 text-xs font-medium text-warning">
      {{ t('waitingList.belowJoinAge') }}
    </span>
  </span>
</template>
