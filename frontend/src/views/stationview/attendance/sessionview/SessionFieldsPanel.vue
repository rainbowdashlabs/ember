/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectInput from '@/components/input/select/MultiSelectInput.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import type {AttendanceTemplateField} from '@/api/attendance'
import type {StationMember} from '@/api/types'

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

function isMemberField(fieldType: string): boolean {
  return ['MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'].includes(fieldType)
}

function isListField(fieldType: string): boolean {
  return ['MEMBER_LIST', 'MEMBER_LIST_OF_GROUP'].includes(fieldType)
}

function isImmediateField(fieldType: string): boolean {
  return ['BOOLEAN', 'DATE', 'ENUM', 'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'].includes(fieldType)
}

function getFieldValue(fieldId: number): string {
  return props.fieldValues.get(fieldId) ?? ''
}

function getFieldMemberIds(fieldId: number): string[] {
  const raw = getFieldValue(fieldId)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed.map(String)
    if (parsed) return [String(parsed)]
  } catch { /* ignore */ }
  if (raw) return [raw]
  return []
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
    <div class="space-y-3">
      <div v-for="field in templateFields" :key="field.id" class="space-y-1">
        <FieldLabel>{{ field.name }}</FieldLabel>
        <template v-if="!readonly">
          <!-- Member list fields -->
          <template v-if="isMemberField(field.fieldType ?? '')">
            <MultiSelectInput
                v-if="isListField(field.fieldType ?? '')"
                :model-value="getFieldMemberIds(field.id)"
                :options="getMemberOptions(field)"
                :placeholder="t('attendanceSession.addMember')"
                @update:model-value="emit('fieldMemberIds', field.id, $event)"
            />
            <SelectInput
                v-else
                class="w-full"
                :model-value="getFieldValue(field.id)"
                @update:model-value="emit('fieldMemberIds', field.id, $event ? [String($event)] : [])"
            >
              <option value="">—</option>
              <option v-for="opt in getMemberOptions(field)" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </SelectInput>
          </template>
          <!-- Regular fields -->
          <template v-else>
            <ProfileFieldInput
                :field-type="field.fieldType ?? 'TEXT'"
                :model-value="getFieldValue(field.id)"
                :options="(parseFieldConfig(field.config).options as string[]) ?? []"
                @update:model-value="emit('fieldUpdate', field.id, $event, isImmediateField(field.fieldType ?? ''))"
            />
          </template>
        </template>
        <!-- Read-only display -->
        <template v-else>
          <span v-if="isMemberField(field.fieldType ?? '')" class="text-sm">
            {{ getFieldMemberIds(field.id).map(id => getMemberOptions(field).find(o => o.value === id)?.label ?? id).join(', ') || '—' }}
          </span>
          <span v-else class="text-sm">{{ getFieldValue(field.id) || '—' }}</span>
        </template>
      </div>
    </div>
  </NeutralContainer>
</template>
