/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import StockShareBar from '@/components/inventory/StockShareBar.vue'

const {t} = useI18n()

const rows = [
  {size: 'S', total: 6, free: 2, assigned: 3, lost: 1},
  {size: 'M', total: 10, free: 3, assigned: 7, lost: 0},
  {size: 'L', total: 8, free: 3, assigned: 5, lost: 0},
]
</script>

<template>
  <DataTable>
    <template #head>
      <Th>{{ t('inventory.detail.size') }}</Th>
      <Th align="center">{{ t('inventory.detail.total') }}</Th>
      <Th align="center">{{ t('inventory.detail.free') }}</Th>
      <Th align="center">{{ t('inventory.detail.assigned') }}</Th>
      <Th align="center">{{ t('inventory.detail.lost') }}</Th>
    </template>
    <TRow v-for="row in rows" :key="row.size">
      <Td class="font-medium">
        {{ row.size }}
        <StockShareBar :assigned="row.assigned" :free="row.free" :total="row.total"/>
      </Td>
      <Td align="center">{{ row.total }}</Td>
      <Td align="center" class="text-success">{{ row.free }}</Td>
      <Td align="center" class="text-primary">{{ row.assigned }}</Td>
      <Td align="center" class="text-error">{{ row.lost }}</Td>
    </TRow>
  </DataTable>
</template>
