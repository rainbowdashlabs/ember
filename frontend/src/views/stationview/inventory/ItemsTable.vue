/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import type { InventoryItem, InventorySize, StationMember } from '@/api/types'
import { InventoryTypes, ItemSource } from '@/api/types'
import type { LentOutItem } from '@/api/lending'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import ItemActions from './itemstable/ItemActions.vue'
import ItemsTableDesktop from './itemstable/ItemsTableDesktop.vue'
import type { InventoryItemActionEmits } from './itemEmits'
import type { ItemTableApi } from './itemtable/useItemTable'
import { formatDate } from '@/util/format'

const { isMobile } = useBreakpoint()

const { t } = useI18n()

const props = withDefaults(defineProps<{
  items: InventoryItem[]
  hasSizes: boolean
  sizes?: InventorySize[]
  members?: Map<number, StationMember>
  showActions?: boolean
  showHistory?: boolean
  inventoryType?: string
  lentOutItems?: LentOutItem[]
  lentItemMap?: Map<number, string>
  containerPathById?: Map<number, string>
  tableApi?: ItemTableApi
}>(), {
  showActions: false,
  showHistory: false,
  inventoryType: InventoryTypes.INTERNAL,
})

const displayItems = computed(() => props.tableApi ? props.tableApi.filteredItems : props.items)

function locationLabel(containerId: number | null | undefined): string {
  if (!containerId) return ''
  return props.containerPathById?.get(containerId) ?? ''
}

function isLentOut(itemId: number): boolean {
  return props.lentItemMap?.has(itemId) ?? false
}

function lentToStationName(itemId: number): string | null {
  return props.lentItemMap?.get(itemId) ?? null
}

const isMixed = computed(() => props.inventoryType === InventoryTypes.MIXED)

const emit = defineEmits<InventoryItemActionEmits>()

function getSizeLabel(sizeId: number | null | undefined): string {
  if (!sizeId || !props.sizes) return t('common.unisize')
  return props.sizes.find(s => s.id === sizeId)?.label ?? t('common.unisize')
}

function getMemberIdentity(memberId: number | null | undefined) {
  if (!memberId || !props.members) return undefined
  const m = props.members.get(memberId)
  return m?.identity
}

</script>

<template>
  <div v-if="isMobile && items.length > 0" class="space-y-3">
    <NeutralContainer v-for="item in displayItems" :key="item.id" :class="item.lostAt ? 'opacity-60' : ''" class="space-y-2">
      <div class="flex items-center justify-between">
        <div>
          <router-link :to="{ name: 'inventory-item-detail', params: { id: item.id } }" class="font-medium hover:text-primary hover:underline">{{ item.name }}</router-link>
          <span v-if="item.lostAt" class="ml-2 text-xs text-error">{{ t('inventory.edit.lost') }} ({{ formatDate(item.lostAt) }})</span>
          <div v-if="isLentOut(item.id)" class="mt-0.5">
            <InfoBadge>
              <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-0.5 h-2.5 w-2.5"/>
              {{ t('inventory.detail.lentTo') }} {{ lentToStationName(item.id) }}
            </InfoBadge>
          </div>
        </div>
        <div v-if="hasSizes">
          <SizeBadge :lost="!!item.lostAt">{{ getSizeLabel(item.sizeId) }}</SizeBadge>
        </div>
      </div>
      <div class="grid grid-cols-1 gap-1 text-xs">
        <div v-if="item.internalId" class="text-(--text-muted)">{{ t('inventory.edit.colId') }}: {{ item.internalId }}</div>
        <div v-if="isMixed">
          <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
          <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
        </div>
        <div v-if="item.assignedTo">
          <span class="text-(--text-muted)">{{ t('inventory.edit.colAssigned') }}:</span>
          <router-link :to="{ name: 'inventory-member', params: { memberId: item.assignedTo } }" class="inline-block ml-1 font-medium hover:text-primary hover:underline" @click.stop>
            <MemberName :identity="getMemberIdentity(item.assignedTo)"/>
          </router-link>
        </div>
        <div v-else-if="locationLabel(item.containerId)" class="text-(--text-muted) flex items-center gap-1">
          <font-awesome-icon :icon="['fas', 'box']" class="h-3 w-3"/>
          {{ locationLabel(item.containerId) }}
        </div>
      </div>
      <div v-if="showActions || showHistory" class="flex items-center gap-0.5 pt-1 border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
        <ItemActions :item="item" :show-actions="showActions" :lent-out="isLentOut(item.id)"
                     @assign="emit('assign', $event)"
                     @unassign="emit('unassign', $event)"
                     @edit="emit('edit', $event)"
                     @mark-lost="emit('markLost', $event)"
                     @mark-found="emit('markFound', $event)"
                     @history="emit('history', $event)"
                     @delete="emit('delete', $event)"/>
      </div>
    </NeutralContainer>
  </div>

  <ItemsTableDesktop v-else-if="items.length > 0"
                     :items="displayItems" :has-sizes="hasSizes" :is-mixed="isMixed"
                     :table-api="tableApi"
                     :show-actions="showActions" :show-history="showHistory"
                     :lent-item-map="lentItemMap"
                     :container-path-by-id="containerPathById"
                     :get-size-label="getSizeLabel"
                     :get-member-identity="getMemberIdentity"
                     :format-date="formatDate"
                     @assign="emit('assign', $event)"
                     @unassign="emit('unassign', $event)"
                     @edit="emit('edit', $event)"
                     @mark-lost="emit('markLost', $event)"
                     @mark-found="emit('markFound', $event)"
                     @history="emit('history', $event)"
                     @delete="emit('delete', $event)"/>
</template>
