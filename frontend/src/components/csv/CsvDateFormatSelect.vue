/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {CSV_DATE_FORMATS, type CsvDateFormat} from '@/util/csvDate'

/**
 * Picks how the days of an imported file are written. Each format is shown with the 5th of January
 * 2026 written in it, so the reader matches the shape against their file rather than reading the
 * letters.
 */
const format = defineModel<CsvDateFormat>({required: true})

const {t} = useI18n()

const EXAMPLES: Readonly<Record<(typeof CSV_DATE_FORMATS)[number], string>> = {
  'DD.MM.YYYY': '05.01.2026',
  'DD-MM-YYYY': '05-01-2026',
  'YYYY-MM-DD': '2026-01-05',
  'MM/DD/YYYY': '01/05/2026',
  'DD/MM/YYYY': '05/01/2026',
}
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('csvImport.dateFormat') }}</FieldLabel>
    <SelectInput v-model="format">
      <option value="auto">{{ t('csvImport.dateFormatAuto') }}</option>
      <option v-for="candidate in CSV_DATE_FORMATS" :key="candidate" :value="candidate">
        {{ candidate }} ({{ EXAMPLES[candidate] }})
      </option>
    </SelectInput>
  </div>
</template>
