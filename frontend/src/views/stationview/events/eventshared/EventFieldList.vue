/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldEditor from '@/components/input/EventFieldEditor.vue'
import type {AttendanceTemplateField, EventFieldEntry, StationMember} from '@/api/types'

const props = defineProps<{
  fields: EventFieldEntry[]
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  allMembers?: StationMember[]
  groupMembers?: Map<number, StationMember[]>
}>()

const emit = defineEmits<{
  'update:fields': [fields: EventFieldEntry[]]
}>()

const {t} = useI18n()

const quickFields = [
  {name: 'Ort', fieldType: 'STRING', overview: true, isPublic: true},
  {name: 'Treffpunkt', fieldType: 'STRING', overview: true, isPublic: true},
  {name: 'Thema', fieldType: 'STRING', overview: true, isPublic: false},
]

const existingNames = computed(() => new Set(props.fields.map(f => f.name.toLowerCase())))

function addQuickField(qf: typeof quickFields[number]) {
  emit('update:fields', [...props.fields, {
    name: qf.name, fieldType: qf.fieldType, config: '{}', value: '', overview: qf.overview, attendanceFieldId: null, isPublic: qf.isPublic,
  }])
}

function addField() {
  emit('update:fields', [...props.fields, {name: '', fieldType: 'STRING', config: '{}', value: '', overview: false, attendanceFieldId: null}])
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
    <div class="flex items-center gap-2">
      <SecondaryButton
          v-for="qf in quickFields"
          :key="qf.name"
          :disabled="existingNames.has(qf.name.toLowerCase())"
          class="!py-1 !px-2 !text-xs"
          @click="addQuickField(qf)"
      >
        + {{ qf.name }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" @click="addField">
        {{ t('eventFields.addField') }}
      </SecondaryButton>
    </div>
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
      :all-members="allMembers"
      :group-members="groupMembers"
      @update:model-value="updateField(index, $event)"
      @remove="removeField(index)"
  />
</template>
