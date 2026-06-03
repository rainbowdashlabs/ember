/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import type { InventoryItem, InventorySize, StationMember } from '@/api/types'
import { InventoryTypes, ItemSource } from '@/api/types'
import type { LentOutItem } from '@/api/lending'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const { isMobile } = useBreakpoint()

const { t } = useI18n()
const router = useRouter()

const props = withDefaults(defineProps<{
  items: InventoryItem[]
  hasSizes: boolean
  sizes?: InventorySize[]
  members?: Map<number, StationMember>
  showActions?: boolean
  inventoryType?: string
  lentOutItems?: LentOutItem[]
  lentItemMap?: Map<number, string>
}>(), {
  showActions: false,
  inventoryType: InventoryTypes.INTERNAL,
})

function isLentOut(itemId: number): boolean {
  return props.lentItemMap?.has(itemId) ?? false
}

function lentToStationName(itemId: number): string | null {
  return props.lentItemMap?.get(itemId) ?? null
}

const isMixed = computed(() => props.inventoryType === InventoryTypes.MIXED)

const emit = defineEmits<{
  assign: [item: InventoryItem]
  unassign: [item: InventoryItem]
  edit: [item: InventoryItem]
  markLost: [item: InventoryItem]
  markFound: [item: InventoryItem]
  history: [item: InventoryItem]
  delete: [item: InventoryItem]
}>()

function getSizeLabel(sizeId: number | null | undefined): string {
  if (!sizeId || !props.sizes) return t('common.unisize')
  return props.sizes.find(s => s.id === sizeId)?.label ?? t('common.unisize')
}

function getMemberName(memberId: number | null | undefined): string {
  if (!memberId) return ''
  if (!props.members) return `#${memberId}`
  const m = props.members.get(memberId)
  return m ? (m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`) : `#${memberId}`
}

function getMemberIdentity(memberId: number | null | undefined) {
  if (!memberId || !props.members) return undefined
  const m = props.members.get(memberId)
  return m?.identity
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
}
</script>

<template>
  <!-- Mobile card layout -->
  <div v-if="isMobile && items.length > 0" class="space-y-3">
    <NeutralContainer v-for="item in items" :key="item.id" :class="item.lostAt ? 'opacity-60' : ''" class="space-y-2">
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
          <SecondaryButton class="!bg-transparent !p-0 ml-1 text-primary font-medium hover:underline" @click.stop="router.push({ name: 'inventory-member', params: { memberId: item.assignedTo } })">
            <MemberName :identity="getMemberIdentity(item.assignedTo)"/>
          </SecondaryButton>
        </div>
      </div>
      <div v-if="showActions" class="flex items-center gap-0.5 pt-1 border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
        <IconButton v-if="!item.lostAt && !isLentOut(item.id)" :icon="['fas', 'user']" :label="item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign')" class="text-primary hover:bg-primary/15" @click="emit('assign', item)"/>
        <IconButton class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" v-if="item.assignedTo && !item.lostAt && !isLentOut(item.id)" :icon="['fas', 'right-from-bracket']" :label="t('inventory.edit.unassign')" @click="emit('unassign', item)"/>
        <IconButton v-if="!item.lostAt && !isLentOut(item.id)" :icon="['fas', 'triangle-exclamation']" :label="t('inventory.edit.markLost')" class="text-error hover:bg-error/15" @click="emit('markLost', item)"/>
        <IconButton v-if="item.lostAt" :icon="['fas', 'check']" :label="t('inventory.edit.markFound')" class="text-success hover:bg-success/15" @click="emit('markFound', item)"/>
        <IconButton class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" :icon="['fas', 'clock-rotate-left']" :label="t('inventory.edit.historyTitle')" @click="emit('history', item)"/>
        <EditButton @click="emit('edit', item)"/>
        <DeleteButton v-if="!isLentOut(item.id)" @click="emit('delete', item)"/>
      </div>
    </NeutralContainer>
  </div>

  <!-- Desktop table layout -->
  <NeutralContainer v-else-if="items.length > 0" class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <THead>
          <Th>{{ t('inventory.edit.colName') }}</Th>
          <Th>{{ t('inventory.edit.colId') }}</Th>
          <Th v-if="hasSizes">{{ t('inventory.edit.colSize') }}</Th>
          <Th v-if="isMixed">{{ t('inventory.edit.colSource') }}</Th>
          <Th>{{ t('inventory.edit.colAssigned') }}</Th>
          <th v-if="showActions" class="px-3 py-2"></th>
        </THead>
      </thead>
      <tbody>
        <TRow v-for="item in items" :key="item.id"
            :class="item.lostAt ? 'opacity-60' : ''">
          <Td class="font-medium">
            <router-link :to="{ name: 'inventory-item-detail', params: { id: item.id } }" class="hover:text-primary hover:underline">{{ item.name }}</router-link>
            <span v-if="item.lostAt" class="ml-2 text-xs text-error font-normal">
              {{ t('inventory.edit.lost') }} ({{ formatDate(item.lostAt) }})
            </span>
            <template v-if="isLentOut(item.id)">
              <span class="ml-2 inline-flex items-center gap-1">
                <InfoBadge>
                  <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-0.5 h-2.5 w-2.5"/>
                  {{ t('inventory.detail.lentTo') }} {{ lentToStationName(item.id) }}
                </InfoBadge>
              </span>
            </template>
          </Td>
          <Td muted>{{ item.internalId || '–' }}</Td>
          <Td v-if="hasSizes"><SizeBadge :lost="!!item.lostAt">{{ getSizeLabel(item.sizeId) }}</SizeBadge></Td>
          <Td v-if="isMixed">
            <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
            <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
            <span v-else class="text-(--text-muted)">–</span>
          </Td>
          <Td>
            <SecondaryButton v-if="item.assignedTo" class="!bg-transparent !p-0 text-primary font-medium hover:underline" @click.stop="router.push({ name: 'inventory-member', params: { memberId: item.assignedTo } })"><MemberName :identity="getMemberIdentity(item.assignedTo)"/></SecondaryButton>
            <span v-else class="text-(--text-muted)">–</span>
          </Td>
          <Td v-if="showActions" align="right">
            <div class="flex items-center justify-end gap-0.5">
              <IconButton v-if="!item.lostAt && !isLentOut(item.id)" :icon="['fas', 'user']"
                          :label="item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign')"
                          class="text-primary hover:bg-primary/15" @click="emit('assign', item)" />
              <IconButton v-if="item.assignedTo && !item.lostAt && !isLentOut(item.id)" :icon="['fas', 'right-from-bracket']"
                          :label="t('inventory.edit.unassign')"
                          class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                          @click="emit('unassign', item)" />
              <IconButton v-if="!item.lostAt && !isLentOut(item.id)" :icon="['fas', 'triangle-exclamation']"
                          :label="t('inventory.edit.markLost')" class="text-error hover:bg-error/15"
                          @click="emit('markLost', item)" />
              <IconButton v-if="item.lostAt" :icon="['fas', 'check']" :label="t('inventory.edit.markFound')"
                          class="text-success hover:bg-success/15" @click="emit('markFound', item)" />
              <IconButton :icon="['fas', 'clock-rotate-left']" :label="t('inventory.edit.historyTitle')"
                          class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                          @click="emit('history', item)" />
              <EditButton @click="emit('edit', item)" />
              <DeleteButton v-if="!isLentOut(item.id)" @click="emit('delete', item)" />
            </div>
          </Td>
        </TRow>
      </tbody>
    </table>
  </NeutralContainer>
</template>
