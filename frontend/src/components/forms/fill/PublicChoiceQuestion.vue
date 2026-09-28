/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {PublicFormQuestion} from '@/api/publicForms'
import type {ChoiceAnswer, FormOption} from '@/api/forms'
import {optionsOf} from '@/util/formOptions'

const props = defineProps<{
  question: PublicFormQuestion
  answer: ChoiceAnswer
}>()

/** Reports the key of the option picked, or an empty key where the dropdown was cleared. */
const emit = defineEmits<{
  (e: 'toggle', optionKey: string): void
}>()

function options(): FormOption[] {
  return optionsOf(props.question.config)
}

function isSelected(optionKey: string): boolean {
  return props.answer?.selected?.includes(optionKey) ?? false
}

function onSelectChange(v: string | number | null | undefined) {
  emit('toggle', String(v ?? ''))
}

function onOptionClick(optionKey: string) {
  emit('toggle', optionKey)
}
</script>

<template>
  <div class="space-y-1">
    <template v-if="question.config.dropdown">
      <SelectInput
          :model-value="answer?.selected?.[0] ?? ''"
          @update:model-value="onSelectChange">
        <option value="">--</option>
        <option v-for="opt in options()" :key="opt.key" :value="opt.key">
          {{ opt.label }}
        </option>
      </SelectInput>
    </template>
    <template v-else>
      <div v-for="opt in options()"
           :key="opt.key"
           data-testid="choice-option"
           class="flex cursor-pointer items-center gap-2 rounded-lg border-2 px-4 py-3 text-sm font-medium transition-all"
           :class="isSelected(opt.key)
             ? 'border-primary bg-primary/10 text-primary'
             : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text) hover:border-primary/50'"
           @click="onOptionClick(opt.key)">
        <font-awesome-icon
            :icon="['fas', isSelected(opt.key)
              ? (question.config.multiSelect ? 'square-check' : 'circle-dot')
              : (question.config.multiSelect ? 'square' : 'circle')]"
            :class="isSelected(opt.key) ? 'text-primary' : 'text-(--text-muted)'"
            class="shrink-0"/>
        <span>{{ opt.label }}</span>
      </div>
    </template>
  </div>
</template>
