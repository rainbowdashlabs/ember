/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {PublicFormQuestion} from '@/api/publicForms'

interface ChoiceAnswer {
  selected: number[]
  other: string
}

const props = defineProps<{
  question: PublicFormQuestion
  answer: ChoiceAnswer
}>()

const emit = defineEmits<{
  (e: 'toggle', optionIndex: number): void
}>()

function options(): string[] {
  return (props.question.config.options as string[]) ?? []
}

function isSelected(optionIndex: number): boolean {
  return props.answer?.selected?.includes(optionIndex) ?? false
}

function onSelectChange(v: string | undefined) {
  emit('toggle', Number(v))
}

function onOptionClick(optionIndex: number) {
  emit('toggle', optionIndex)
}
</script>

<template>
  <div class="space-y-1">
    <template v-if="question.config.dropdown">
      <SelectInput
          :model-value="String(answer?.selected?.[0] ?? '')"
          @update:model-value="onSelectChange">
        <option value="">--</option>
        <option v-for="(opt, oi) in options()" :key="oi" :value="oi">
          {{ opt }}
        </option>
      </SelectInput>
    </template>
    <template v-else>
      <div v-for="(opt, oi) in options()"
           :key="oi"
           class="flex cursor-pointer items-center gap-2 rounded-lg border-2 px-4 py-3 text-sm font-medium transition-all"
           :class="isSelected(oi)
             ? 'border-primary bg-primary/10 text-primary'
             : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text) hover:border-primary/50'"
           @click="onOptionClick(oi)">
        <font-awesome-icon
            :icon="['fas', isSelected(oi)
              ? (question.config.multiSelect ? 'square-check' : 'circle-dot')
              : (question.config.multiSelect ? 'square' : 'circle')]"
            :class="isSelected(oi) ? 'text-primary' : 'text-(--text-muted)'"
            class="shrink-0"/>
        <span>{{ opt }}</span>
      </div>
    </template>
  </div>
</template>
