/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {configOf, spanForWidth} from '@/components/profilefields/fieldLayout'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import QuestionValueInput from '@/components/input/QuestionValueInput.vue'
import type {AttendanceTemplateField} from '@/api/attendance'
import type {StationMember} from '@/api/types'
import {QuestionKinds, memberIdsOf, questionKindOf, type QuestionKindName} from '@/util/questions'

const {t} = useI18n()

const props = defineProps<{
  templateFields: AttendanceTemplateField[]
  fieldValues: Map<number, string>
  groupMembers: Map<number, StationMember[]>
  allMembers: StationMember[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  fieldUpdate: [fieldId: number, value: string, immediate: boolean]
  fieldMemberIds: [fieldId: number, ids: string[]]
}>()

function parseFieldConfig(config?: Record<string, unknown>): { options?: string[]; groupId?: number; autoAttend?: boolean } {
  return (config ?? {}) as { options?: string[]; groupId?: number; autoAttend?: boolean }
}

/** What kind of answer a field takes, which is what decides the box it is answered in. */
function kindOf(field: AttendanceTemplateField): QuestionKindName {
  return questionKindOf(field.fieldType) ?? QuestionKinds.TEXT
}

function isMemberField(fieldType: string): boolean {
  const kind = questionKindOf(fieldType)
  return kind === QuestionKinds.MEMBER || kind === QuestionKinds.MEMBER_LIST
}

function isImmediateField(fieldType: string): boolean {
  return ['BOOLEAN', 'DATE', 'ENUM', 'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'].includes(fieldType)
}

function getFieldValue(fieldId: number): string {
  return props.fieldValues.get(fieldId) ?? ''
}

function getFieldMemberIds(fieldId: number): string[] {
  return memberIdsOf(getFieldValue(fieldId))
}

/**
 * Writes what was answered.
 *
 * <p>A field naming members goes the other way about it, because naming somebody on a sheet also
 * puts them on it where the field attends by itself: the ids are what that needs, and they are read
 * back out of the answer.
 */
function writeField(field: AttendanceTemplateField, value: string) {
  if (isMemberField(field.fieldType ?? '')) {
    emit('fieldMemberIds', field.id, memberIdsOf(value))
    return
  }
  emit('fieldUpdate', field.id, value, isImmediateField(field.fieldType ?? ''))
}

function getMemberOptions(field: AttendanceTemplateField): { value: string; label: string }[] {
  const config = parseFieldConfig(field.config)
  const groupId = config.groupId
  let members: StationMember[]
  if (groupId && props.groupMembers.has(groupId)) {
    members = props.groupMembers.get(groupId)!
  } else {
    members = props.allMembers
  }
  return members.map(m => ({value: String(m.id), label: m.name ?? m.email ?? `#${m.id}`}))
}
</script>

<template>
  <NeutralContainer v-if="templateFields.length > 0" class="space-y-4">
    <SectionHeader>{{ t('attendanceSession.fields') }}</SectionHeader>
    <div class="grid grid-cols-6 gap-3">
      <div v-for="field in templateFields" :key="field.id" :class="['space-y-1', spanForWidth(configOf(field.config).width)]">
        <FieldLabel>{{ field.name }}</FieldLabel>
        <template v-if="!readonly">
          <QuestionValueInput
              :kind="kindOf(field)"
              :members="getMemberOptions(field)"
              :model-value="getFieldValue(field.id)"
              :options="(parseFieldConfig(field.config).options as string[]) ?? []"
              :placeholder="isMemberField(field.fieldType ?? '') ? t('attendanceSession.addMember') : undefined"
              @update:model-value="writeField(field, $event)"
          />
        </template>
        <!-- Read-only display -->
        <template v-else>
          <span v-if="isMemberField(field.fieldType ?? '')" class="text-sm">
            {{ getFieldMemberIds(field.id).map(id => getMemberOptions(field).find(o => o.value === id)?.label ?? id).join(', ') || '-' }}
          </span>
          <span v-else class="text-sm">{{ getFieldValue(field.id) || '-' }}</span>
        </template>
      </div>
    </div>
  </NeutralContainer>
</template>
