/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

const props = defineProps<{
  config: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:config': [value: Record<string, unknown>]
}>()

function updateConfig(patch: Record<string, unknown>) {
  emit('update:config', { ...props.config, ...patch })
}

// --- Fill blank helpers ---
function addFillBlankAnswer() {
  const answers = [...((props.config.answers as string[]) || [])]
  answers.push('')
  updateConfig({ answers })
}

function removeFillBlankAnswer(idx: number) {
  const answers = [...((props.config.answers as string[]) || [])]
  answers.splice(idx, 1)
  updateConfig({ answers })
}

function updateFillBlankAnswer(idx: number, value: string) {
  const answers = [...((props.config.answers as string[]) || [])]
  answers[idx] = value
  updateConfig({ answers })
}

function addDistractor() {
  const distractors = [...((props.config.distractors as string[]) || [])]
  distractors.push('')
  updateConfig({ distractors })
}

function removeDistractor(idx: number) {
  const distractors = [...((props.config.distractors as string[]) || [])]
  distractors.splice(idx, 1)
  updateConfig({ distractors })
}

function updateDistractor(idx: number, value: string) {
  const distractors = [...((props.config.distractors as string[]) || [])]
  distractors[idx] = value
  updateConfig({ distractors })
}
</script>

<template>
  <SubHeader>{{ t('quiz.questions.config.fillText') }}</SubHeader>
  <TextAreaInput :model-value="(config.text as string)" :placeholder="t('quiz.questions.config.fillTextPlaceholder')" @update:model-value="(v: string | undefined) => updateConfig({ text: v ?? '' })" />
  <SubHeader>{{ t('quiz.questions.config.correctAnswers') }}</SubHeader>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.fillAnswersHint') }}</p>
  <div class="space-y-2">
    <div v-for="(ans, idx) in (config.answers as string[] || [])" :key="'a'+idx" class="flex items-center gap-2">
      <span class="text-xs text-(--text-muted) shrink-0 w-5 text-right">{{ idx + 1 }}.</span>
      <TextInput :model-value="ans" class="flex-1" @update:model-value="(v: string | undefined) => updateFillBlankAnswer(idx, v ?? '')" />
      <DeleteButton @click="removeFillBlankAnswer(idx)" />
    </div>
    <SecondaryButton @click="addFillBlankAnswer"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addAnswer') }}</SecondaryButton>
  </div>
  <SubHeader>{{ t('quiz.questions.config.distractors') }}</SubHeader>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.distractorsHint') }}</p>
  <div class="space-y-2">
    <div v-for="(word, idx) in (config.distractors as string[] || [])" :key="'d'+idx" class="flex items-center gap-2">
      <TextInput :model-value="word" class="flex-1" @update:model-value="(v: string | undefined) => updateDistractor(idx, v ?? '')" />
      <DeleteButton @click="removeDistractor(idx)" />
    </div>
    <SecondaryButton @click="addDistractor"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addDistractor') }}</SecondaryButton>
  </div>
  <label class="flex items-center gap-2 text-sm mt-3">
    <ToggleInput :model-value="(config.useDropdown as boolean) ?? false" @update:model-value="(v: boolean) => updateConfig({ useDropdown: v })" />
    {{ t('quiz.questions.config.useDropdown') }}
  </label>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.useDropdownHint') }}</p>
</template>
