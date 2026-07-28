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
import type { Inventory, InventoryItem } from '@/api/inventory'
import type { StationMember } from '@/api/types'
import MutedText from '@/components/typography/MutedText.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'

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
  if (!item.sizeId) return ''
  return props.sizeMap.get(item.sizeId) ?? ''
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
          <UserAvatar :identity="member.identity" :name="memberDisplayName(member)" size="sm" />
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
  <DataTable v-else>
    <template #head>
      <th v-if="exportMode" class="px-1 py-2 w-8">
        <CheckboxInput :model-value="selectedForExport.size === members.length && members.length > 0" @update:model-value="emit('toggleSelectAll')" />
      </th>
      <Th>{{ t('membersList.colName') }}</Th>
      <Th v-for="inv in inventories" :key="inv.id">{{ inv.name }}</Th>
    </template>
    <TRow v-for="member in members" :key="member.id"
        class="hover:bg-(--bg-accent)/30 cursor-pointer"
        @click="handleRowClick(member)">
      <td v-if="exportMode" class="px-1 py-2.5 w-8" @click.stop>
        <CheckboxInput :model-value="selectedForExport.has(member.id)" @update:model-value="emit('toggleExportSelection', member.id)" />
      </td>
      <Td class="font-medium text-primary">
        <div class="flex items-center gap-2">
          <UserAvatar :identity="member.identity" :name="memberDisplayName(member)" size="sm" />
          {{ memberDisplayName(member) }}
        </div>
      </Td>
      <Td v-for="inv in inventories" :key="inv.id">
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
        <MutedText v-else size="base">&mdash;</MutedText>
      </Td>
    </TRow>
  </DataTable>
</template>
