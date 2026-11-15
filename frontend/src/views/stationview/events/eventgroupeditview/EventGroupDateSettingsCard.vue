/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldDefaultRow from '../eventshared/FieldDefaultRow.vue'
import EventReminderEditor from '../eventshared/EventReminderEditor.vue'
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory} from '@/api/generated/schema'
import type {FieldDefaultValue} from '../eventeditview/useEventFieldDefaults'

/**
 * What an event group hands to every part without a setting of its own: the category, the attendance
 * template with the values it fills into the sheet, and the reminders. A part can replace each of them
 * in its own editor.
 */
const props = defineProps<{
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  /** The fields of the chosen attendance template, which the pre-fills are for. */
  templateFields: AttendanceTemplateField[]
  fieldDefaults: Map<number, FieldDefaultValue>
}>()

const emit = defineEmits<{
  'update:fieldDefaultSource': [fieldId: number, source: string]
  'update:fieldDefaultValue': [fieldId: number, value: string]
}>()

const categoryId = defineModel<string>('categoryId', {required: true})
const templateId = defineModel<string>('templateId', {required: true})
const reminders = defineModel<number[]>('reminders', {required: true})

const {t} = useI18n()

function entryFor(fieldId: number): FieldDefaultValue {
  return props.fieldDefaults.get(fieldId) ?? {source: '', value: ''}
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="event-group-date-settings">
    <SectionHeader>{{ t('eventGroups.dateSettings') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.dateSettingsHint') }}</MutedText>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('events.category') }}</FieldLabel>
        <SelectInput v-model="categoryId" class="w-full" data-testid="event-group-category">
          <option value="">-</option>
          <option v-for="cat in props.categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('events.template') }}</FieldLabel>
        <SelectInput v-model="templateId" class="w-full" data-testid="event-group-attendance-template">
          <option value="">-</option>
          <option v-for="tpl in props.templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
        </SelectInput>
      </div>
    </div>

    <div v-if="templateId && props.templateFields.length > 0" class="space-y-2" data-testid="event-group-field-defaults">
      <SubHeader class="text-sm">{{ t('events.fieldDefaults') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('events.fieldDefaultsHint') }}</MutedText>
      <FieldDefaultRow
          v-for="field in props.templateFields"
          :key="field.id"
          :field="field"
          :source="entryFor(field.id).source"
          :value="entryFor(field.id).value"
          @update-source="emit('update:fieldDefaultSource', field.id, $event)"
          @update-value="emit('update:fieldDefaultValue', field.id, $event)"
      />
    </div>

    <EventReminderEditor v-model="reminders"/>
  </NeutralContainer>
</template>
