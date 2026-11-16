/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, toRaw} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldRow from './EventFieldRow.vue'
import EventFieldAddButtons from './EventFieldAddButtons.vue'
import EventQuestionEditor from './EventQuestionEditor.vue'
import AttendanceFieldPicker from './AttendanceFieldPicker.vue'
import DragList from '@/components/input/DragList.vue'
import FieldLayoutPreview from '@/components/profilefields/FieldLayoutPreview.vue'
import type {FieldWidthName, PreviewField} from '@/components/profilefields/fieldLayout'
import {moveWithin} from '@/util/reorder'
import type {
  AttendanceTemplateField,
  FieldType,
  EventFieldEntry,
  MemberGroup,
  UserTag,
} from '@/api/generated/schema'
import {blankQuestion, emptySettings, type QuickField} from './eventQuestions'
import type {MemberLike} from '@/components/input/select/memberOption'

const fields = defineModel<EventFieldEntry[]>('fields', {required: true})

const props = defineProps<{
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  valueLabel?: string
  allMembers?: MemberLike[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, MemberLike[]>
  tags?: UserTag[]
  tagMembers?: Map<number, MemberLike[]>
  /** Whether the event repeats, which is what offers answering a field per date. */
  recurring?: boolean
  /** The kinds offered where fewer than an appointment's apply, such as on an event group. */
  offeredTypes?: readonly FieldType[]
  /** One line per field with its settings behind a button, and the ways to add one below the list. */
  compact?: boolean
}>()

const {t} = useI18n()

const existingNames = computed(() => new Set(fields.value.map(f => (f.name ?? '').toLowerCase())))

function addQuickField(qf: QuickField) {
  fields.value = [...fields.value, {...blankQuestion(), name: qf.name, overview: qf.overview, isPublic: qf.isPublic}]
}

function addField() {
  fields.value = [...fields.value, blankQuestion()]
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
    name: field.name,
    fieldType: field.fieldType,
    config: {
      ...emptySettings(),
      options: field.config.options ?? undefined,
      groupId: field.config.groupId ?? undefined,
      width: field.config.width ?? undefined,
    },
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

/**
 * A key per field that stays with it while it is edited, moved or a field before it is removed.
 *
 * <p>Every change hands back a new copy of the field, so the copy inherits the key of the one it
 * replaces. The keys are kept by the plain object, since the list hands out reactive copies of it. A line keyed by its place instead kept its opened settings at that place, and showed
 * them on whatever field moved there.
 */
const keys = new WeakMap<EventFieldEntry, number>()
let nextKey = 0

function keyOf(field: EventFieldEntry): number {
  const raw = toRaw(field)
  let key = keys.get(raw)
  if (key === undefined) {
    key = nextKey++
    keys.set(raw, key)
  }
  return key
}

function updateField(index: number, field: EventFieldEntry) {
  const replaced = fields.value[index]
  if (replaced) keys.set(toRaw(field), keyOf(replaced))
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

/** The questions as the preview of the form draws them, in the order they are asked. */
const previewFields = computed<PreviewField[]>(() => fields.value.map(field => ({
  name: field.name ?? '',
  fieldType: field.fieldType ?? undefined,
  required: field.config?.required ?? false,
  width: field.config?.width ?? null,
})))

/** Takes the width a question was dragged to in the preview into its settings. */
function resizeField(shown: PreviewField, width: FieldWidthName) {
  const index = previewFields.value.indexOf(shown)
  const field = fields.value[index]
  if (!field) return
  updateField(index, {...field, config: {...(field.config ?? emptySettings()), width}})
}
</script>

<template>
  <div class="flex items-center justify-between">
    <SubHeader>{{ t('events.eventFields') }}</SubHeader>
    <ButtonRow v-if="!compact" align="end">
      <EventFieldAddButtons :existing-names="existingNames" @add="addField" @add-quick="addQuickField"/>
    </ButtonRow>
  </div>
  <AttendanceFieldPicker
      v-if="!compact && attendanceFields && attendanceFields.length > 0"
      :fields="attendanceFields"
      :taken-ids="takenAttendanceIds"
      @take="takeAttendanceField"
      @take-all="takeAllAttendanceFields"
  />

  <MutedText v-if="fields.length === 0" tag="div" size="sm" class="py-2">
    {{ t('events.noFields') }}
  </MutedText>

  <div data-testid="event-field-list" :class="compact ? 'space-y-2' : ''">
    <DragList :items="fields" :key-fn="keyOf" @reorder="moveField">
    <template #default="{index}">
      <EventFieldRow
          v-if="compact"
          :offered-types="offeredTypes"
          :model-value="fields[index]!"
          :attendance-fields="attendanceFields"
          :show-value="showValue"
          :value-label="valueLabel"
          :all-members="allMembers"
          :groups="groups"
          :group-members="groupMembers"
          :tags="tags"
          :tag-members="tagMembers"
          :recurring="recurring"
          @update:model-value="updateField(index, $event)"
          @remove="removeField(index)"
      />
      <EventQuestionEditor
          v-else
          mode="organiser"
          :offered-types="offeredTypes"
          :model-value="fields[index]!"
          :attendance-fields="attendanceFields"
          :show-value="showValue"
          :value-label="valueLabel"
          :all-members="allMembers"
          :groups="groups"
          :group-members="groupMembers"
          :tags="tags"
          :tag-members="tagMembers"
          :recurring="recurring"
          @update:model-value="updateField(index, $event)"
          @remove="removeField(index)"
      />
    </template>
    </DragList>
  </div>

  <FieldLayoutPreview :fields="previewFields" :hint="t('events.fieldsPreviewHint')" @reorder="moveField" @resize="resizeField"/>

  <div v-if="compact" class="flex flex-wrap items-center gap-2" data-testid="event-field-add-line">
    <EventFieldAddButtons :existing-names="existingNames" @add="addField" @add-quick="addQuickField"/>
    <AttendanceFieldPicker
        v-if="attendanceFields && attendanceFields.length > 0"
        :fields="attendanceFields"
        :taken-ids="takenAttendanceIds"
        inline
        @take="takeAttendanceField"
        @take-all="takeAllAttendanceFields"
    />
  </div>
</template>
