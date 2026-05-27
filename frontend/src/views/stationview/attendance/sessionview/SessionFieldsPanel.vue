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
import type {AttendanceTemplateField, StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  templateFields: AttendanceTemplateField[]
  fieldValues: Map<number, string>
  groupMembers: Map<number, StationMember[]>
  allMembers: StationMember[]
}>()

const emit = defineEmits<{
  fieldUpdate: [fieldId: number, value: string, immediate: boolean]
  fieldMemberIds: [fieldId: number, ids: string[]]
}>()

function parseFieldConfig(configStr?: string): { options?: string[]; groupId?: number; autoAttend?: boolean } {
  if (!configStr) return {}
  try {
    return JSON.parse(configStr)
  } catch {
    return {}
  }
}

function isMemberField(fieldType: string): boolean {
  return ['member', 'member_list', 'member_of_group', 'member_list_of_group'].includes(fieldType)
}

function isListField(fieldType: string): boolean {
  return ['member_list', 'member_list_of_group'].includes(fieldType)
}

function isImmediateField(fieldType: string): boolean {
  return ['boolean', 'date', 'enum', 'member', 'member_list', 'member_of_group', 'member_list_of_group'].includes(fieldType)
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
              @update:model-value="emit('fieldMemberIds', field.id, $event ? [$event] : [])"
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
              :field-type="field.fieldType ?? 'text'"
              :model-value="getFieldValue(field.id)"
              :options="(parseFieldConfig(field.config).options as string[]) ?? []"
              @update:model-value="emit('fieldUpdate', field.id, $event, isImmediateField(field.fieldType ?? ''))"
          />
        </template>
      </div>
    </div>
  </NeutralContainer>
</template>
