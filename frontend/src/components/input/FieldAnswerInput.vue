/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import QuestionValueInput from './QuestionValueInput.vue'
import type {MemberOption} from './select/memberOption'
import {QuestionKinds, questionKindOf} from '@/util/questions'

/**
 * The box a field of any feature is answered in, picked by the field's type.
 *
 * <p>Every feature turned its own type into the kind of box by hand, and the waiting list alone did
 * it in four wrappers that differed in nothing but their names. A number takes a fraction only where
 * its step is below one, the same rule the server checks it by.
 */
const value = defineModel<string>({required: true})

const props = withDefaults(defineProps<{
  fieldType: string
  /** The answers a choice offers, as plain words or as a value and a label. */
  options?: (string | {value: string; label: string})[]
  /** Whom a member field may name. */
  members?: MemberOption[]
  min?: number | null
  max?: number | null
  /** What a number steps by; below one it takes a fraction. */
  step?: number | null
  maxLength?: number
  disabled?: boolean
  required?: boolean
  placeholder?: string
}>(), {
  options: () => [],
  members: () => [],
  min: undefined,
  max: undefined,
  step: undefined,
  maxLength: undefined,
  placeholder: undefined,
})

const kind = computed(() => questionKindOf(props.fieldType, props.step) ?? QuestionKinds.TEXT)
</script>

<template>
  <QuestionValueInput
      v-model="value"
      :disabled="disabled"
      :kind="kind"
      :max="max ?? undefined"
      :max-length="maxLength"
      :members="members"
      :min="min ?? undefined"
      :options="options"
      :placeholder="placeholder"
      :required="required"
      :step="step ?? 1"
  />
</template>
