/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {AttendanceTemplateField} from '@/api/attendance'
import {DEFAULT_SOURCES} from './fieldDefaults'

/**
 * What one field of the attendance sheet is filled in with before anybody is there.
 *
 * <p>The one row of its kind. Both the editor and the quick dialog show this, and each carried its
 * own copy with the names of the appointment's own properties written into it in German, so a
 * property renamed in one place kept its old name in the other and neither could be translated.
 */
const {t} = useI18n()

defineProps<{
  field: AttendanceTemplateField
  source: string
  value: string
}>()

const emit = defineEmits<{
  updateSource: [source: string]
  updateValue: [value: string]
}>()

/** The name of a kind of field as a station reads it, falling back to the bare value for a new one. */
function typeLabel(fieldType?: string): string {
  if (!fieldType) return ''
  const key = `attendanceConfig.fieldTypeLabels.${fieldType}`
  return t(key) === key ? fieldType : t(key)
}
</script>

<template>
  <div data-testid="field-default-row" class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
    <div class="text-sm font-medium">
      {{ field.name }} <span class="text-xs text-(--text-muted)">({{ typeLabel(field.fieldType) }})</span>
    </div>
    <div class="grid gap-2 sm:grid-cols-2">
      <SelectInput
          :model-value="source"
          @update:model-value="emit('updateSource', ($event as string) ?? '')"
      >
        <option value="">{{ t('events.noDefault') }}</option>
        <option value="VALUE">{{ t('events.staticValue') }}</option>
        <option v-for="src in DEFAULT_SOURCES" :key="src" :value="src">
          {{ t(`events.defaultSources.${src}`) }}
        </option>
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
