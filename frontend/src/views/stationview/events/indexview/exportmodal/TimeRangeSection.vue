/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

const exportMode = defineModel<string>('exportMode', {required: true})
const exportYear = defineModel<string>('exportYear', {required: true})
const exportMonth = defineModel<string>('exportMonth', {required: true})
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('events.exportPeriod') }}</FieldLabel>
    <div class="flex items-center gap-2 flex-wrap">
      <SelectInput v-model="exportMode" class="w-32">
        <option value="year">{{ t('events.exportYear') }}</option>
        <option value="month">{{ t('events.exportMonth') }}</option>
      </SelectInput>
      <TextInput v-model="exportYear" class="w-24"/>
      <SelectInput v-if="exportMode === 'month'" v-model="exportMonth" class="w-32">
        <option v-for="m in 12" :key="m" :value="String(m)">{{ new Date(2000, m - 1).toLocaleDateString('de-DE', { month: 'long' }) }}</option>
      </SelectInput>
    </div>
  </div>
</template>
