/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
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
  <NeutralContainer
      class="h-full flex flex-col cursor-pointer hover:border-[var(--accent)] transition-colors"
      @click="$emit('open')"
  >
    <div class="flex items-start gap-2 mb-2">
      <font-awesome-icon :icon="['fas', 'list-check']" class="mt-1 text-(--text-muted) shrink-0"/>
      <SubHeader class="flex-1 min-w-0 truncate">{{ item.name }}</SubHeader>
    </div>
    <p v-if="item.description" class="text-sm text-(--text-muted) line-clamp-2 mb-3">
      {{ item.description }}
    </p>
    <div class="mt-auto flex flex-wrap gap-2 items-center">
      <SecondaryBadge>{{ t('checklist.memberCount', {count: item.memberCount}) }}</SecondaryBadge>
      <InfoBadge>{{ t('checklist.columnCount', {count: item.columnCount}) }}</InfoBadge>
    </div>
    <div class="text-xs text-(--text-muted) mt-2">{{ lastRefreshLabel }}</div>
  </NeutralContainer>
</template>
