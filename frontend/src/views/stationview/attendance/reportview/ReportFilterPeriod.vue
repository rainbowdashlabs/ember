/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

defineProps<{
  periodOptions: { value: string; label: string }[]
  yearOptions: number[]
  monthOptions: { value: number; label: string }[]
  weekOptions: number[]
}>()

const selectedPeriod = defineModel<string>('selectedPeriod', {required: true})
const selectedYear = defineModel<number>('selectedYear', {required: true})
const selectedMonth = defineModel<number>('selectedMonth', {required: true})
const selectedWeek = defineModel<number>('selectedWeek', {required: true})
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('attendanceReport.period') }}</FieldLabel>
      <SelectInput :model-value="selectedPeriod" class="w-full" @update:model-value="selectedPeriod = String($event)">
        <option v-for="opt in periodOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </SelectInput>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('attendanceReport.year') }}</FieldLabel>
      <SelectInput :model-value="String(selectedYear)" class="w-full" @update:model-value="selectedYear = Number($event)">
        <option v-for="y in yearOptions" :key="y" :value="String(y)">{{ y }}</option>
      </SelectInput>
    </div>
    <div v-if="selectedPeriod === 'month'" class="space-y-1">
      <FieldLabel>{{ t('attendanceReport.month') }}</FieldLabel>
      <SelectInput :model-value="String(selectedMonth)" class="w-full" @update:model-value="selectedMonth = Number($event)">
        <option v-for="m in monthOptions" :key="m.value" :value="String(m.value)">{{ m.label }}</option>
      </SelectInput>
    </div>
    <div v-if="selectedPeriod === 'week'" class="space-y-1">
      <FieldLabel>{{ t('attendanceReport.week') }}</FieldLabel>
      <SelectInput :model-value="String(selectedWeek)" class="w-full" @update:model-value="selectedWeek = Number($event)">
        <option v-for="w in weekOptions" :key="w" :value="String(w)">KW {{ w }}</option>
      </SelectInput>
    </div>
  </div>
</template>
