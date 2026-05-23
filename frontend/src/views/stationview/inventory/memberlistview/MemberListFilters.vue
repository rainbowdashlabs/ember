/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type { Inventory, MemberGroup } from '@/api/types'

const { t } = useI18n()

const filterRole = defineModel<string>('filterRole', { required: true })
const showEmpty = defineModel<boolean>('showEmpty', { required: true })

const props = defineProps<{
  filterableRoles: string[]
  groups: MemberGroup[]
  filterGroups: Set<number>
  inventories: Inventory[]
  visibleInventoryIds: Set<number>
  showName: boolean
  showInternalId: boolean
  showSize: boolean
}>()

const emit = defineEmits<{
  toggleGroupFilter: [groupId: number]
  toggleInventory: [invId: number]
  'update:showName': [value: boolean]
  'update:showInternalId': [value: boolean]
  'update:showSize': [value: boolean]
}>()

const roleLabels: Record<string, string> = {
  MEMBER: 'Mitglied',
  GUARDIAN: 'Erziehungsberechtigter',
  TEAM: 'Team',
}

function translateRole(role: string): string {
  return roleLabels[role] ?? role
}
</script>

<template>
  <!-- Filters -->
  <NeutralContainer class="flex flex-wrap items-center gap-4">
    <div class="flex items-center gap-2">
      <label class="text-sm font-medium">{{ t('inventoryMembers.role') }}</label>
      <SelectInput v-model="filterRole" class="w-40 text-sm">
        <option value="">{{ t('inventoryMembers.allRoles') }}</option>
        <option v-for="role in filterableRoles" :key="role" :value="role">{{ translateRole(role) }}</option>
      </SelectInput>
    </div>
    <div class="flex items-center gap-2">
      <label class="text-sm font-medium">{{ t('inventoryMembers.showEmpty') }}</label>
      <ToggleInput v-model="showEmpty" />
    </div>
  </NeutralContainer>

  <!-- Group multi-select -->
  <NeutralContainer v-if="groups.length > 0" class="space-y-2">
    <p class="text-sm font-medium">{{ t('inventoryMembers.group') }}</p>
    <div class="flex flex-wrap gap-2">
      <label v-for="g in groups" :key="g.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
        <CheckboxInput :model-value="filterGroups.has(g.id)" @update:model-value="emit('toggleGroupFilter', g.id)" />
        {{ g.name }}
      </label>
    </div>
  </NeutralContainer>

  <!-- Inventory column toggles -->
  <NeutralContainer class="space-y-2">
    <p class="text-sm font-medium">{{ t('inventoryMembers.columns') }}</p>
    <div class="flex flex-wrap gap-2">
      <label v-for="inv in inventories" :key="inv.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
        <CheckboxInput :model-value="visibleInventoryIds.has(inv.id)" @update:model-value="emit('toggleInventory', inv.id)" />
        {{ inv.name }}
      </label>
    </div>
  </NeutralContainer>

  <!-- Display options -->
  <NeutralContainer class="space-y-2">
    <p class="text-sm font-medium">{{ t('inventoryMembers.displayOptions') }}</p>
    <div class="flex flex-wrap gap-4">
      <label class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
        <CheckboxInput :model-value="showName" @update:model-value="emit('update:showName', $event)" />
        {{ t('inventoryMembers.optName') }}
      </label>
      <label class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
        <CheckboxInput :model-value="showInternalId" @update:model-value="emit('update:showInternalId', $event)" />
        {{ t('inventoryMembers.optInternalId') }}
      </label>
      <label class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
        <CheckboxInput :model-value="showSize" @update:model-value="emit('update:showSize', $event)" />
        {{ t('inventoryMembers.optSize') }}
      </label>
    </div>
  </NeutralContainer>
</template>
