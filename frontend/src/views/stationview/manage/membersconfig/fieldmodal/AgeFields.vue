/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {ProfileField} from '@/api/profileFields'

defineProps<{
  dateFields: ProfileField[]
}>()

const source = defineModel<string>('source', {required: true})
const mode = defineModel<string>('mode', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldAgeSource') }}</FieldLabel>
      <SelectInput v-model="source">
        <option disabled value="">{{ t('membersConfig.fieldAgeSourcePlaceholder') }}</option>
        <option v-for="f in dateFields" :key="f.id" :value="f.name">{{ f.name }}</option>
      </SelectInput>
      <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldAgeSourceHint') }}</p>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.fieldAgeMode') }}</FieldLabel>
      <SelectInput v-model="mode">
        <option value="now">{{ t('membersConfig.fieldAgeModeNow') }}</option>
        <option value="end_of_year">{{ t('membersConfig.fieldAgeModeEndOfYear') }}</option>
      </SelectInput>
    </div>
  </div>
</template>
