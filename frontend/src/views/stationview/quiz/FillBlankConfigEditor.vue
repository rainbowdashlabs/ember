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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PointsPerCorrectField from './PointsPerCorrectField.vue'
import { useQuestionConfigList } from './useQuestionConfigList'

const { t } = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

const answers = useQuestionConfigList(config, 'answers')
const distractors = useQuestionConfigList(config, 'distractors')

function updateConfig(patch: Record<string, unknown>) {
  config.value = { ...config.value, ...patch }
}
</script>

<template>
  <PointsPerCorrectField :model-value="(config.pointsPerCorrect as number) || 1"
                         @update:model-value="v => updateConfig({ pointsPerCorrect: v })"/>
  <SubHeader>{{ t('quiz.questions.config.fillText') }}</SubHeader>
  <TextAreaInput :model-value="(config.text as string)" :placeholder="t('quiz.questions.config.fillTextPlaceholder')" @update:model-value="(v: string | undefined) => updateConfig({ text: v ?? '' })" />
  <SubHeader>{{ t('quiz.questions.config.correctAnswers') }}</SubHeader>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.fillAnswersHint') }}</p>
  <div class="space-y-2">
    <div v-for="(ans, idx) in answers.items.value" :key="'a'+idx" class="flex items-center gap-2">
      <span class="text-xs text-(--text-muted) shrink-0 w-5 text-right">{{ idx + 1 }}.</span>
      <TextInput :model-value="ans" class="flex-1" @update:model-value="(v: string | undefined) => answers.update(idx, v ?? '')" />
      <DeleteButton @click="answers.remove(idx)" />
    </div>
    <SecondaryButton @click="answers.add"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addAnswer') }}</SecondaryButton>
  </div>
  <SubHeader>{{ t('quiz.questions.config.distractors') }}</SubHeader>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.distractorsHint') }}</p>
  <div class="space-y-2">
    <div v-for="(word, idx) in distractors.items.value" :key="'d'+idx" class="flex items-center gap-2">
      <TextInput :model-value="word" class="flex-1" @update:model-value="(v: string | undefined) => distractors.update(idx, v ?? '')" />
      <DeleteButton @click="distractors.remove(idx)" />
    </div>
    <SecondaryButton @click="distractors.add"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addDistractor') }}</SecondaryButton>
  </div>
  <FieldLabel inline class="mt-3">
    <ToggleInput :model-value="(config.useDropdown as boolean) ?? false" @update:model-value="(v: boolean) => updateConfig({ useDropdown: v })" />
    {{ t('quiz.questions.config.useDropdown') }}
  </FieldLabel>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.useDropdownHint') }}</p>
</template>
