/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script generic="T extends EventFieldEntry" lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import QuestionSettingsEditor from '@/components/input/questionsettings/QuestionSettingsEditor.vue'
import type {QuestionSettingsModel} from '@/components/input/questionsettings/questionSettings'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EventFieldValueInput from './EventFieldValueInput.vue'
import OrganiserQuestionSettings from './OrganiserQuestionSettings.vue'
import RegistrantQuestionSettings from './RegistrantQuestionSettings.vue'
import {namesMembers, OfferedFieldTypes} from '@/api/fieldTypes'
import {
  FieldType, type AttendanceTemplateField, type EventFieldEntry, type MemberGroup, type UserTag,
} from '@/api/generated/schema'
import {fromMember, type MemberLike} from '@/components/input/select/memberOption'
import {SHARED_SETTINGS, settingsModelOf, withSettingsModel, type EventQuestionMode} from './eventQuestions'

/**
 * One question of an appointment, whoever answers it.
 *
 * <p>The organiser's question and the registrant's question had an editor each, with their own type
 * list and their own way of writing the same choices. They share the name, the type and the settings
 * every field has, and differ only in what is theirs alone: the organiser's carries an answer, a width,
 * a tie to the attendance sheet and whether it is public; the registrant's may be required, start from
 * a value and keep its answer for the organisers. The mode says which of those this one shows.
 *
 * <p>The question is edited in place: every change hands back the whole question, so a question
 * moved to another row brings its form with it.
 */
const question = defineModel<T>({required: true})

const props = defineProps<{
  mode: EventQuestionMode
  attendanceFields?: AttendanceTemplateField[]
  /** Whether the organiser's question shows the answer it carries, or a template its starting value. */
  showValue?: boolean
  valueLabel?: string
  allMembers?: MemberLike[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, MemberLike[]>
  tags?: UserTag[]
  tagMembers?: Map<number, MemberLike[]>
  /** Whether the event repeats, which is what offers answering a field per date. */
  recurring?: boolean
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

const organiser = computed(() => props.mode === 'organiser')
const fieldType = computed<FieldType>(() => question.value.fieldType ?? FieldType.TEXT)
const offered = computed(() => (organiser.value ? OfferedFieldTypes.APPOINTMENT : OfferedFieldTypes.REGISTRATION))
const settings = computed(() => settingsModelOf(question.value.config))
const members = computed(() => (props.allMembers ?? []).map(fromMember))
const nameLabel = computed(() =>
    (organiser.value ? t('eventFields.name') : t('events.registrationFields.fieldName')))
const namePlaceholder = computed(() =>
    (organiser.value ? t('eventFields.namePlaceholder') : t('events.registrationFields.fieldNamePlaceholder')))

/**
 * Whether this row offers a value at all.
 *
 * <p>A question that names a member can only be answered where the members are known. The template
 * editor knows the questions but not who is in the station, and a person is not something a template
 * should answer with anyway, so such a question is left without one there.
 */
const offersValue = computed(() => organiser.value && Boolean(props.showValue)
    && (question.value.name ?? '').trim() !== ''
    && (!namesMembers(fieldType.value) || Boolean(props.allMembers?.length)))

function update(patch: Partial<EventFieldEntry>) {
  question.value = {...question.value, ...patch}
}

function updateSettings(model: QuestionSettingsModel) {
  update({config: withSettingsModel(question.value.config, model)})
}
</script>

<template>
  <div class="rounded border border-(--border) p-3 space-y-3">
    <div class="grid grid-cols-1 sm:grid-cols-[1fr_auto_auto] gap-2 items-end">
      <LabelledField :label="nameLabel">
        <TextInput :model-value="question.name ?? ''" :placeholder="namePlaceholder" data-testid="event-field-name"
                   @update:model-value="name => update({name: String(name ?? '')})"/>
      </LabelledField>
      <LabelledField :label="t('fieldTypes.type')" class="sm:w-56">
        <FieldTypePicker :model-value="fieldType" :types="offered" data-testid="event-field-type"
                         @update:model-value="type => update({fieldType: type})"/>
      </LabelledField>
      <DeleteButton class="justify-self-end" :label="t('common.delete')" @click="emit('remove')"/>
    </div>

    <QuestionSettingsEditor
        :default-hint="t('events.registrationFields.defaultValueHint')"
        :field-type="fieldType"
        :groups="groups"
        :members="members"
        :model-value="settings"
        :offers="SHARED_SETTINGS[mode]"
        :tags="tags"
        @update:model-value="updateSettings"
    />

    <OrganiserQuestionSettings
        v-if="organiser"
        :attendance-fields="attendanceFields"
        :model-value="question"
        :recurring="recurring"
        @update:model-value="next => question = next as T"
    />
    <RegistrantQuestionSettings v-else :model-value="question" @update:model-value="next => question = next as T"/>

    <LabelledField v-if="offersValue" :label="valueLabel ?? t('eventFields.value')" data-testid="event-field-value">
      <EventFieldValueInput
          :all-members="allMembers"
          :config="question.config"
          :field-type="fieldType"
          :group-members="groupMembers"
          :model-value="question.value ?? ''"
          :tag-members="tagMembers"
          @update:model-value="value => update({value})"
      />
    </LabelledField>
  </div>
</template>
