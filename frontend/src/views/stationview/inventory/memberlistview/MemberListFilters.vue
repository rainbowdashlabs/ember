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
import { type RestrictionSelection, emptyRestriction } from '@/components/input/restriction'
import type { FilterCriteria } from '@/composables/useMemberFilter'
import type { Inventory, MemberGroup, UserTag } from '@/api/types'

const { t } = useI18n()

const showEmpty = defineModel<boolean>('showEmpty', { required: true })
const showName = defineModel<boolean>('showName', { required: true })
const showInternalId = defineModel<boolean>('showInternalId', { required: true })
const showSize = defineModel<boolean>('showSize', { required: true })

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  inventories: Inventory[]
  visibleInventoryIds: Set<number>
}>()

const emit = defineEmits<{
  filter: [criteria: FilterCriteria]
  toggleInventory: [invId: number]
}>()

const restriction = ref<RestrictionSelection>(emptyRestriction())

function emitFilter() {
  emit('filter', {
    userTypes: restriction.value.userTypes,
    groupIds: restriction.value.groupIds,
    tagIds: restriction.value.tagIds,
    mode: restriction.value.mode,
  })
}
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center gap-4">
    <RestrictionPicker
        :groups="groups"
        :tags="tags"
        v-model="restriction"
        @update:model-value="emitFilter"
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
        <CheckboxInput v-model="showName" />
        {{ t('inventoryMembers.optName') }}
      </FieldLabel>
      <FieldLabel inline class="cursor-pointer">
        <CheckboxInput v-model="showInternalId" />
        {{ t('inventoryMembers.optInternalId') }}
      </FieldLabel>
      <FieldLabel inline class="cursor-pointer">
        <CheckboxInput v-model="showSize" />
        {{ t('inventoryMembers.optSize') }}
      </FieldLabel>
    </div>
  </NeutralContainer>
</template>
