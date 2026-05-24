/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import { ai } from '@/api'
import type { AiModel, AiSettings } from '@/api/ai'

const { t } = useI18n()

const props = defineProps<{
  config: Record<string, unknown>
  questionTitle: string
}>()

const emit = defineEmits<{
  'update:config': [value: Record<string, unknown>]
}>()

function updateConfig(patch: Record<string, unknown>) {
  emit('update:config', { ...props.config, ...patch })
}

// --- MC helpers ---
function addMcOption() {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts.push({ text: '', correct: false })
  updateConfig({ options: opts })
}

function removeMcOption(idx: number) {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts.splice(idx, 1)
  updateConfig({ options: opts })
}

function updateMcOptionText(idx: number, value: string) {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts[idx] = { ...opts[idx], text: value }
  updateConfig({ options: opts })
}

function toggleMcOptionCorrect(idx: number) {
  const opts = [...((props.config.options as { text: string; correct: boolean }[]) || [])]
  opts[idx] = { ...opts[idx], correct: !opts[idx].correct }
  updateConfig({ options: opts })
}

// --- AI generation ---
const showAiPanel = ref(false)
const aiSettings = ref<AiSettings | null>(null)
const aiProvider = ref('openai')
const aiTransientKey = ref('')
const aiModels = ref<AiModel[]>([])
const aiSelectedModel = ref('')
const aiGenerating = ref(false)
const aiError = ref('')
const aiFetchingModels = ref(false)
const aiCount = ref(3)

async function loadAiSettings() {
  try {
    aiSettings.value = await ai.getSettings()
    if (aiSettings.value.providers.length > 0) {
      aiProvider.value = aiSettings.value.providers[0].provider
      aiSelectedModel.value = aiSettings.value.providers[0].model ?? ''
    }
  } catch { /* ignore */ }
}

function hasStoredKey(provider: string): boolean {
  return aiSettings.value?.providers.some(p => p.provider === provider) ?? false
}

async function loadModels() {
  aiFetchingModels.value = true
  aiError.value = ''
  try {
    aiModels.value = await ai.fetchModels(aiProvider.value, aiTransientKey.value || null)
  } catch (e: unknown) {
    aiError.value = e instanceof Error ? e.message : String(e)
  } finally {
    aiFetchingModels.value = false
  }
}

async function generateWrongAnswers() {
  const options = (props.config.options as { text: string; correct: boolean }[]) || []
  const correctAnswer = options.filter(o => o.correct).map(o => o.text).join(', ')
  if (!correctAnswer || !props.questionTitle) return
  aiGenerating.value = true
  aiError.value = ''
  try {
    const results = await ai.generate({
      provider: aiProvider.value,
      apiKey: aiTransientKey.value || null,
      model: aiSelectedModel.value || null,
      question: props.questionTitle,
      correctAnswer,
      count: aiCount.value,
    })
    if (results.length > 0) {
      const newOptions = [...options, ...results.map(text => ({ text, correct: false }))]
      emit('update:config', { ...props.config, options: newOptions })
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
    <DecimalInput :model-value="(config.pointsPerCorrect as number)" step="0.5" class="w-20" @update:model-value="(v: number | undefined) => updateConfig({ pointsPerCorrect: v ?? 0.5 })" />
  </div>
  <p class="text-xs text-(--text-muted)">{{ t('quiz.questions.config.mcScoringHint') }}</p>
  <div class="space-y-2">
    <div v-for="(opt, idx) in (config.options as { text: string; correct: boolean }[])" :key="idx" class="flex items-center gap-2">
      <IconButton
        :icon="['fas', opt.correct ? 'square-check' : 'square']"
        :label="t('quiz.questions.config.correctAnswer')"
        :class="opt.correct ? 'text-success' : 'text-(--text-muted)'"
        @click="toggleMcOptionCorrect(idx)"
      />
      <TextInput :model-value="opt.text" class="flex-1" @update:model-value="(v: string | undefined) => updateMcOptionText(idx, v ?? '')" />
      <DeleteButton @click="removeMcOption(idx)" />
    </div>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton @click="addMcOption"><font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />{{ t('quiz.questions.config.addOption') }}</SecondaryButton>
      <SecondaryButton @click="() => { showAiPanel = !showAiPanel; if (showAiPanel && !aiSettings) loadAiSettings() }">
        <font-awesome-icon :icon="['fas', 'brain']" class="mr-1" />
        {{ t('quiz.ai.generate') }}
      </SecondaryButton>
    </div>
  </div>

  <!-- AI Generation Panel -->
  <div v-if="showAiPanel" class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent p-4 space-y-3">
    <div class="flex items-center gap-2 text-sm font-medium">
      <font-awesome-icon :icon="['fas', 'brain']" class="text-primary" />
      {{ t('quiz.ai.title') }}
    </div>
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
      <div>
        <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.ai.provider') }}</label>
        <SelectInput v-model="aiProvider">
          <option value="openai">{{ t('quiz.ai.providers.openai') }}</option>
          <option value="gemini">{{ t('quiz.ai.providers.gemini') }}</option>
          <option value="claude">{{ t('quiz.ai.providers.claude') }}</option>
        </SelectInput>
      </div>
      <div>
        <label class="text-xs text-(--text-muted) block mb-1">
          {{ t('quiz.ai.apiKey') }}
          <span v-if="hasStoredKey(aiProvider)" class="text-success ml-1">{{ t('quiz.ai.keyStored') }}</span>
        </label>
        <TextInput v-model="aiTransientKey" type="password" :placeholder="hasStoredKey(aiProvider) ? t('quiz.ai.keyStoredPlaceholder') : 'sk-...'" />
      </div>
      <div>
        <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.ai.model') }}</label>
        <div class="flex gap-1">
          <SelectInput v-if="aiModels.length > 0" v-model="aiSelectedModel" class="flex-1">
            <option value="">{{ t('quiz.ai.defaultModel') }}</option>
            <option v-for="m in aiModels" :key="m.id" :value="m.id">{{ m.name }}</option>
          </SelectInput>
          <TextInput v-else v-model="aiSelectedModel" class="flex-1" placeholder="gpt-4o-mini" />
          <SecondaryButton @click="loadModels" :disabled="aiFetchingModels">
            <Spinner v-if="aiFetchingModels" size="sm" />
            <font-awesome-icon v-else :icon="['fas', 'rotate']" />
          </SecondaryButton>
        </div>
      </div>
    </div>
    <div class="flex items-center gap-3">
      <div class="flex items-center gap-2">
        <FieldHint>{{ t('quiz.ai.count') }}</FieldHint>
        <NumberInput v-model="aiCount" class="w-16" />
      </div>
      <PrimaryButton :disabled="aiGenerating" @click="generateWrongAnswers">
        <Spinner v-if="aiGenerating" size="sm" class="mr-1" />
        <font-awesome-icon v-else :icon="['fas', 'brain']" class="mr-1" />
        {{ aiGenerating ? t('quiz.ai.generating') : t('quiz.ai.generate') }}
      </PrimaryButton>
    </div>
    <div v-if="aiError" class="text-xs text-error">{{ aiError }}</div>
  </div>
</template>
