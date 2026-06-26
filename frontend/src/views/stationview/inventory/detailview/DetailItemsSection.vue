/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import ItemsTable from '../ItemsTable.vue'
import type { InventoryItem, InventorySize, StationMember } from '@/api/types'
import { InventoryTypes, type InventoryTypeName } from '@/api/types'
import type { LentOutItem } from '@/api/lending'
import type { InventoryItemActionEmits } from '../itemEmits'

const props = defineProps<{
  items: InventoryItem[]
  freeItems: InventoryItem[]
  hasSizes: boolean
  sizes: InventorySize[] | undefined
  members: Map<number, StationMember>
  inventoryType: InventoryTypeName | null
  lentOutItems: LentOutItem[]
  lentItemStationMap: Map<number, string>
  canEdit: boolean
  canCreateItem: boolean
  canQuickAssign: boolean
  canAddInternal: boolean
  canProcure: boolean
}>()

const emit = defineEmits<InventoryItemActionEmits & {
  quickAssign: []
  add: []
  openAssign: [itemId: number]
  createProcurement: []
}>()

const { t } = useI18n()

const resolvedType = computed(() => props.inventoryType ?? InventoryTypes.INTERNAL)

function sizeName(sizeId: number | null | undefined): string {
  if (!sizeId || !props.sizes) return ''
  return props.sizes.find(s => s.id === sizeId)?.label ?? ''
}
</script>

<template>
  <div v-if="items.length > 0 || canCreateItem" class="flex flex-wrap items-center justify-between gap-2">
    <SubHeader>{{ t('inventory.edit.itemsTitle') }} ({{ items.length }})</SubHeader>
    <div v-if="canCreateItem" class="flex items-center gap-2">
      <PrimaryButton v-if="canQuickAssign" :icon="['fas', 'user-plus']" @click="emit('quickAssign')">
        {{ t('inventory.edit.quickAssign') }}
      </PrimaryButton>
      <PrimaryButton v-if="canAddInternal" :icon="['fas', 'plus']" @click="emit('add')">
        {{ t('inventory.edit.addItem') }}
      </PrimaryButton>
    </div>
  </div>
  <ItemsTable
    v-if="items.length > 0"
    :items="items"
    :has-sizes="hasSizes"
    :sizes="sizes"
    :members="members"
    :show-actions="canEdit"
    :show-history="true"
    :inventory-type="resolvedType"
    :lent-out-items="lentOutItems"
    :lent-item-map="lentItemStationMap"
    @assign="emit('assign', $event)"
    @unassign="emit('unassign', $event)"
    @edit="emit('edit', $event)"
    @mark-lost="emit('markLost', $event)"
    @mark-found="emit('markFound', $event)"
    @history="emit('history', $event)"
    @delete="emit('delete', $event)"
  />
  <SubHeader v-if="canEdit && freeItems.length > 0">{{ t('inventory.detail.freeItems') }}</SubHeader>
  <div v-if="canEdit && freeItems.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
    <NeutralContainer v-for="item in freeItems" :key="item.id" class="flex items-center justify-between gap-2">
      <div>
        <div class="text-sm font-medium">
          {{ item.name }}
          <SizeBadge v-if="sizeName(item.sizeId)">{{ sizeName(item.sizeId) }}</SizeBadge>
        </div>
        <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
      </div>
      <PrimaryButton @click="emit('openAssign', item.id)">
        {{ t('inventory.detail.assign') }}
      </PrimaryButton>
    </NeutralContainer>
  </div>
  <div v-if="canProcure" class="flex gap-2">
    <PrimaryButton :icon="['fas', 'folder-plus']" @click="emit('createProcurement')">
      {{ t('inventory.detail.createProcurement') }}
    </PrimaryButton>
  </div>
</template>
