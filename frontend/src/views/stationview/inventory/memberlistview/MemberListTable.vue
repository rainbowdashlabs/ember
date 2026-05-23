/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import type { Inventory, InventoryItem, StationMember } from '@/api/types'

const { t } = useI18n()

const props = defineProps<{
  members: StationMember[]
  inventories: Inventory[]
  exportMode: boolean
  selectedForExport: Set<number>
  isMobile: boolean
  memberItemMap: Map<number, Map<number, InventoryItem[]>>
  showName: boolean
  showInternalId: boolean
  showSize: boolean
  sizeMap: Map<number, string>
}>()

const emit = defineEmits<{
  goToMember: [memberId: number]
  toggleExportSelection: [id: number]
  toggleSelectAll: []
}>()

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function memberInventoryItems(memberId: number, inventoryId: number): InventoryItem[] {
  return props.memberItemMap.get(memberId)?.get(inventoryId) ?? []
}

function memberInventoryCount(memberId: number, inventoryId: number): number {
  return memberInventoryItems(memberId, inventoryId).length
}

function itemNamePart(item: InventoryItem): string {
  const parts: string[] = []
  if (props.showName && item.name) parts.push(item.name)
  if (props.showInternalId && item.internalId) parts.push(`(${item.internalId})`)
  return parts.join(' ')
}

function itemSizeLabel(item: InventoryItem): string {
  if (!props.showSize) return ''
  if (!item.sizeId) return t('common.unisize')
  return props.sizeMap.get(item.sizeId) ?? t('common.unisize')
}

function handleRowClick(member: StationMember) {
  if (props.exportMode) {
    emit('toggleExportSelection', member.id)
  } else {
    emit('goToMember', member.id)
  }
}
</script>

<template>
  <!-- Member cards (mobile) -->
  <div v-if="isMobile" class="space-y-3">
    <NeutralContainer v-for="member in members" :key="member.id" class="space-y-2 cursor-pointer" @click="handleRowClick(member)">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <CheckboxInput v-if="exportMode" :model-value="selectedForExport.has(member.id)" @update:model-value="emit('toggleExportSelection', member.id)" />
          <UserAvatar :member-id="member.id" :name="memberDisplayName(member)" size="sm" />
          <span class="font-medium text-primary text-sm">{{ memberDisplayName(member) }}</span>
        </div>
      </div>
      <div v-for="inv in inventories" :key="inv.id" class="text-xs">
        <template v-if="memberInventoryCount(member.id, inv.id) > 0">
          <span class="font-medium text-(--text-muted)">{{ inv.name }}:</span>
          <div class="flex flex-wrap gap-1 mt-0.5">
            <span v-for="item in memberInventoryItems(member.id, inv.id)" :key="item.id"
                  :class="item.lostAt ? 'text-error' : ''"
                  class="inline-flex items-center gap-1">
              <template v-if="itemNamePart(item)">{{ itemNamePart(item) }}</template>
              <SizeBadge v-if="itemSizeLabel(item)" :lost="!!item.lostAt">{{ itemSizeLabel(item) }}</SizeBadge>
              <span v-if="item.lostAt" class="text-[10px]">({{ t('inventoryMembers.lost') }})</span>
            </span>
          </div>
        </template>
      </div>
    </NeutralContainer>
  </div>

  <!-- Member table (desktop) -->
  <NeutralContainer v-else class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
          <th v-if="exportMode" class="px-1 py-2 w-8">
            <CheckboxInput :model-value="selectedForExport.size === members.length && members.length > 0" @update:model-value="emit('toggleSelectAll')" />
          </th>
          <th class="px-3 py-2 font-medium">{{ t('membersList.colName') }}</th>
          <th v-for="inv in inventories" :key="inv.id" class="px-3 py-2 font-medium">{{ inv.name }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="member in members" :key="member.id"
            class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 hover:bg-(--bg-accent)/30 cursor-pointer"
            @click="handleRowClick(member)">
          <td v-if="exportMode" class="px-1 py-2.5 w-8" @click.stop>
            <CheckboxInput :model-value="selectedForExport.has(member.id)" @update:model-value="emit('toggleExportSelection', member.id)" />
          </td>
          <td class="px-3 py-2.5 font-medium text-primary">
            <div class="flex items-center gap-2">
              <UserAvatar :member-id="member.id" :name="memberDisplayName(member)" size="sm" />
              {{ memberDisplayName(member) }}
            </div>
          </td>
          <td v-for="inv in inventories" :key="inv.id" class="px-3 py-2.5">
            <template v-if="memberInventoryCount(member.id, inv.id) > 0">
              <div class="flex flex-wrap gap-1">
                <span v-for="item in memberInventoryItems(member.id, inv.id)" :key="item.id"
                      :class="item.lostAt ? 'text-error' : ''"
                      class="inline-flex items-center gap-1 text-xs">
                  <template v-if="itemNamePart(item)">{{ itemNamePart(item) }}</template>
                  <SizeBadge v-if="itemSizeLabel(item)" :lost="!!item.lostAt">{{ itemSizeLabel(item) }}</SizeBadge>
                  <span v-if="item.lostAt" class="text-[10px]">({{ t('inventoryMembers.lost') }})</span>
                </span>
              </div>
            </template>
            <span v-else class="text-(--text-muted)">&mdash;</span>
          </td>
        </tr>
      </tbody>
    </table>
  </NeutralContainer>
</template>
