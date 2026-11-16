/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {FieldType, type AttendanceTemplateField, type EventFieldEntry, type MemberGroup, type UserTag} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import CompactQuestionRow from '@/components/input/questionsettings/CompactQuestionRow.vue'
import {fieldTypeLabel} from '@/api/fieldTypes'
import EventFieldMarks from './EventFieldMarks.vue'
import EventFieldValueInput from './EventFieldValueInput.vue'
import EventQuestionEditor from './EventQuestionEditor.vue'
import {offersFieldValue} from './eventQuestions'

/**
 * One field of an appointment as a single line: its name, its value, its type and whether it shows in
 * the overview, to readers outside and on the attendance sheet. The rest of its card opens on
 * request; a field without a name yet opens straight away, since it has to be set up first.
 */
const field = defineModel<EventFieldEntry>({required: true})

const props = defineProps<{
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  valueLabel?: string
  allMembers?: MemberLike[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, MemberLike[]>
  tags?: UserTag[]
  tagMembers?: Map<number, MemberLike[]>
  recurring?: boolean
  offeredTypes?: readonly FieldType[]
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

const open = ref((field.value.name ?? '').trim() === '')

const fieldType = computed(() => field.value.fieldType ?? FieldType.TEXT)
const offersValue = computed(() =>
    offersFieldValue(field.value, Boolean(props.showValue), Boolean(props.allMembers?.length)))
/** What the value box is read out as: the field's own name, or what a value is called here. */
const valueName = computed(() =>
    (field.value.name ?? '').trim() || props.valueLabel || t('eventFields.value'))
const attendanceName = computed(() =>
    props.attendanceFields?.find(candidate => candidate.id === field.value.attendanceFieldId)?.name)

function update(patch: Partial<EventFieldEntry>) {
  field.value = {...field.value, ...patch}
}
</script>

<template>
  <CompactQuestionRow
      v-model:open="open"
      :name="field.name ?? ''"
      :name-label="t('eventFields.name')"
      :type-label="fieldTypeLabel(t, fieldType)"
      @update:name="name => update({name})"
      @remove="emit('remove')"
  >
    <template #value>
      <div class="min-w-0 grow-[2] basis-48" :role="offersValue ? 'group' : undefined"
           :aria-label="offersValue ? valueName : undefined">
        <EventFieldValueInput
            v-if="offersValue"
            :aria-label="valueName"
            :all-members="allMembers"
            :config="field.config"
            :field-type="fieldType"
            :group-members="groupMembers"
            :model-value="field.value ?? ''"
            :tag-members="tagMembers"
            data-testid="event-field-row-value"
            @update:model-value="value => update({value})"
        />
      </div>
    </template>
    <template #marks>
      <EventFieldMarks
          :overview="field.overview ?? false"
          :is-public="field.isPublic ?? false"
          :attendance-name="attendanceName"
          @update:overview="overview => update({overview})"
          @update:is-public="isPublic => update({isPublic})"
      />
    </template>

    <EventQuestionEditor
        v-model="field"
        mode="organiser"
        :offered-types="offeredTypes"
        :attendance-fields="attendanceFields"
        :show-value="showValue"
        :value-label="valueLabel"
        :all-members="allMembers"
        :groups="groups"
        :group-members="groupMembers"
        :tags="tags"
        :tag-members="tagMembers"
        :recurring="recurring"
        @remove="emit('remove')"
    />
  </CompactQuestionRow>
</template>
