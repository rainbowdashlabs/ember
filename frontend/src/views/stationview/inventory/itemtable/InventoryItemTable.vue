/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberInventoryLink from '@/components/inventory/MemberInventoryLink.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {ItemOwner, type InventoryItem} from '@/api/inventory'
import type {MemberIdentity} from '@/api/types'
import {formatDate} from '@/util/format'
import ItemActions from '../itemstable/ItemActions.vue'
import type {InventoryItemActionEmits} from '../itemEmits'
import type {ItemTableApi} from './useItemTable'

/**
 * The pieces of one inventory, in the table every screen of the inventory shows them in.
 *
 * <p>The piece's name leads to its own page and the holder's name to what else they hold. A piece
 * that is lost is dimmed and says since when; one lent to another station says to whom.
 */
const props = withDefaults(defineProps<{
  items: ItemTableApi
  /** The pieces of this part, where the inventory is shown a block per kind. */
  subset?: readonly InventoryItem[]
  showActions?: boolean
  lentItemMap?: Map<number, string>
  containerPathById?: Map<number, string>
  memberIdentity: (memberId: number) => MemberIdentity | null | undefined
}>(), {
  subset: undefined,
  showActions: false,
  lentItemMap: () => new Map(),
  containerPathById: () => new Map(),
})

const emit = defineEmits<InventoryItemActionEmits>()

const {t} = useI18n()
const routes = useInventoryRoutes()

function locationOf(item: InventoryItem): string {
  return item.containerId ? props.containerPathById.get(item.containerId) ?? '' : ''
}

function dimmedWhenLost(item: InventoryItem): string {
  return item.lostAt ? 'opacity-60' : ''
}
</script>

<template>
  <RecordTable :row-class="dimmedWhenLost" :subset="subset" :table="items.table" plain test-id="items-table">
    <template #cell-name="{row}">
      <router-link :to="{name: routes.item, params: {id: row.id}}" class="font-medium hover:text-primary hover:underline">{{ row.name }}</router-link>
      <span v-if="row.lostAt" class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} ({{ formatDate(row.lostAt) }})</span>
      <InfoBadge v-if="lentItemMap.has(row.id)" class="ml-2">
        <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-0.5 h-2.5 w-2.5"/>
        {{ t('inventory.detail.lentTo') }} {{ lentItemMap.get(row.id) }}
      </InfoBadge>
    </template>
    <template #cell-size="{row, text}">
      <SizeBadge v-if="text" :lost="!!row.lostAt">{{ text }}</SizeBadge>
    </template>
    <template #cell-owner="{row}">
      <PrimaryBadge v-if="row.ownerKind === ItemOwner.STATION">{{ t('inventory.edit.ownerStation') }}</PrimaryBadge>
      <SecondaryBadge v-else-if="row.ownerKind === ItemOwner.CLUSTER">{{ t('inventory.edit.ownerCluster') }}</SecondaryBadge>
      <InfoBadge v-else-if="row.ownerKind === ItemOwner.PARTNER_STATION">{{ t('inventory.edit.ownerPartner') }}</InfoBadge>
    </template>
    <template #cell-tags="{row}">
      <span class="flex flex-wrap gap-1">
        <SecondaryBadge v-for="name in items.itemTagNames(row)" :key="name">{{ name }}</SecondaryBadge>
      </span>
    </template>
    <template #cell-assigned="{row}">
      <MemberInventoryLink v-if="row.assignedTo" :identity="memberIdentity(row.assignedTo)" :member-id="row.assignedTo"/>
      <span v-else-if="locationOf(row)" class="inline-flex items-center gap-1 text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'box']" class="h-3 w-3"/>
        {{ locationOf(row) }}
      </span>
    </template>
    <template #actions="{row}">
      <ItemActions
          :item="row"
          :lent-out="lentItemMap.has(row.id)"
          :show-actions="showActions"
          @assign="emit('assign', $event)"
          @delete="emit('delete', $event)"
          @edit="emit('edit', $event)"
          @history="emit('history', $event)"
          @mark-found="emit('markFound', $event)"
          @mark-lost="emit('markLost', $event)"
          @unassign="emit('unassign', $event)"
      />
    </template>
  </RecordTable>
</template>
