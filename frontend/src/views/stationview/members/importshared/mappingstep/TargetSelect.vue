/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { SKIP_TARGET } from '../memberImport'

const { t } = useI18n()

const modelValue = defineModel<string>({required: true})

defineProps<{
  targetOptions: { value: string; label: string; group?: string }[]
  fieldScopeGroups: string[]
  primaryGroupLabel: string
  managerCount: number
}>()
</script>

<template>
  <SelectInput v-model="modelValue" class="flex-1">
    <option :value="SKIP_TARGET">{{ t('memberImport.targetSkip') }}</option>
    <optgroup :label="primaryGroupLabel">
      <option v-for="opt in targetOptions.filter(o => o.group === primaryGroupLabel)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </optgroup>
    <optgroup v-for="mi in managerCount" :key="'mgr'+mi" :label="t('memberImport.groupManager', { n: mi })">
      <option v-for="opt in targetOptions.filter(o => o.group === t('memberImport.groupManager', { n: mi }))" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </optgroup>
    <optgroup v-for="sg in fieldScopeGroups" :key="sg" :label="sg">
      <option v-for="opt in targetOptions.filter(o => o.group === sg)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </optgroup>
  </SelectInput>
</template>
