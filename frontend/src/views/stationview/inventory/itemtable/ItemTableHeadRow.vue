/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import HeaderFilterCell from '@/components/table/HeaderFilterCell.vue'
import type { ItemTableApi } from './useItemTable'

defineProps<{
  table: ItemTableApi
  showActionColumn?: boolean
}>()
</script>

<template>
  <THead>
    <Th v-for="col in table.visibleColumns" :key="col.key">
      <HeaderFilterCell
          :label="col.label"
          :sort-icon="table.sortIcon(col.key)"
          :has-filter="table.hasActiveFilter(col.key)"
          show-sort
          show-filter
          @sort="table.toggleSort(col.key)"
          @filter="table.openFilterModal(col.key, col.label)"
      />
    </Th>
    <th v-if="showActionColumn" class="px-3 py-2"></th>
  </THead>
</template>
