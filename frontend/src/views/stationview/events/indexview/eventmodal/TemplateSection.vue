/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldDefaultRow from './FieldDefaultRow.vue'
import type {AttendanceTemplate, AttendanceTemplateField} from '@/api/attendance'

const {t} = useI18n()

defineProps<{
  templates: AttendanceTemplate[]
  currentTemplateFields: AttendanceTemplateField[]
  sources: { value: string; label: string }[]
  getDefault: (fieldId: number) => { source: string; value: string }
}>()

const eventTemplateId = defineModel<string>('eventTemplateId', {required: true})

const emit = defineEmits<{
  updateSource: [fieldId: number, source: string]
  updateValue: [fieldId: number, value: string]
}>()
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('events.template') }}</FieldLabel>
      <SelectInput v-model="eventTemplateId">
        <option value="">{{ t('events.noTemplate') }}</option>
        <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
      </SelectInput>
      <p class="text-xs text-(--text-muted)">{{ t('events.templateHint') }}</p>
    </div>

    <div v-if="currentTemplateFields.length > 0" class="space-y-3">
      <FieldLabel>{{ t('events.fieldDefaults') }}</FieldLabel>
      <p class="text-xs text-(--text-muted)">{{ t('events.fieldDefaultsHint') }}</p>
      <div class="space-y-2">
        <FieldDefaultRow
            v-for="field in currentTemplateFields"
            :key="field.id"
            :field="field"
            :source="getDefault(field.id).source"
            :value="getDefault(field.id).value"
            :sources="sources"
            @update-source="(s) => emit('updateSource', field.id, s)"
            @update-value="(v) => emit('updateValue', field.id, v)"
        />
      </div>
    </div>
  </div>
</template>
