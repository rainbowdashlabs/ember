/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const enabled = defineModel<boolean>('enabled', {required: true})
const count = defineModel<number>('count', {required: true})
const prompt = defineModel<string>('prompt', {required: true})

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center gap-2">
      <ToggleInput v-model="enabled" />
      <span class="text-sm font-medium">{{ t('quiz.csv.generateWrongAnswers') }}</span>
    </div>
    <template v-if="enabled">
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.wrongAnswerCount') }}</FieldLabel>
        <NumberInput v-model="count" :min="1" :max="10" class="w-20" />
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.ai.additionalPrompt') }}</FieldLabel>
        <TextInput v-model="prompt" :placeholder="t('quiz.ai.additionalPromptPlaceholder')" />
      </div>
    </template>
    <p class="text-xs text-(--text-muted)">{{ t('quiz.csv.generateWrongAnswersHint') }}</p>
  </NeutralContainer>
</template>
