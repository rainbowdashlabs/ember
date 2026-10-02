/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import FieldAnswerInput from '@/components/input/FieldAnswerInput.vue'
import {fromMember, type MemberLike} from '@/components/input/select/memberOption'
import {memberConstraintOf} from '@/api/fieldTypes'
import type {EventQuestionSettings} from '@/api/generated/schema'

/**
 * Answering a question an appointment asks.
 *
 * <p>The box itself is the one every feature uses, bounds and all. What stays here is the half that
 * is the appointment's own: which members a question may name. An appointment can narrow that to a
 * group, to a kind of member or to a tag, and the shared box is handed the people rather than taught
 * the rules.
 */
const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  fieldType: string
  config?: Partial<EventQuestionSettings> | null
  disabled?: boolean
  allMembers?: MemberLike[]
  groupMembers?: Map<number, MemberLike[]>
  tagMembers?: Map<number, MemberLike[]>
}>()

const settings = computed<Partial<EventQuestionSettings>>(() => props.config ?? {})

/** Who the question may name, narrowed the way the appointment narrowed it. */
const memberOptions = computed(() => narrowedMembers().map(fromMember))

function narrowedMembers(): MemberLike[] {
  const all = props.allMembers ?? []
  const {groupId, userType, tagId} = settings.value
  switch (memberConstraintOf(props.fieldType)) {
    case 'group':
      return (groupId != null && props.groupMembers?.get(groupId)) || all
    case 'userType':
      return userType ? all.filter(member => member.userType === userType) : all
    case 'tag':
      return (tagId != null && props.tagMembers?.get(tagId)) || all
    default:
      return all
  }
}
</script>

<template>
  <FieldAnswerInput
      v-model="modelValue"
      :disabled="disabled"
      :field-type="fieldType"
      :max="settings.max"
      :members="memberOptions"
      :min="settings.min"
      :options="settings.options ?? []"
      :required="settings.required"
  />
</template>
