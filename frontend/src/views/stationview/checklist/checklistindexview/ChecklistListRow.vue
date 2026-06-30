/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryContainer from '@/components/container/SecondaryContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {ChecklistSummary} from '@/api/types'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()

const props = defineProps<{
  item: ChecklistSummary
}>()

defineEmits<{
  (e: 'open'): void
}>()

const lastRefreshLabel = computed(() =>
    props.item.lastRefreshedAt
        ? t('checklist.lastRefreshed', {when: formatDateTime(props.item.lastRefreshedAt)})
        : t('checklist.neverRefreshed'),
)
</script>

<template>
  <SecondaryContainer
      class="flex flex-wrap items-center justify-between gap-3 cursor-pointer hover:bg-(--bg-light-accent) dark:hover:bg-(--bg-dark-accent)"
      @click="$emit('open')"
  >
    <div class="flex-1 min-w-0">
      <div class="font-semibold text-base truncate">{{ item.name }}</div>
      <div v-if="item.description" class="text-sm text-(--text-muted) truncate">{{ item.description }}</div>
      <div class="text-xs text-(--text-muted) mt-1">{{ lastRefreshLabel }}</div>
    </div>
    <div class="flex flex-wrap gap-2 items-center">
      <SecondaryBadge>{{ t('checklist.memberCount', {count: item.memberCount}) }}</SecondaryBadge>
      <InfoBadge>{{ t('checklist.columnCount', {count: item.columnCount}) }}</InfoBadge>
    </div>
  </SecondaryContainer>
</template>
