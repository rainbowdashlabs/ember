/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PointsPerCorrectField from './PointsPerCorrectField.vue'
import { useQuestionConfigList } from './useQuestionConfigList'

const { t } = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

const items = useQuestionConfigList(config, 'items')

function updateConfig(patch: Record<string, unknown>) {
  config.value = { ...config.value, ...patch }
}
</script>

<template>
  <PointsPerCorrectField :model-value="(config.pointsPerCorrect as number) || 1"
                         @update:model-value="v => updateConfig({ pointsPerCorrect: v })"/>
  <SubHeader>{{ t('quiz.questions.config.items') }}</SubHeader>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.orderingHint') }}</p>
  <div class="space-y-2">
    <div v-for="(item, idx) in items.items.value" :key="idx" class="flex items-center gap-2">
      <span class="text-xs text-(--text-muted) shrink-0 w-5 text-right">{{ idx + 1 }}.</span>
      <TextInput :model-value="item" class="flex-1" @update:model-value="(v: string | undefined) => items.update(idx, v ?? '')" />
      <MutedIconButton :icon="['fas', 'chevron-up']" label="Up" :disabled="idx === 0" @click="items.move(idx, -1)" />
      <MutedIconButton :icon="['fas', 'chevron-down']" label="Down" :disabled="idx === items.items.value.length - 1" @click="items.move(idx, 1)" />
      <DeleteButton @click="items.remove(idx)" />
    </div>
    <SecondaryButton @click="items.add"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addItem') }}</SecondaryButton>
  </div>
</template>
