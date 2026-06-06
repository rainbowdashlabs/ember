/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type { FilterCriteria } from '@/composables/useMemberFilter'
import type { Inventory, MemberGroup, UserTag } from '@/api/types'

const { t } = useI18n()

const showEmpty = defineModel<boolean>('showEmpty', { required: true })

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  inventories: Inventory[]
  visibleInventoryIds: Set<number>
  showName: boolean
  showInternalId: boolean
  showSize: boolean
}>()

const emit = defineEmits<{
  filter: [criteria: FilterCriteria]
  toggleInventory: [invId: number]
  'update:showName': [value: boolean]
  'update:showInternalId': [value: boolean]
  'update:showSize': [value: boolean]
}>()

const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const filterMode = ref<'AND' | 'OR'>('AND')

function emitFilter() {
  emit('filter', {
    userTypes: selectedUserTypes.value,
    groupIds: selectedGroupIds.value,
    tagIds: selectedTagIds.value,
    mode: filterMode.value,
  })
}
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center gap-4">
    <RestrictionPicker
        :groups="groups"
        :tags="tags"
        :selected-user-types="selectedUserTypes"
        :selected-group-ids="selectedGroupIds"
        :selected-tag-ids="selectedTagIds"
        :mode="filterMode"
        @update:selected-user-types="v => { selectedUserTypes = v; emitFilter() }"
        @update:selected-group-ids="v => { selectedGroupIds = v; emitFilter() }"
        @update:selected-tag-ids="v => { selectedTagIds = v; emitFilter() }"
        @update:mode="v => { filterMode = v; emitFilter() }"
    />
    <div class="flex items-center gap-2">
      <label class="text-sm font-medium">{{ t('inventoryMembers.showEmpty') }}</label>
      <ToggleInput v-model="showEmpty" />
    </div>
  </NeutralContainer>

  <!-- Inventory column toggles -->
  <NeutralContainer class="space-y-2">
    <p class="text-sm font-medium">{{ t('inventoryMembers.columns') }}</p>
    <div class="flex flex-wrap gap-2">
      <FieldLabel v-for="inv in inventories" :key="inv.id" inline class="cursor-pointer">
        <CheckboxInput :model-value="visibleInventoryIds.has(inv.id)" @update:model-value="emit('toggleInventory', inv.id)" />
        {{ inv.name }}
      </FieldLabel>
    </div>
  </NeutralContainer>

  <!-- Display options -->
  <NeutralContainer class="space-y-2">
    <p class="text-sm font-medium">{{ t('inventoryMembers.displayOptions') }}</p>
    <div class="flex flex-wrap gap-4">
      <FieldLabel inline class="cursor-pointer">
        <CheckboxInput :model-value="showName" @update:model-value="emit('update:showName', $event)" />
        {{ t('inventoryMembers.optName') }}
      </FieldLabel>
      <FieldLabel inline class="cursor-pointer">
        <CheckboxInput :model-value="showInternalId" @update:model-value="emit('update:showInternalId', $event)" />
        {{ t('inventoryMembers.optInternalId') }}
      </FieldLabel>
      <FieldLabel inline class="cursor-pointer">
        <CheckboxInput :model-value="showSize" @update:model-value="emit('update:showSize', $event)" />
        {{ t('inventoryMembers.optSize') }}
      </FieldLabel>
    </div>
  </NeutralContainer>
</template>
