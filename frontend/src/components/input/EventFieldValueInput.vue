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

const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  fieldType: string
  config?: Record<string, unknown>
  disabled?: boolean
  allMembers?: StationMember[]
  groupMembers?: Map<number, StationMember[]>
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
  <!-- Time -->
  <template v-if="fieldType === 'TIME'">
    <TimeShortInput :disabled="disabled" :model-value="modelValue"
                    @update:model-value="modelValue = $event ?? ''"/>
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
    <ProfileFieldInput v-model="modelValue" :disabled="disabled" :field-type="fieldType"
                       :options="parseConfig().options ?? []"/>
  </template>
</template>
