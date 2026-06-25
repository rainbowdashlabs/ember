/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {AttendanceTemplateField} from '@/api/types'

const {t} = useI18n()

defineProps<{
  field: AttendanceTemplateField
  source: string
  value: string
  sources: { value: string; label: string }[]
}>()

const emit = defineEmits<{
  updateSource: [source: string]
  updateValue: [value: string]
}>()
</script>

<template>
  <div class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
    <div class="text-sm font-medium">
      {{ field.name }} <span class="text-xs text-(--text-muted)">({{ field.fieldType }})</span>
    </div>
    <div class="grid gap-2 sm:grid-cols-2">
      <SelectInput
          :model-value="source"
          @update:model-value="emit('updateSource', ($event as string) ?? '')"
      >
        <option value="">{{ t('events.noDefault') }}</option>
        <option value="VALUE">{{ t('events.staticValue') }}</option>
        <option v-for="src in sources" :key="src.value" :value="src.value">{{ src.label }}</option>
      </SelectInput>
      <TextInput
          v-if="source === 'VALUE'"
          :model-value="value"
          :placeholder="t('events.defaultValuePlaceholder')"
          @update:model-value="emit('updateValue', ($event as string) ?? '')"
      />
    </div>
  </div>
</template>
