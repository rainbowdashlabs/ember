/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import {ai} from '@/api'
import {getItem} from '@/api/storage'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()

const config = defineModel<Record<string, unknown>>('config', {required: true})

const props = defineProps<{
  questionTitle: string
}>()

function updateConfig(patch: Record<string, unknown>) {
  config.value = {...config.value, ...patch}
}

/** One answer somebody may pick, and whether picking it is right. */
interface McOption {
  text: string
  correct: boolean
}

const mcOptions = computed<McOption[]>(() => (config.value.options as McOption[]) ?? [])

function toggleMcOptionCorrect(option: McOption) {
  updateConfig({
    options: mcOptions.value.map(candidate =>
        candidate === option ? {...candidate, correct: !candidate.correct} : candidate),
  })
}

const aiCountMode = ref<'add' | 'fillTo'>('add')
const aiCount = ref(3)

const {running: aiGenerating, error: aiError, run: generateWrongAnswers} = useAsyncAction(async () => {
  const options = (config.value.options as { text: string; correct: boolean }[]) || []
  const correctAnswer = options.filter(o => o.correct).map(o => o.text).join(', ')
  if (!correctAnswer || !props.questionTitle) return

  const provider = getItem('ai_provider') || 'openai'
  const apiKey = getItem('ai_api_key') || ''
  const model = getItem('ai_model') || ''

  if (!apiKey) throw new Error(t('quiz.ai.noKeyConfigured'))

  const count = aiCountMode.value === 'fillTo'
      ? Math.max(0, aiCount.value - options.length)
      : aiCount.value
  if (count <= 0) return

  const results = await ai.generate({
    provider, apiKey, model: model || null,
    question: props.questionTitle, correctAnswer, count,
  })
  if (results.length > 0) {
    const newOptions = [...options, ...results.map(text => ({text, correct: false}))]
    config.value = {...config.value, options: newOptions}
  }
}, {formatError: e => e instanceof Error ? e.message : String(e)})
</script>

<template>
  <SubHeader>{{ t('quiz.questions.config.options') }}</SubHeader>
  <div class="flex items-center gap-2">
    <FieldHint>{{ t('quiz.questions.config.pointsPerCorrect') }}</FieldHint>
    <DecimalInput :model-value="(config.pointsPerCorrect as number)" step="0.5" class="w-20"
                  @update:model-value="(v: number | undefined) => updateConfig({ pointsPerCorrect: v ?? 1 })"/>
  </div>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.mcScoringHint') }}</p>
  <div class="space-y-2">
    <QuestionOptionsEditor
        :add-label="t('quiz.questions.config.addOption')"
        :blank="() => ({text: '', correct: false})"
        :model-value="mcOptions"
        :text-of="(option: McOption) => option.text"
        :with-text="(option: McOption, text: string) => ({...option, text})"
        @update:model-value="options => updateConfig({options})"
    >
      <template #before="{option}">
        <IconButton
            :class="option.correct ? 'text-success' : 'text-(--text-muted)'"
            :icon="['fas', option.correct ? 'square-check' : 'square']"
            :label="t('quiz.questions.config.correctAnswer')"
            @click="toggleMcOptionCorrect(option)"
        />
      </template>

      <template #actions>
        <SecondaryButton :disabled="aiGenerating" @click="generateWrongAnswers">
          <Spinner v-if="aiGenerating" size="sm" class="mr-1"/>
          <font-awesome-icon v-else :icon="['fas', 'brain']" class="mr-1"/>
          {{ t('quiz.ai.generate') }}
        </SecondaryButton>
        <ToggleSwitch v-model="aiCountMode" :label-a="t('quiz.ai.modeAdd')" :label-b="t('quiz.ai.modeFillTo')"
                      option-a="add" option-b="fillTo"/>
        <NumberInput v-model="aiCount" class="w-14"/>
      </template>
    </QuestionOptionsEditor>
    <div v-if="aiError" class="text-xs text-error">{{ aiError }}</div>
  </div>
</template>
