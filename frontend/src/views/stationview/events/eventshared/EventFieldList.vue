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
import EventFieldEditor from './EventFieldEditor.vue'
import AttendanceFieldPicker from './AttendanceFieldPicker.vue'
import DragList from '@/components/input/DragList.vue'
import FieldLayoutPreview from '@/components/profilefields/FieldLayoutPreview.vue'
import {configOf} from '@/components/profilefields/fieldLayout'
import {moveWithin} from '@/util/reorder'
import type {AttendanceTemplateField} from '@/api/attendance'
import type {EventFieldEntry} from '@/api/events'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

const fields = defineModel<EventFieldEntry[]>('fields', {required: true})

const props = defineProps<{
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  valueLabel?: string
  allMembers?: StationMember[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, StationMember[]>
  tags?: UserTag[]
  tagMembers?: Map<number, StationMember[]>
}>()

const {t} = useI18n()

const quickFields = [
  {name: 'Ort', fieldType: 'STRING', overview: true, isPublic: true},
  {name: 'Treffpunkt', fieldType: 'STRING', overview: true, isPublic: true},
  {name: 'Thema', fieldType: 'STRING', overview: true, isPublic: false},
]

const existingNames = computed(() => new Set(fields.value.map(f => f.name.toLowerCase())))

function addQuickField(qf: typeof quickFields[number]) {
  fields.value = [...fields.value, {
    name: qf.name, fieldType: qf.fieldType, config: {}, value: '', overview: qf.overview, attendanceFieldId: null, isPublic: qf.isPublic,
  }]
}

function addField() {
  fields.value = [...fields.value, {name: '', fieldType: 'STRING', config: {}, value: '', overview: false, attendanceFieldId: null}]
}

/** The sheet fields the questions already fill in, so none of them is offered a second time. */
const takenAttendanceIds = computed(() =>
    fields.value.map(field => field.attendanceFieldId).filter((id): id is number => id != null))

/**
 * One question of the sheet, taken over as a question of the appointment.
 *
 * <p>Name, type and settings come across as they are, and the tie to the sheet is made here rather
 * than left to be picked from a dropdown afterwards. That tie is the whole point: the answer given
 * at the appointment is what fills the field in when the attendance is taken.
 */
function takeAttendanceField(field: AttendanceTemplateField) {
  fields.value = [...fields.value, {
    name: field.name ?? '',
    fieldType: field.fieldType ?? 'STRING',
    config: {...(field.config ?? {})},
    value: '',
    overview: false,
    attendanceFieldId: field.id,
  }]
}

function takeAllAttendanceFields() {
  const taken = new Set(takenAttendanceIds.value)
  for (const field of props.attendanceFields ?? []) {
    if (!taken.has(field.id)) takeAttendanceField(field)
  }
}

function removeField(index: number) {
  const updated = [...fields.value]
  updated.splice(index, 1)
  fields.value = updated
}

function updateField(index: number, field: EventFieldEntry) {
  const updated = [...fields.value]
  updated[index] = field
  fields.value = updated
}

/**
 * Puts a question in a different place in the order it is asked in.
 *
 * <p>A forgotten question used to mean deleting everything after it and typing it in again.
 */
function moveField(fromIndex: number, toIndex: number) {
  fields.value = moveWithin(fields.value, fromIndex, toIndex)
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
  <AttendanceFieldPicker
      v-if="attendanceFields && attendanceFields.length > 0"
      :fields="attendanceFields"
      :taken-ids="takenAttendanceIds"
      @take="takeAttendanceField"
      @take-all="takeAllAttendanceFields"
  />

  <MutedText v-if="fields.length === 0" tag="div" size="sm" class="py-2">
    {{ t('events.noFields') }}
  </MutedText>

  <DragList :items="fields" :key-fn="(_, index) => index" @reorder="moveField">
    <template #default="{index}">
      <EventFieldEditor
          :model-value="fields[index]!"
          :attendance-fields="attendanceFields"
          :show-value="showValue"
          :value-label="valueLabel"
          :all-members="allMembers"
          :groups="groups"
          :group-members="groupMembers"
          :tags="tags"
          :tag-members="tagMembers"
          @update:model-value="updateField(index, $event)"
          @remove="removeField(index)"
      />
    </template>
  </DragList>

  <FieldLayoutPreview :fields="fields.map(field => ({name: field.name, width: configOf(field.config).width}))"/>
</template>
