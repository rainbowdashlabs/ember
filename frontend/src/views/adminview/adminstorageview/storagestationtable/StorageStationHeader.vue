/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import SortableHeader from '@/components/table/SortableHeader.vue'
import type {SortDirection} from '@/composables/useSortable'

export type StorageSortKey = 'name' | 'usage' | 'percent'

defineProps<{
  sortKey: StorageSortKey
  direction: SortDirection
}>()

const emit = defineEmits<{
  sort: [key: StorageSortKey]
}>()

const {t} = useI18n()
</script>

<template>
  <thead>
  <THead>
    <SortableHeader
        :label="t('storageMonitoring.stationName')"
        sort-key="name"
        :active-key="sortKey"
        :direction="direction"
        @sort="emit('sort', 'name')"
    />
    <SortableHeader
        :label="t('storageMonitoring.usage')"
        sort-key="usage"
        :active-key="sortKey"
        :direction="direction"
        class="min-w-50"
        @sort="emit('sort', 'usage')"
    />
    <SortableHeader
        :label="t('storageMonitoring.quota')"
        sort-key="percent"
        :active-key="sortKey"
        :direction="direction"
        align="right"
        @sort="emit('sort', 'percent')"
    />
    <Th align="center">{{ t('storageMonitoring.status') }}</Th>
    <Th>{{ t('storageMonitoring.preset') }}</Th>
    <Th align="right">{{ t('storageMonitoring.actions') }}</Th>
  </THead>
  </thead>
</template>
