/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {ItemCheckHistoryEntry} from '@/api/inventoryContainers'
import {formatDateTime} from '@/util/format'

defineProps<{
  entries: ItemCheckHistoryEntry[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <SubHeader>{{ t('itemDetail.checkHistory.title') }}</SubHeader>
    <EmptyState v-if="entries.length === 0" :message="t('itemDetail.checkHistory.empty')"/>
    <ul v-else class="divide-y divide-(--bg-accent) mt-2">
      <li v-for="entry in entries" :key="entry.checkId" class="py-2 flex items-start gap-3 flex-wrap">
        <div class="flex-1 min-w-48">
          <div class="text-sm font-medium">{{ formatDateTime(entry.checkedAt) }}</div>
          <div class="text-xs text-(--text-muted)">
            {{ t('itemDetail.checkHistory.byChecker', {name: entry.checkerName || t('common.unknown')}) }}
          </div>
          <div v-if="entry.reporterName" class="text-xs text-(--text-muted)" data-testid="check-history-reporter">
            {{ t('itemDetail.checkHistory.reportedBy', {name: entry.reporterName}) }}
          </div>
          <div v-if="entry.containerName" class="text-xs text-(--text-muted)">
            {{ t('itemDetail.checkHistory.inContainer', {container: entry.containerName}) }}
          </div>
          <div v-if="entry.note" class="text-xs italic text-(--text-muted) mt-1">"{{ entry.note }}"</div>
        </div>
        <SuccessBadge v-if="entry.result === 'CONFIRMED'">{{ t('itemDetail.checkHistory.result.CONFIRMED') }}</SuccessBadge>
        <ErrorBadge v-else-if="entry.result === 'LOST'">{{ t('itemDetail.checkHistory.result.LOST') }}</ErrorBadge>
        <ErrorBadge v-else-if="entry.result === 'NOT_IN_POSSESSION'">{{ t('itemDetail.checkHistory.result.NOT_IN_POSSESSION') }}</ErrorBadge>
        <InfoBadge v-else>{{ entry.result }}</InfoBadge>
      </li>
    </ul>
  </NeutralContainer>
</template>
