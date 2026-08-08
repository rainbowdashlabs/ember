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
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import PointsPerCorrectField from './PointsPerCorrectField.vue'
import { useQuestionConfigList } from './useQuestionConfigList'

const { t } = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

const answers = useQuestionConfigList(config, 'answers')

function updateConfig(patch: Record<string, unknown>) {
  config.value = { ...config.value, ...patch }
}
</script>

<template>
  <PointsPerCorrectField :model-value="(config.pointsPerCorrect as number) || 1"
                         @update:model-value="v => updateConfig({ pointsPerCorrect: v })"/>
  <div class="flex items-center gap-2">
    <FieldHint>{{ t('quiz.questions.config.lines') }}</FieldHint>
    <NumberInput :model-value="(config.lines as number)" class="w-20" @update:model-value="(v: number | undefined) => updateConfig({ lines: v ?? 3 })" />
  </div>
  <SubHeader>{{ t('quiz.questions.config.enumerationAnswers') }}</SubHeader>
  <div class="space-y-2">
    <div v-for="(ans, idx) in answers.items.value" :key="idx" class="flex items-center gap-2">
      <span class="text-xs text-(--text-muted) shrink-0">{{ idx + 1 }}.</span>
      <TextInput :model-value="ans" class="flex-1" @update:model-value="(v: string | undefined) => answers.update(idx, v ?? '')" />
      <DeleteButton @click="answers.remove(idx)" />
    </div>
    <SecondaryButton @click="answers.add"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addAnswer') }}</SecondaryButton>
  </div>
</template>
