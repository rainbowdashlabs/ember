/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import ItemModals from './ItemModals.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ItemActions from '../itemstable/ItemActions.vue'
import ItemTableHeadRow from '../itemtable/ItemTableHeadRow.vue'
import ItemTableFilterModal from '../itemtable/ItemTableFilterModal.vue'
import ItemListControls from '../itemtable/ItemListControls.vue'
import {useItemTable} from '../itemtable/useItemTable'
import type {InventoryDetail, InventoryItem, StationMember} from '@/api/types'
import {InventoryTypes, ItemSource} from '@/api/types'
import {inventory} from '@/api'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {formatDate} from '@/util/format'

const {isMobile} = useBreakpoint()
const {t} = useI18n()

const props = defineProps<{
  detail: InventoryDetail
  items: InventoryItem[]
  members: StationMember[]
}>()

const emit = defineEmits<{
  itemsChanged: []
  error: [message: string]
}>()

const modals = ref<InstanceType<typeof ItemModals> | null>(null)

const table = useItemTable({
  inventoryId: () => props.detail.id,
  items: () => props.items,
  hasSizes: () => props.detail.hasSizes,
  isMixed: () => props.detail.inventoryType === InventoryTypes.MIXED,
  sizeLabel: item => getSizeLabel(item.sizeId),
  assignedName: item => getMemberName(item.assignedTo),
})

function getMemberName(memberId: number | null | undefined): string {
  if (!memberId) return ''
  const m = props.members.find(mem => mem.id === memberId)
  return m ? (m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`) : `#${memberId}`
}

function getMemberIdentity(memberId: number | null | undefined) {
  if (!memberId) return null
  return props.members.find(mem => mem.id === memberId)?.identity ?? null
}

function getSizeLabel(sizeId: number | null | undefined): string {
  if (!sizeId || !props.detail.sizes) return ''
  return props.detail.sizes.find(s => s.id === sizeId)?.label ?? ''
}

async function unassignItem(item: InventoryItem) {
  try {
    if (props.detail.inventoryType === InventoryTypes.EXTERNAL) {
      await inventory.deleteItem(item.id)
    } else {
      await inventory.assignItem(item.id, {memberId: null, memberName: ''})
    }
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

async function doMarkLost(item: InventoryItem) {
  try {
    await inventory.markLost(item.id)
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

async function doMarkFound(item: InventoryItem) {
  try {
    await inventory.markFound(item.id)
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <ItemListControls
        :table="table"
        :count="items.length"
        :show-quick-assign="detail.inventoryType === InventoryTypes.EXTERNAL || detail.inventoryType === InventoryTypes.MIXED"
        :show-add="detail.inventoryType !== InventoryTypes.EXTERNAL"
        :show-search="items.length > 0"
        @quick-assign="modals?.openQuickAssign()"
        @add="modals?.openAdd()"
    >
      <p v-if="detail.inventoryType === InventoryTypes.EXTERNAL" class="text-xs text-(--text-muted)">
        {{ t('inventory.edit.externalItemsHint') }}
      </p>
    </ItemListControls>

    <div v-if="items.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
      {{ t('inventory.edit.noItems') }}
    </div>

    <div v-if="table.filteredItems.length > 0 && isMobile" class="space-y-2">
      <NeutralContainer v-for="item in table.filteredItems" :key="item.id" :class="item.lostAt ? 'opacity-60' : ''">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <div class="font-medium text-sm">{{ item.name }}</div>
            <span v-if="item.lostAt" class="text-xs text-error">{{ t('inventory.edit.lost') }} ({{ formatDate(item.lostAt) }})</span>
          </div>
          <span v-if="item.internalId" class="text-xs text-(--text-muted) shrink-0">{{ item.internalId }}</span>
        </div>
        <div class="grid grid-cols-2 gap-x-4 gap-y-1 mt-2 text-xs">
          <div v-if="detail.hasSizes && getSizeLabel(item.sizeId)">
            <div class="text-(--text-muted)">{{ t('inventory.edit.colSize') }}</div>
            <div>{{ getSizeLabel(item.sizeId) }}</div>
          </div>
          <div v-if="detail.inventoryType === InventoryTypes.MIXED">
            <div class="text-(--text-muted)">{{ t('inventory.edit.colSource') }}</div>
            <div>
              <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
              <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
              <span v-else>&#x2013;</span>
            </div>
          </div>
          <div>
            <div class="text-(--text-muted)">{{ t('inventory.edit.colAssigned') }}</div>
            <div v-if="item.assignedTo" class="font-medium"><MemberName :identity="getMemberIdentity(item.assignedTo)"/></div>
            <div v-else class="text-(--text-muted)">&#x2013;</div>
          </div>
        </div>
        <div class="flex items-center justify-end gap-1 mt-2 pt-2 border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <ItemActions :item="item" show-actions
                       @assign="modals?.openAssign($event)"
                       @unassign="unassignItem($event)"
                       @edit="modals?.openEdit($event)"
                       @mark-lost="doMarkLost($event)"
                       @mark-found="doMarkFound($event)"
                       @history="modals?.openHistory($event)"
                       @delete="modals?.requestDelete($event)"/>
        </div>
      </NeutralContainer>
    </div>

    <div v-if="items.length > 0 && !isMobile" class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <ItemTableHeadRow :table="table" show-action-column/>
        </thead>
        <tbody>
          <TRow v-for="item in table.filteredItems" :key="item.id" :class="item.lostAt ? 'opacity-60' : ''">
            <Td class="font-medium">
              {{ item.name }}
              <span v-if="item.lostAt" class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} ({{ formatDate(item.lostAt) }})</span>
            </Td>
            <Td muted>{{ item.internalId || '–' }}</Td>
            <Td v-if="table.isColumnVisible('size')" muted>{{ getSizeLabel(item.sizeId) || '–' }}</Td>
            <Td v-if="table.isColumnVisible('source')">
              <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
              <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
              <span v-else class="text-(--text-muted)">&#x2013;</span>
            </Td>
            <Td v-if="table.isColumnVisible('assigned')">
              <MemberName v-if="item.assignedTo" :identity="getMemberIdentity(item.assignedTo)"/>
              <span v-else class="text-(--text-muted)">&#x2013;</span>
            </Td>
            <Td v-for="col in table.visibleFieldColumns" :key="col.key" muted>{{ table.columnValue(item, col.key) || '–' }}</Td>
            <Td align="right">
              <div class="flex items-center justify-end gap-0.5">
                <ItemActions :item="item" show-actions
                             @assign="modals?.openAssign($event)"
                             @unassign="unassignItem($event)"
                             @edit="modals?.openEdit($event)"
                             @mark-lost="doMarkLost($event)"
                             @mark-found="doMarkFound($event)"
                             @history="modals?.openHistory($event)"
                             @delete="modals?.requestDelete($event)"/>
              </div>
            </Td>
          </TRow>
        </tbody>
      </table>
    </div>
  </NeutralContainer>

  <ItemTableFilterModal :table="table"/>

  <ItemModals ref="modals" :detail="detail" :members="members" @items-changed="emit('itemsChanged')" @error="emit('error', $event)"/>
</template>
