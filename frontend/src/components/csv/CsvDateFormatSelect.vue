/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {CsvDateFormat} from '@/util/csvDate'

/**
 * Picks the order the days of an imported file are written in. Each order is shown with the 5th of
 * January 2026 written in it, so the reader matches the shape against their file; dots, dashes and
 * slashes all read alike.
 */
const format = defineModel<CsvDateFormat>({required: true})

const {t} = useI18n()

const options = computed(() => [
  {value: 'auto', label: t('csvImport.dateFormatAuto')},
  {value: 'day-month-year', label: t('csvImport.dateDayMonthYear')},
  {value: 'month-day-year', label: t('csvImport.dateMonthDayYear')},
  {value: 'year-month-day', label: t('csvImport.dateYearMonthDay')},
])
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('csvImport.dateFormat') }}</FieldLabel>
    <SelectInput v-model="format">
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </SelectInput>
  </div>
</template>
