/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HintIcon from '@/components/typography/HintIcon.vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldDefaultsCard from '../eventeditview/FieldDefaultsCard.vue'
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
  /**
   * Inside the event group's form: the hint behind an icon by the category, category and template in
   * one row with whatever the form puts beside them, then what the form puts after that row before the
   * template's pre-fills, and the reminders left to the form, which asks for them further down.
   */
  compact?: boolean
}>()

const emit = defineEmits<{
  'update:fieldDefaultSource': [fieldId: number, source: string]
  'update:fieldDefaultValue': [fieldId: number, value: string]
}>()

const categoryId = defineModel<string>('categoryId', {required: true})
const templateId = defineModel<string>('templateId', {required: true})
const reminders = defineModel<number[]>('reminders', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="event-group-date-settings">
    <template v-if="!compact">
      <SectionHeader>{{ t('eventGroups.dateSettings') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('eventGroups.dateSettingsHint') }}</MutedText>
    </template>

    <div class="grid grid-cols-1 gap-3" :class="compact ? 'sm:grid-cols-3' : 'sm:grid-cols-2'">
      <div class="space-y-1">
        <div v-if="compact" class="flex items-center gap-1">
          <FieldLabel>{{ t('events.category') }}</FieldLabel>
          <HintIcon :text="t('eventGroups.dateSettingsHint')"/>
        </div>
        <FieldLabel v-else>{{ t('events.category') }}</FieldLabel>
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
      <slot name="row-end"/>
    </div>

    <slot name="after-row"/>

    <FieldDefaultsCard
        v-if="templateId && props.templateFields.length > 0"
        :fields="props.templateFields"
        :defaults="props.fieldDefaults"
        data-testid="event-group-field-defaults"
        @update:source="(fieldId, source) => emit('update:fieldDefaultSource', fieldId, source)"
        @update:value="(fieldId, value) => emit('update:fieldDefaultValue', fieldId, value)"
    />

    <EventReminderEditor v-if="!compact" v-model="reminders"/>
  </NeutralContainer>
</template>
