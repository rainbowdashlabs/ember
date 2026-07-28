/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import DummyAvailableRow from '@/views/helpcenter/stationview/inventory/lendingbrowsehelp/DummyAvailableRow.vue'

const {t} = useI18n()

const rows = [
  {station: 'DLRG Musterstadt', item: 'Rettungsring', quantity: 5, available: 'available' as const},
  {station: 'DLRG Musterstadt', item: 'Taucheranzug', quantity: 2, available: 'partiallyAvailable' as const},
  {station: 'DLRG Beispielburg', item: 'Erste-Hilfe-Koffer', quantity: 3, available: 'available' as const},
]
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('helpCenter.inventoryLendingBrowse.dummyTitle') }}</SectionHeader>
    <DataTable>
      <template #head>
        <Th>{{ t('helpCenter.inventoryLendingBrowse.colStation') }}</Th>
        <Th>{{ t('helpCenter.inventoryLendingBrowse.colItem') }}</Th>
        <Th>{{ t('helpCenter.inventoryLendingBrowse.colQuantity') }}</Th>
        <Th>{{ t('helpCenter.inventoryLendingBrowse.colAvailable') }}</Th>
        <th class="px-3 py-2"></th>
      </template>
      <DummyAvailableRow
        v-for="(r, i) in rows"
        :key="i"
        :station="r.station"
        :item="r.item"
        :quantity="r.quantity"
        :available="r.available"
      />
    </DataTable>
  </NeutralContainer>
</template>
