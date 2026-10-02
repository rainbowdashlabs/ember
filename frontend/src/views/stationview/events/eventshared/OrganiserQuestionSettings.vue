/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import MutedText from '@/components/typography/MutedText.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import {FieldWidths} from '@/components/profilefields/fieldLayout'
import {namesMembers} from '@/api/fieldTypes'
import type {AttendanceTemplateField, EventFieldEntry, EventQuestionSettings} from '@/api/generated/schema'
import {emptySettings} from './eventQuestions'

/**
 * What only the organiser's question has: whether it shows in the overview and to readers outside,
 * which field of the attendance sheet its answer fills in, how wide it is drawn, and for a member
 * field whether members put themselves in and whether a series answers it per date.
 */
const question = defineModel<EventFieldEntry>({required: true})

const props = defineProps<{
  attendanceFields?: AttendanceTemplateField[]
  /** Whether the event repeats, which is the only case where an answer can be given per date. */
  recurring?: boolean
}>()

const {t} = useI18n()

const settings = computed(() => question.value.config ?? emptySettings())
const namesPeople = computed(() => namesMembers(question.value.fieldType))

const matchingAttendanceFields = computed(() =>
    (props.attendanceFields ?? []).filter(field => field.fieldType === question.value.fieldType))

function update(patch: Partial<EventFieldEntry>) {
  question.value = {...question.value, ...patch}
}

function updateSettings(patch: Partial<EventQuestionSettings>) {
  update({config: {...settings.value, ...patch}})
}

function widthOf(width: string): string | undefined {
  return width === FieldWidths.FULL ? undefined : width
}
</script>

<template>
  <div class="flex flex-wrap items-end gap-x-4 gap-y-2">
    <label class="flex items-center gap-2 pb-2 text-sm">
      <ToggleInput :model-value="question.overview ?? false" @update:model-value="overview => update({overview})"/>
      {{ t('eventFields.overview') }}
    </label>
    <label class="flex items-center gap-2 pb-2 text-sm">
      <ToggleInput :model-value="question.isPublic ?? false" @update:model-value="isPublic => update({isPublic})"/>
      {{ t('eventFields.public') }}
    </label>

    <LabelledField v-if="matchingAttendanceFields.length > 0" :label="t('eventFields.attendanceLink')" class="w-48">
      <SelectInput class="w-full" :model-value="String(question.attendanceFieldId ?? '')"
                   @update:model-value="id => update({attendanceFieldId: id ? Number(id) : null})">
        <option value="">-</option>
        <option v-for="field in matchingAttendanceFields" :key="field.id" :value="String(field.id)">{{ field.name }}</option>
      </SelectInput>
    </LabelledField>

    <div class="w-40">
      <WidthField :model-value="settings.width ?? FieldWidths.FULL"
                  @update:model-value="width => updateSettings({width: widthOf(width)})"/>
    </div>
  </div>

  <label v-if="namesPeople" class="flex items-center gap-2 text-sm">
    <ToggleInput :model-value="settings.selfRegistration"
                 @update:model-value="selfRegistration => updateSettings({selfRegistration})"/>
    {{ t('eventFields.selfRegistration') }}
  </label>

  <div v-if="namesPeople && recurring" class="space-y-1">
    <label class="flex items-center gap-2 text-sm">
      <ToggleInput :model-value="settings.perDate" @update:model-value="perDate => updateSettings({perDate})"/>
      {{ t('eventFields.perDate') }}
    </label>
    <MutedText size="sm" tag="p">{{ t('eventFields.perDateHint') }}</MutedText>
    <MutedText v-if="!settings.perDate" size="sm" tag="p">{{ t('eventFields.perDateKeptAnswers') }}</MutedText>
  </div>
</template>
