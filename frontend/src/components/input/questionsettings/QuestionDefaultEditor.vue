/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldAnswerInput from '@/components/input/FieldAnswerInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {MemberOption} from '@/components/input/select/memberOption'
import {isDateType} from '@/api/fieldTypes'
import {FieldType} from '@/api/generated/schema'
import {TODAY} from './questionSettings'

/**
 * What a field starts from, written in the box its own answer is written in.
 *
 * <p>Two settings screens each had a section of their own for this, with one branch per type: a
 * toggle for a yes, a number box for a number, a select for a choice and a line of text for the
 * rest. The box an answer is written in already knows all of that, so the starting value is written
 * in it too, and a choice offers its own choices rather than a line to type into.
 *
 * <p>Where a feature starts a date from today or not at all, the switch says exactly that.
 */
const defaultValue = defineModel<string | null>({required: true})

const props = defineProps<{
  fieldType: string
  options?: string[]
  /** Whether a date starts from the day the form is opened rather than from a day chosen here. */
  todayDefault?: boolean
  members?: MemberOption[]
  min?: number | null
  max?: number | null
  step?: number | null
  /** A line under the box, where a feature says when the starting value is used. */
  hint?: string
}>()

const {t} = useI18n()
const toggleId = useId()

const startsToday = computed(() => props.todayDefault && isDateType(props.fieldType))

const hasDefault = computed({
  get: () => defaultValue.value !== null && defaultValue.value !== undefined,
  set: (on: boolean) => {
    if (!on) defaultValue.value = null
    else if (startsToday.value) defaultValue.value = TODAY
    else defaultValue.value = props.fieldType === FieldType.BOOLEAN ? 'false' : ''
  },
})

const text = computed({
  get: () => defaultValue.value ?? '',
  set: (next: string) => {
    defaultValue.value = next
  },
})
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between gap-2">
      <span :id="toggleId" class="text-sm font-medium">
        {{ startsToday ? t('questionSettings.defaultToday') : t('questionSettings.hasDefault') }}
      </span>
      <ToggleInput v-model="hasDefault" :aria-labelledby="toggleId" data-testid="question-has-default"/>
    </div>
    <FieldAnswerInput
        v-if="hasDefault && !startsToday"
        v-model="text"
        :field-type="fieldType"
        :max="max"
        :members="members"
        :min="min"
        :options="options"
        :step="step"
        data-testid="question-default"
    />
    <MutedText v-if="hint" size="sm" tag="p">{{ hint }}</MutedText>
  </div>
</template>
