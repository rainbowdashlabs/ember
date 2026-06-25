/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

interface Option {
  value: string
  label: string
}

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<string[]>('selectedGroupIds', {required: true})
const selectedRounding = defineModel<string>('selectedRounding', {required: true})

defineProps<{
  userTypeOptions: Option[]
  groupOptions: Option[]
  roundingOptions: { value: string; label: string }[]
}>()
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('attendanceReport.userTypes') }}</FieldLabel>
      <MultiSelectDropdown
          v-model="selectedUserTypes"
          :options="userTypeOptions"
          :placeholder="t('attendanceReport.selectUserTypes')"
      />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('attendanceReport.groups') }}</FieldLabel>
      <MultiSelectDropdown
          v-model="selectedGroupIds"
          :options="groupOptions"
          :placeholder="t('attendanceReport.selectGroups')"
      />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('attendanceReport.rounding') }}</FieldLabel>
      <SelectInput v-model="selectedRounding" class="w-full">
        <option v-for="opt in roundingOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </SelectInput>
    </div>
  </div>
</template>
