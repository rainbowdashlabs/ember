/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import type { InventoryItem, MemberIdentity } from '@/api/types'
import type { InventoryItemActionEmits } from '../itemEmits'
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
}>()

function locationLabel(containerId: number | null | undefined): string {
  if (!containerId) return ''
  return props.containerPathById?.get(containerId) ?? ''
}

const emit = defineEmits<InventoryItemActionEmits>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <THead>
          <Th>{{ t('inventory.edit.colName') }}</Th>
          <Th>{{ t('inventory.edit.colId') }}</Th>
          <Th v-if="hasSizes">{{ t('inventory.edit.colSize') }}</Th>
          <Th v-if="isMixed">{{ t('inventory.edit.colSource') }}</Th>
          <Th>{{ t('inventory.edit.colAssigned') }}</Th>
          <th v-if="showActions || showHistory" class="px-3 py-2"></th>
        </THead>
      </thead>
      <tbody>
        <ItemsTableRow v-for="item in items" :key="item.id"
                       :item="item" :has-sizes="hasSizes" :is-mixed="isMixed"
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
  </NeutralContainer>
</template>
