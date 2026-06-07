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

const props = defineProps<{
  fieldType: string
  config?: Record<string, unknown>
  modelValue: string
  disabled?: boolean
  allMembers?: StationMember[]
  groupMembers?: Map<number, StationMember[]>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function parseConfig(): { options?: string[]; groupId?: number } {
  return (props.config ?? {}) as { options?: string[]; groupId?: number }
}

function isMemberField(): boolean {
  return ['MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'].includes(props.fieldType)
}

function isListField(): boolean {
  return ['MEMBER_LIST', 'MEMBER_LIST_OF_GROUP'].includes(props.fieldType)
}

function getMemberOptions(): { value: string; label: string }[] {
  const cfg = parseConfig()
  let members: StationMember[]
  if (cfg.groupId && props.groupMembers?.has(cfg.groupId)) {
    members = props.groupMembers.get(cfg.groupId)!
  } else {
    members = props.allMembers ?? []
  }
  return members.map(m => ({value: String(m.id), label: m.name ?? m.email ?? `#${m.id}`}))
}

function getMemberIds(): string[] {
  if (!props.modelValue) return []
  try {
    const parsed = JSON.parse(props.modelValue)
    if (Array.isArray(parsed)) return parsed.map(String)
    if (parsed) return [String(parsed)]
  } catch { /* ignore */ }
  if (props.modelValue) return [props.modelValue]
  return []
}

function setMemberIds(ids: string[]) {
  emit('update:modelValue', JSON.stringify(ids.map(Number)))
}

function setSingleMember(id: string) {
  emit('update:modelValue', id || '')
}
</script>

<template>
  <!-- Time -->
  <template v-if="fieldType === 'TIME'">
    <TimeShortInput :disabled="disabled" :model-value="modelValue"
                    @update:model-value="emit('update:modelValue', $event ?? '')"/>
  </template>

  <!-- Member list fields -->
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

  <!-- Regular fields -->
  <template v-else>
    <ProfileFieldInput :disabled="disabled" :field-type="fieldType" :model-value="modelValue"
                       :options="parseConfig().options ?? []"
                       @update:model-value="emit('update:modelValue', $event)"/>
  </template>
</template>
