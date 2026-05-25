/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import {ai} from '@/api'
import {getItem} from '@/api/storage'

const {t} = useI18n()

const props = defineProps<{
  config: Record<string, unknown>
  questionTitle: string
}>()

const emit = defineEmits<{
  'update:config': [value: Record<string, unknown>]
}>()

function updateConfig(patch: Record<string, unknown>) {
  emit('update:config', {...props.config, ...patch})
}

// --- MC helpers ---
function addMcOption() {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts.push({text: '', correct: false})
  updateConfig({options: opts})
}

function removeMcOption(idx: number) {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts.splice(idx, 1)
  updateConfig({options: opts})
}

function updateMcOptionText(idx: number, value: string) {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts[idx] = {...opts[idx], text: value}
  updateConfig({options: opts})
}

function toggleMcOptionCorrect(idx: number) {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts[idx] = {...opts[idx], correct: !opts[idx].correct}
  updateConfig({options: opts})
}

// --- AI generation ---
const aiGenerating = ref(false)
const aiError = ref('')
const aiCountMode = ref<'add' | 'fillTo'>('add')
const aiCount = ref(3)

async function generateWrongAnswers() {
  const options = (props.config.options as { text: string; correct: boolean }[]) || []
  const correctAnswer = options.filter(o => o.correct).map(o => o.text).join(', ')
  if (!correctAnswer || !props.questionTitle) return

  const provider = getItem('ai_provider') || 'openai'
  const apiKey = getItem('ai_api_key') || ''
  const model = getItem('ai_model') || ''

  if (!apiKey) {
    aiError.value = t('quiz.ai.noKeyConfigured')
    return
  }

  const count = aiCountMode.value === 'fillTo'
      ? Math.max(0, aiCount.value - options.length)
      : aiCount.value
  if (count <= 0) return

  aiGenerating.value = true
  aiError.value = ''
  try {
    const results = await ai.generate({
      provider, apiKey, model: model || null,
      question: props.questionTitle, correctAnswer, count,
    })
    if (results.length > 0) {
      const newOptions = [...options, ...results.map(text => ({text, correct: false}))]
      emit('update:config', {...props.config, options: newOptions})
    }
  } catch (e: unknown) {
    aiError.value = e instanceof Error ? e.message : String(e)
  } finally {
    aiGenerating.value = false
  }
}
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
    <div v-for="(opt, idx) in (config.options as { text: string; correct: boolean }[])" :key="idx"
         class="flex items-center gap-2">
      <IconButton
          :icon="['fas', opt.correct ? 'square-check' : 'square']"
          :label="t('quiz.questions.config.correctAnswer')"
          :class="opt.correct ? 'text-success' : 'text-(--text-muted)'"
          @click="toggleMcOptionCorrect(idx)"
      />
      <TextInput :model-value="opt.text" class="flex-1"
                 @update:model-value="(v: string | undefined) => updateMcOptionText(idx, v ?? '')"/>
      <DeleteButton @click="removeMcOption(idx)"/>
    </div>
    <div class="flex items-center gap-2 flex-wrap">
      <SecondaryButton @click="addMcOption">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('quiz.questions.config.addOption') }}
      </SecondaryButton>
      <SecondaryButton :disabled="aiGenerating" @click="generateWrongAnswers">
        <Spinner v-if="aiGenerating" size="sm" class="mr-1"/>
        <font-awesome-icon v-else :icon="['fas', 'brain']" class="mr-1"/>
        {{ t('quiz.ai.generate') }}
      </SecondaryButton>
      <ToggleSwitch v-model="aiCountMode" option-a="add" option-b="fillTo" :label-a="t('quiz.ai.modeAdd')" :label-b="t('quiz.ai.modeFillTo')"/>
      <NumberInput v-model="aiCount" class="w-14"/>
    </div>
    <div v-if="aiError" class="text-xs text-error">{{ aiError }}</div>
  </div>
</template>
