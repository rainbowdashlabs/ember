/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import type { InventoryItem } from '@/api/inventory'
import type { MemberIdentity } from '@/api/types'
import type { InventoryItemActionEmits } from '../itemEmits'
import type { ItemTableApi } from '../itemtable/useItemTable'
import ItemTableHeadRow from '../itemtable/ItemTableHeadRow.vue'
import ItemsTableRow from './ItemsTableRow.vue'

const props = defineProps<{
  items: InventoryItem[]
  hasSizes: boolean
  isMixed: boolean
  showActions: boolean
  showHistory: boolean
  lentItemMap?: Map<number, string>
  containerPathById?: Map<number, string>
  getSizeLabel: (sizeId: number | null | undefined) => string
  getMemberIdentity: (memberId: number | null | undefined) => MemberIdentity | undefined
  formatDate: (iso: string | null | undefined) => string
  tableApi?: ItemTableApi
}>()

const showSize = computed(() => props.tableApi ? props.tableApi.isColumnVisible('size') : props.hasSizes)
const showOwner = computed(() => props.tableApi ? props.tableApi.isColumnVisible('owner') : props.isMixed)
const showAssigned = computed(() => props.tableApi ? props.tableApi.isColumnVisible('assigned') : true)

function fieldValues(item: InventoryItem): string[] {
  const api = props.tableApi
  if (!api) return []
  return api.visibleFieldColumns.map(col => api.columnValue(item, col.key))
}

function locationLabel(containerId: number | null | undefined): string {
  if (!containerId) return ''
  return props.containerPathById?.get(containerId) ?? ''
}

const emit = defineEmits<InventoryItemActionEmits>()

const { t } = useI18n()
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <ItemTableHeadRow v-if="tableApi" :table="tableApi" :show-action-column="showActions || showHistory"/>
        <THead v-else>
          <Th>{{ t('inventory.edit.colName') }}</Th>
          <Th>{{ t('inventory.edit.colId') }}</Th>
          <Th v-if="hasSizes">{{ t('inventory.edit.colSize') }}</Th>
          <Th v-if="isMixed">{{ t('inventory.edit.colOwner') }}</Th>
          <Th>{{ t('inventory.edit.colAssigned') }}</Th>
          <th v-if="showActions || showHistory" class="px-3 py-2"></th>
        </THead>
      </thead>
      <tbody>
        <ItemsTableRow v-for="item in items" :key="item.id"
                       :item="item" :has-sizes="showSize" :is-mixed="showOwner"
                       :show-assigned="showAssigned" :field-values="fieldValues(item)"
                       :show-actions="showActions" :show-history="showHistory"
                       :lent-out="lentItemMap?.has(item.id) ?? false"
                       :lent-to-station-name="lentItemMap?.get(item.id) ?? null"
                       :size-label="getSizeLabel(item.sizeId)"
                       :member-identity="getMemberIdentity(item.assignedTo)"
                       :formatted-lost-at="formatDate(item.lostAt)"
                       :location-label="locationLabel(item.containerId)"
                       @assign="emit('assign', $event)"
                       @unassign="emit('unassign', $event)"
                       @edit="emit('edit', $event)"
                       @mark-lost="emit('markLost', $event)"
                       @mark-found="emit('markFound', $event)"
                       @history="emit('history', $event)"
                       @delete="emit('delete', $event)"/>
      </tbody>
    </table>
  </div>
</template>
