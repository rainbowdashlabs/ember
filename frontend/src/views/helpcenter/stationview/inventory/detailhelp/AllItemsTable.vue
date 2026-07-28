/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import AllItemsRow from '@/views/helpcenter/stationview/inventory/detailhelp/AllItemsRow.vue'

const {t} = useI18n()

const rows = [
  {name: 'Helm', inventoryId: 'INV-0001', size: 'M', assignee: 'Max Mustermann'},
  {name: 'Helm', inventoryId: 'INV-0002', size: 'L'},
  {name: 'Helm', inventoryId: 'INV-0003', size: 'S', assignee: 'Erika Musterfrau', faded: true, lostNote: `${t('inventory.edit.lost')} (01.05.2026)`},
]
</script>

<template>
  <DataTable>
    <template #head>
      <Th>{{ t('inventory.edit.colName') }}</Th>
      <Th>{{ t('inventory.edit.colId') }}</Th>
      <Th>{{ t('inventory.edit.colSize') }}</Th>
      <Th>{{ t('inventory.edit.colAssigned') }}</Th>
      <th class="px-3 py-2"></th>
    </template>
    <AllItemsRow
      v-for="r in rows"
      :key="r.inventoryId"
      :name="r.name"
      :inventory-id="r.inventoryId"
      :size="r.size"
      :assignee="r.assignee"
      :faded="r.faded"
      :lost-note="r.lostNote"
    />
  </DataTable>
</template>
