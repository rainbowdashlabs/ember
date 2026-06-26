/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

defineProps<{
  fieldType: string
  enumOptions: string
}>()

const hasDefault = defineModel<boolean>('hasDefault', {required: true})
const defaultValue = defineModel<string>('defaultValue', {required: true})
const defaultBool = defineModel<boolean>('defaultBool', {required: true})
const defaultToday = defineModel<boolean>('defaultToday', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ t('attendanceConfig.fieldHasDefault') }}</label>
      <ToggleInput v-model="hasDefault"/>
    </div>
    <template v-if="hasDefault">
      <template v-if="fieldType === 'BOOLEAN'">
        <ToggleInput v-model="defaultBool"/>
      </template>
      <template v-else-if="fieldType === 'DATE'">
        <ToggleInput v-model="defaultToday"/>
        <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldDefaultDateHint') }}</p>
      </template>
      <template v-else-if="fieldType === 'ENUM'">
        <SelectInput v-model="defaultValue">
          <option value="">—</option>
          <option v-for="opt in enumOptions.split('\n').map(o => o.trim()).filter(o => o)" :key="opt"
                  :value="opt">{{ opt }}
          </option>
        </SelectInput>
      </template>
      <template v-else>
        <TextInput v-model="defaultValue" :placeholder="t('attendanceConfig.fieldDefaultValuePlaceholder')"/>
      </template>
      <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldDefaultValueHint') }}</p>
    </template>
  </div>
</template>
