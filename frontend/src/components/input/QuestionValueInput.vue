/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from './text/TextInput.vue'
import TextAreaInput from './text/TextAreaInput.vue'
import NumberInput from './number/NumberInput.vue'
import DecimalInput from './number/DecimalInput.vue'
import DateInput from './datetime/DateInput.vue'
import TimeShortInput from './datetime/TimeShortInput.vue'
import SelectInput from './select/SelectInput.vue'
import SingleSelectDropdown from './select/SingleSelectDropdown.vue'
import MultiSelectDropdown from './select/MultiSelectDropdown.vue'
import ToggleInput from './toggle/ToggleInput.vue'
import {QuestionKinds, formatMemberIds, memberIdsOf, type QuestionKindName} from '@/util/questions'

/**
 * The box somebody answers a question in, whatever asks it.
 *
 * <p>Five features drew their own, and they disagreed in the small ways nobody chose: one had no
 * control for a time and fell back to a line of text, one had none for a long answer, two wrote the
 * member picker again, and each spelled the empty entry of a choice differently. What differs
 * between them is not the box, it is which members may be named, and that stays with the feature
 * that knows.
 *
 * <p>The answer is text, which is the same language the server measures it in. A feature that stores
 * something else turns it into text at its own edge rather than teaching this every shape.
 */
const value = defineModel<string>({required: true})

const props = withDefaults(defineProps<{
  kind: QuestionKindName
  /**
   * The answers a choice offers. Written as plain words where the answer is what is shown, or as a
   * value and a label where the two differ, which is how the equipment fields have always kept them.
   */
  options?: (string | {value: string; label: string})[]
  /** Whom this question may name, where it names members. Empty means nobody has been loaded yet. */
  members?: {value: string; label: string}[]
  min?: number
  max?: number
  /** What a number steps by, which is what tells a measurement from a count. */
  step?: number
  maxLength?: number
  disabled?: boolean
  /**
   * Whether an answer is expected. A choice that must be answered still offers its empty entry,
   * because a question never answered has to show as unanswered rather than as its first option,
   * but that entry cannot be chosen.
   */
  required?: boolean
  placeholder?: string
}>(), {step: 1})

const {t} = useI18n()

const Kinds = QuestionKinds

const asNumber = computed(() => (value.value === '' || value.value == null ? undefined : Number(value.value)))
const asBoolean = computed(() => value.value === 'true' || value.value === '1')

const emptyChoiceLabel = computed(() => (props.required ? t('questionValue.chooseOne') : '-'))

const choices = computed(() =>
    (props.options ?? []).map(option => (typeof option === 'string' ? {value: option, label: option} : option)))

function write(next: unknown) {
  value.value = next == null ? '' : String(next)
}
</script>

<template>
  <TextAreaInput
      v-if="kind === Kinds.LONG_TEXT"
      :disabled="disabled" :maxlength="maxLength" :model-value="value" :placeholder="placeholder" :rows="3"
      @update:model-value="write($event)"
  />

  <template v-else-if="kind === Kinds.NUMBER || kind === Kinds.DECIMAL">
    <DecimalInput
        v-if="kind === Kinds.DECIMAL && step < 1"
        :disabled="disabled" :max="max" :min="min" :model-value="asNumber"
        @update:model-value="write($event)"
    />
    <NumberInput
        v-else
        :disabled="disabled" :max="max" :min="min" :model-value="asNumber"
        @update:model-value="write($event)"
    />
  </template>

  <DateInput
      v-else-if="kind === Kinds.DATE"
      :disabled="disabled" :model-value="value" @update:model-value="write($event)"
  />

  <TimeShortInput
      v-else-if="kind === Kinds.TIME"
      :disabled="disabled" :model-value="value" @update:model-value="write($event)"
  />

  <ToggleInput
      v-else-if="kind === Kinds.BOOLEAN"
      :disabled="disabled" :model-value="asBoolean" @update:model-value="write($event)"
  />

  <SelectInput
      v-else-if="kind === Kinds.CHOICE"
      :disabled="disabled" :model-value="value" class="w-full"
      @update:model-value="write($event)"
  >
    <option :disabled="required" value="">{{ emptyChoiceLabel }}</option>
    <option v-for="choice in choices" :key="choice.value" :value="choice.value">{{ choice.label }}</option>
  </SelectInput>

  <MultiSelectDropdown
      v-else-if="kind === Kinds.MEMBER_LIST"
      :model-value="memberIdsOf(value)"
      :options="members ?? []"
      :placeholder="placeholder ?? t('questionValue.chooseMember')"
      :searchable="true"
      @update:model-value="write(formatMemberIds($event))"
  />

  <SingleSelectDropdown
      v-else-if="kind === Kinds.MEMBER"
      :clearable="true" :disabled="disabled"
      :model-value="value"
      :options="members ?? []"
      :placeholder="placeholder ?? t('questionValue.chooseMember')"
      :searchable="true"
      @update:model-value="write($event)"
  />

  <TextInput
      v-else
      :disabled="disabled" :maxlength="maxLength" :model-value="value" :placeholder="placeholder"
      @update:model-value="write($event)"
  />
</template>
