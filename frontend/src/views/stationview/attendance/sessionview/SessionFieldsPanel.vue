/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {configOf, spanForWidth} from '@/components/profilefields/fieldLayout'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldAnswerInput from '@/components/input/FieldAnswerInput.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import {fromMember, type MemberOption} from '@/components/input/select/memberOption'
import {FieldTypes, isDateType, namesMembers} from '@/api/fieldTypes'
import type {AttendanceTemplateField, MemberWithName} from '@/api/generated/schema'
import {memberIdsOf} from '@/util/questions'

const {t} = useI18n()

const props = defineProps<{
  templateFields: AttendanceTemplateField[]
  fieldValues: Map<number, string>
  groupMembers: Map<number, MemberWithName[]>
  allMembers: MemberWithName[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  fieldUpdate: [fieldId: number, value: string, immediate: boolean]
  fieldMemberIds: [fieldId: number, ids: string[]]
}>()

/** Everybody the sheet could name, by id, so a field read back names people rather than numbers. */
const memberNames = computed(() => new Map(props.allMembers.map(member => [member.id, member.name])))

/**
 * Whether an answer is saved the moment it is given. A choice made by a click is complete then,
 * where a typed one is saved once the typing stops.
 */
function isImmediate(fieldType: string): boolean {
  return fieldType === FieldTypes.BOOLEAN || fieldType === FieldTypes.CHOICE || isDateType(fieldType)
}

function getFieldValue(fieldId: number): string {
  return props.fieldValues.get(fieldId) ?? ''
}

/**
 * Writes what was answered.
 *
 * <p>A field naming members goes the other way about it, because naming somebody on a sheet also
 * puts them on it where the field attends by itself: the ids are what that needs, and they are read
 * back out of the answer.
 */
function writeField(field: AttendanceTemplateField, value: string) {
  if (namesMembers(field.fieldType)) {
    emit('fieldMemberIds', field.id, memberIdsOf(value))
    return
  }
  emit('fieldUpdate', field.id, value, isImmediate(field.fieldType))
}

/** Whom a member field may name: the members of its group where it has one, everybody otherwise. */
function getMemberOptions(field: AttendanceTemplateField): MemberOption[] {
  const groupId = field.config.groupId
  const members = groupId != null ? props.groupMembers.get(groupId) ?? props.allMembers : props.allMembers
  return members.map(fromMember)
}
</script>

<template>
  <NeutralContainer v-if="templateFields.length > 0" class="space-y-4">
    <SectionHeader>{{ t('attendanceSession.fields') }}</SectionHeader>
    <div class="grid grid-cols-6 gap-3">
      <div v-for="field in templateFields" :key="field.id" :class="['space-y-1', spanForWidth(configOf(field.config).width)]">
        <FieldLabel>{{ field.name }}</FieldLabel>
        <FieldAnswerInput
            v-if="!readonly"
            :field-type="field.fieldType"
            :members="getMemberOptions(field)"
            :model-value="getFieldValue(field.id)"
            :options="field.config.options ?? []"
            :placeholder="namesMembers(field.fieldType) ? t('attendanceSession.addMember') : undefined"
            @update:model-value="writeField(field, $event)"
        />
        <span v-else class="text-sm">
          <QuestionValueDisplay :field-type="field.fieldType" :member-names="memberNames" :value="getFieldValue(field.id)"/>
        </span>
      </div>
    </div>
  </NeutralContainer>
</template>
