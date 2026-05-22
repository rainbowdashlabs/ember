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

// --- Free answer helpers ---
function addFreeAnswerItem() {
  const answers = [...((props.config.answers as string[]) || [])]
  answers.push('')
  updateConfig({ answers })
}

function removeFreeAnswerItem(idx: number) {
  const answers = [...((props.config.answers as string[]) || [])]
  answers.splice(idx, 1)
  updateConfig({ answers })
}

function updateFreeAnswerItem(idx: number, value: string) {
  const answers = [...((props.config.answers as string[]) || [])]
  answers[idx] = value
  updateConfig({ answers })
}
</script>

<template>
  <div class="flex items-center gap-2">
    <label class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.lines') }}</label>
    <NumberInput :model-value="(config.lines as number)" class="w-20" @update:model-value="(v: number | undefined) => updateConfig({ lines: v ?? 3 })" />
  </div>
  <SubHeader>{{ t('quiz.questions.config.enumerationAnswers') }}</SubHeader>
  <div class="space-y-2">
    <div v-for="(ans, idx) in (config.answers as string[])" :key="idx" class="flex items-center gap-2">
      <span class="text-xs text-(--text-muted) shrink-0">{{ idx + 1 }}.</span>
      <TextInput :model-value="ans" class="flex-1" @update:model-value="(v: string | undefined) => updateFreeAnswerItem(idx, v ?? '')" />
      <DeleteButton @click="removeFreeAnswerItem(idx)" />
    </div>
    <SecondaryButton @click="addFreeAnswerItem"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addAnswer') }}</SecondaryButton>
  </div>
</template>
