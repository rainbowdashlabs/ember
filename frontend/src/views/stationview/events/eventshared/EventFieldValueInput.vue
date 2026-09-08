/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import QuestionValueInput from '@/components/input/QuestionValueInput.vue'
import type {StationMember} from '@/api/types'
import {EventFieldTypes} from '@/api/events'
import {QuestionKinds, questionKindOf, type QuestionKindName} from '@/util/questions'

/**
 * Answering a question an appointment asks.
 *
 * <p>The box itself is the one every feature uses. What stays here is the half that is the
 * appointment's own: which members a question may name. An appointment can narrow that to a group,
 * to a kind of member or to a tag, and nothing else in Ember does, so the shared box is handed the
 * people rather than taught the rules.
 */
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

const config = computed<FieldConfig>(() => (props.config ?? {}) as FieldConfig)

const kind = computed<QuestionKindName>(() => questionKindOf(props.fieldType, true) ?? QuestionKinds.TEXT)

/** Who the question may name, narrowed the way the appointment narrowed it. */
const memberOptions = computed(() => {
  const narrowed = narrowedMembers()
  return narrowed.map(member => ({
    value: String(member.id),
    label: member.name ?? member.email ?? `#${member.id}`,
  }))
})

function narrowedMembers(): StationMember[] {
  const all = props.allMembers ?? []
  const {groupId, userType, tagId} = config.value
  switch (props.fieldType) {
    case EventFieldTypes.MEMBER_OF_GROUP:
    case EventFieldTypes.MEMBER_LIST_OF_GROUP:
      return groupId && props.groupMembers?.has(groupId) ? props.groupMembers.get(groupId)! : all
    case EventFieldTypes.MEMBER_OF_TYPE:
    case EventFieldTypes.MEMBER_LIST_OF_TYPE:
      return userType ? all.filter(member => member.userType === userType) : all
    case EventFieldTypes.MEMBER_OF_TAG:
    case EventFieldTypes.MEMBER_LIST_OF_TAG:
      return tagId && props.tagMembers?.has(tagId) ? props.tagMembers.get(tagId)! : all
    default:
      return all
  }
}
</script>

<template>
  <QuestionValueInput
      v-model="modelValue"
      :disabled="disabled"
      :kind="kind"
      :members="memberOptions"
      :options="config.options ?? []"
  />
</template>
