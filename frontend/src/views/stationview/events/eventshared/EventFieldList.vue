/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldEditor from '@/components/input/EventFieldEditor.vue'
import type {AttendanceTemplateField, EventFieldEntry} from '@/api/types'

const props = defineProps<{
  fields: EventFieldEntry[]
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
}>()

const emit = defineEmits<{
  'update:fields': [fields: EventFieldEntry[]]
}>()

const {t} = useI18n()

function addField() {
  emit('update:fields', [...props.fields, {name: '', fieldType: 'string', config: '{}', value: '', overview: false, attendanceFieldId: null}])
}

function removeField(index: number) {
  const updated = [...props.fields]
  updated.splice(index, 1)
  emit('update:fields', updated)
}

function updateField(index: number, field: EventFieldEntry) {
  const updated = [...props.fields]
  updated[index] = field
  emit('update:fields', updated)
}
</script>

<template>
  <div class="flex items-center justify-between">
    <SubHeader>{{ t('events.eventFields') }}</SubHeader>
    <SecondaryButton :icon="['fas', 'plus']" @click="addField">
      {{ t('eventFields.addField') }}
    </SecondaryButton>
  </div>
  <MutedText v-if="fields.length === 0" tag="div" size="sm" class="py-2">
    {{ t('events.noFields') }}
  </MutedText>
  <EventFieldEditor
      v-for="(field, index) in fields"
      :key="index"
      :model-value="field"
      :attendance-fields="attendanceFields"
      :show-value="showValue"
      @update:model-value="updateField(index, $event)"
      @remove="removeField(index)"
  />
</template>
