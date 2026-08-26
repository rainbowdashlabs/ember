/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StatValue from '@/components/typography/StatValue.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import type { InventorySize } from '@/api/inventory'

interface SizeStat {
  size: InventorySize | null
  total: number
  assigned: number
  free: number
  lost: number
}

const props = defineProps<{
  totalCount: number
  freeCount: number
  assignedCount: number
  lostCount: number
  lentOutCount: number
  hasSizes: boolean
  sizeStats: SizeStat[]
}>()

const { t } = useI18n()
</script>

<template>
  <div :class="props.lentOutCount > 0 ? 'grid-cols-2 sm:grid-cols-5' : 'grid-cols-2 sm:grid-cols-4'" class="grid gap-3">
    <NeutralContainer class="text-center">
      <div class="text-2xl font-bold">{{ props.totalCount }}</div>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.total') }}</div>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue color="success">{{ props.freeCount }}</StatValue>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.free') }}</div>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue>{{ props.assignedCount }}</StatValue>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.assigned') }}</div>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue color="error">{{ props.lostCount }}</StatValue>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.lost') }}</div>
    </NeutralContainer>
    <NeutralContainer v-if="props.lentOutCount > 0" class="text-center">
      <div class="text-2xl font-bold text-secondary-accent">{{ props.lentOutCount }}</div>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.lentOut') }}</div>
    </NeutralContainer>
  </div>

  <!-- Size distribution -->
  <template v-if="props.hasSizes && props.sizeStats.length > 0">
    <SubHeader>{{ t('inventory.detail.bySize') }}</SubHeader>
    <DataTable>
      <template #head>
        <Th>{{ t('inventory.detail.size') }}</Th>
        <Th align="center">{{ t('inventory.detail.total') }}</Th>
        <Th align="center">{{ t('inventory.detail.free') }}</Th>
        <Th align="center">{{ t('inventory.detail.assigned') }}</Th>
        <Th align="center">{{ t('inventory.detail.lost') }}</Th>
      </template>
      <TRow v-for="row in props.sizeStats" :key="row.size?.id ?? 'none'" data-testid="stats-size-row">
        <Td class="font-medium">{{ row.size?.label ?? t('inventory.detail.noSize') }}</Td>
        <Td align="center">{{ row.total }}</Td>
        <Td align="center" class="text-success">{{ row.free }}</Td>
        <Td align="center" class="text-primary">{{ row.assigned }}</Td>
        <Td align="center" class="text-error">{{ row.lost }}</Td>
      </TRow>
    </DataTable>
  </template>
</template>
