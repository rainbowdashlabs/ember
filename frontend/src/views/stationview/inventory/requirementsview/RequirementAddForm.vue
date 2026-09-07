/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { Inventory } from '@/api/inventory'
import type { MemberGroup } from '@/api/types'
import type { StationGroup } from '@/api/clusterStationGroups'
import RequirementTargetFields from './RequirementTargetFields.vue'

const targetType = defineModel<'userType' | 'group'>('targetType', { default: 'userType' })
const userType = defineModel<string>('userType', { default: '' })
const groupId = defineModel<string>('groupId', { default: '' })
const inventoryId = defineModel<string>('inventoryId', { default: '' })
const quantity = defineModel<number>('quantity', { default: 1 })
const stationGroupId = defineModel<string>('stationGroupId', { default: '' })

defineProps<{
  inventories: Inventory[]
  allGroups: MemberGroup[]
  /**
   * The association's ways of filing its stations. Empty at a station, which writes requirements for
   * itself and has nothing to point them at.
   */
  stationGroups?: StationGroup[]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <RequirementTargetFields
      v-model:target-type="targetType"
      v-model:user-type="userType"
      v-model:group-id="groupId"
      v-model:station-group-id="stationGroupId"
      :all-groups="allGroups"
      :station-groups="stationGroups"
    />

    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.inventory') }}</FieldLabel>
      <SelectInput v-model="inventoryId" data-testid="requirement-inventory">
        <option value="" disabled>{{ t('inventory.requirements.selectInventory') }}</option>
        <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
      </SelectInput>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.quantity') }}</FieldLabel>
      <NumberInput v-model="quantity" :min="1" />
    </div>
  </div>
</template>
