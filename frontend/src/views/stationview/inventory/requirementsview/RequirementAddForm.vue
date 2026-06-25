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
import type { Inventory, MemberGroup } from '@/api/types'
import { StationUserType } from '@/api/types'
import { userTypeFriendlyNames } from './types'

const targetType = defineModel<'userType' | 'group'>('targetType', { default: 'userType' })
const userType = defineModel<string>('userType', { default: '' })
const groupId = defineModel<string>('groupId', { default: '' })
const inventoryId = defineModel<string>('inventoryId', { default: '' })
const quantity = defineModel<number>('quantity', { default: 1 })

defineProps<{
  inventories: Inventory[]
  allGroups: MemberGroup[]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.targetType') }}</FieldLabel>
      <SelectInput v-model="targetType">
        <option value="userType">{{ t('inventory.requirements.byUserType') }}</option>
        <option value="group">{{ t('inventory.requirements.byGroup') }}</option>
      </SelectInput>
    </div>

    <div v-if="targetType === 'userType'" class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.userType') }}</FieldLabel>
      <SelectInput v-model="userType">
        <option value="" disabled>{{ t('inventory.requirements.selectUserType') }}</option>
        <option v-for="(value, key) in StationUserType" :key="key" :value="value">{{ userTypeFriendlyNames[value] ?? value }}</option>
      </SelectInput>
    </div>

    <div v-if="targetType === 'group'" class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.group') }}</FieldLabel>
      <SelectInput v-model="groupId">
        <option value="" disabled>{{ t('inventory.requirements.selectGroup') }}</option>
        <option v-for="group in allGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
      </SelectInput>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.requirements.inventory') }}</FieldLabel>
      <SelectInput v-model="inventoryId">
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
