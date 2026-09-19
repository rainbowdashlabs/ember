/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ItemModals from './ItemModals.vue'
import InventoryItemTable from '../itemtable/InventoryItemTable.vue'
import ItemListControls from '../itemtable/ItemListControls.vue'
import {useItemTable} from '../itemtable/useItemTable'
import {InventoryTypes, type InventoryDetail, type InventoryItem} from '@/api/inventory'
import type {StationMember} from '@/api/types'
import {inventory} from '@/api'

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
  sizes: () => props.detail.sizes ?? [],
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
        :table="table.table"
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

    <InventoryItemTable
        v-if="items.length > 0"
        :items="table"
        :member-identity="getMemberIdentity"
        show-actions
        @assign="modals?.openAssign($event)"
        @delete="modals?.requestDelete($event)"
        @edit="modals?.openEdit($event)"
        @history="modals?.openHistory($event)"
        @mark-found="doMarkFound($event)"
        @mark-lost="doMarkLost($event)"
        @unassign="unassignItem($event)"
    />
  </NeutralContainer>

  <ItemModals ref="modals" :detail="detail" :members="members" @items-changed="emit('itemsChanged')" @error="emit('error', $event)"/>
</template>
