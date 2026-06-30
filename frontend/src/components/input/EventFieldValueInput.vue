/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import SingleSelectDropdown from '@/components/input/select/SingleSelectDropdown.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import type {StationMember} from '@/api/types'
import {EventFieldTypes} from '@/api/types'

const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  fieldType: string
  config?: Record<string, unknown>
  disabled?: boolean
  allMembers?: StationMember[]
  groupMembers?: Map<number, StationMember[]>
  tagMembers?: Map<number, StationMember[]>
}>()

type FieldConfig = {
  options?: string[]
  groupId?: number
  userType?: string
  tagId?: number
}

const MEMBER_FIELDS: string[] = [
  EventFieldTypes.MEMBER,
  EventFieldTypes.MEMBER_LIST,
  EventFieldTypes.MEMBER_OF_GROUP,
  EventFieldTypes.MEMBER_LIST_OF_GROUP,
  EventFieldTypes.MEMBER_OF_TYPE,
  EventFieldTypes.MEMBER_LIST_OF_TYPE,
  EventFieldTypes.MEMBER_OF_TAG,
  EventFieldTypes.MEMBER_LIST_OF_TAG,
]

const LIST_FIELDS: string[] = [
  EventFieldTypes.MEMBER_LIST,
  EventFieldTypes.MEMBER_LIST_OF_GROUP,
  EventFieldTypes.MEMBER_LIST_OF_TYPE,
  EventFieldTypes.MEMBER_LIST_OF_TAG,
]

function parseConfig(): FieldConfig {
  return (props.config ?? {}) as FieldConfig
}

function isMemberField(): boolean {
  return MEMBER_FIELDS.includes(props.fieldType)
}

function isListField(): boolean {
  return LIST_FIELDS.includes(props.fieldType)
}

function getMemberOptions(): { value: string; label: string }[] {
  const cfg = parseConfig()
  let members: StationMember[]
  switch (props.fieldType) {
    case EventFieldTypes.MEMBER_OF_GROUP:
    case EventFieldTypes.MEMBER_LIST_OF_GROUP:
      members = cfg.groupId && props.groupMembers?.has(cfg.groupId)
          ? props.groupMembers.get(cfg.groupId)!
          : (props.allMembers ?? [])
      break
    case EventFieldTypes.MEMBER_OF_TYPE:
    case EventFieldTypes.MEMBER_LIST_OF_TYPE:
      members = cfg.userType
          ? (props.allMembers ?? []).filter(m => m.userType === cfg.userType)
          : (props.allMembers ?? [])
      break
    case EventFieldTypes.MEMBER_OF_TAG:
    case EventFieldTypes.MEMBER_LIST_OF_TAG:
      members = cfg.tagId && props.tagMembers?.has(cfg.tagId)
          ? props.tagMembers.get(cfg.tagId)!
          : (props.allMembers ?? [])
      break
    default:
      members = props.allMembers ?? []
  }
  return members.map(m => ({value: String(m.id), label: m.name ?? m.email ?? `#${m.id}`}))
}

function getMemberIds(): string[] {
  if (!modelValue.value) return []
  try {
    const parsed = JSON.parse(modelValue.value)
    if (Array.isArray(parsed)) return parsed.map(String)
    if (parsed) return [String(parsed)]
  } catch { /* ignore */ }
  if (modelValue.value) return [modelValue.value]
  return []
}

function setMemberIds(ids: string[]) {
  modelValue.value = JSON.stringify(ids.map(Number))
}

function setSingleMember(id: string) {
  modelValue.value = id || ''
}
</script>

<template>
  <template v-if="fieldType === 'TIME'">
    <TimeShortInput :disabled="disabled" :model-value="modelValue"
                    @update:model-value="modelValue = $event ?? ''"/>
  </template>

  <template v-else-if="isMemberField() && allMembers">
    <MultiSelectDropdown
        v-if="isListField()"
        :model-value="getMemberIds()"
        :options="getMemberOptions()"
        :searchable="true"
        placeholder="Mitglied wählen"
        @update:model-value="setMemberIds($event)"
    />
    <SingleSelectDropdown
        v-else
        :disabled="disabled"
        :model-value="modelValue"
        :options="getMemberOptions()"
        :searchable="true"
        :clearable="true"
        placeholder="Mitglied wählen"
        @update:model-value="setSingleMember($event)"
    />
  </template>

  <template v-else>
    <ProfileFieldInput v-model="modelValue" :disabled="disabled" :field-type="fieldType"
                       :options="parseConfig().options ?? []"/>
  </template>
</template>
