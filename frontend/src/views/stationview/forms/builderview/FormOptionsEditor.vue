/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import type { FormOption } from '@/api/forms'
import { blankOption } from '@/util/formOptions'

/**
 * The options of a form question, or the statements of a Likert grid, each with the key its answers
 * name it by.
 *
 * <p>The rows are the shared option editor's. What this adds is the key: moving a row or changing
 * its words keeps the key it has, and a row added gets a new one. That is what lets a question with
 * answers be reordered and reworded without the answers changing what they say.
 */
const options = defineModel<FormOption[]>({ required: true })

defineProps<{
  label?: string
  addLabel?: string
}>()

function labelOf(option: FormOption): string {
  return option.label
}

function relabelled(option: FormOption, label: string): FormOption {
  return { ...option, label }
}

function fresh(): FormOption {
  return blankOption(options.value)
}
</script>

<template>
  <QuestionOptionsEditor v-model="options" :label="label" :add-label="addLabel"
                         :text-of="labelOf" :with-text="relabelled" :blank="fresh"/>
</template>
