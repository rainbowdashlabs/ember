/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { AiSettings, AiModel } from '@/api/ai'
import { ai as aiApi } from '@/api'

const props = defineProps<{
  catalogId: number
}>()

const { t } = useI18n()

const showAiSettings = ref(false)
const aiSettingsData = ref<AiSettings | null>(null)
const aiBatchProvider = ref('openai')
const aiBatchTransientKey = ref('')
const aiBatchModel = ref('')
const aiBatchTarget = ref(5)
const aiBatchGenerating = ref(false)
const aiBatchResult = ref('')
const aiPromptEdit = ref('')
const aiSavingKey = ref(false)
const aiSaveKeyValue = ref('')
const aiModels = ref<AiModel[]>([])
const aiFetchingModels = ref(false)
const error = ref('')

async function loadAiSettings() {
  try {
    aiSettingsData.value = await aiApi.getSettings()
    aiPromptEdit.value = aiSettingsData.value.prompt
    if (aiSettingsData.value.providers.length > 0) {
      aiBatchProvider.value = aiSettingsData.value.providers[0].provider
      aiBatchModel.value = aiSettingsData.value.providers[0].model ?? ''
    }
  } catch { /* ignore */ }
}

function hasStoredKey(provider: string): boolean {
  return aiSettingsData.value?.providers.some(p => p.provider === provider) ?? false
}

async function saveAiPrompt() {
  try {
    await aiApi.savePrompt(aiPromptEdit.value)
  } catch { error.value = t('common.error') }
}

async function saveAiKey() {
  if (!aiSaveKeyValue.value) return
  aiSavingKey.value = true
  try {
    await aiApi.saveProvider(aiBatchProvider.value, aiSaveKeyValue.value, aiBatchModel.value || null)
    aiSaveKeyValue.value = ''
    await loadAiSettings()
  } catch { error.value = t('common.error') }
  finally { aiSavingKey.value = false }
}

async function removeAiKey(provider: string) {
  try {
    await aiApi.deleteProvider(provider)
    await loadAiSettings()
  } catch { error.value = t('common.error') }
}

async function loadAiModels() {
  aiFetchingModels.value = true
  try {
    aiModels.value = await aiApi.fetchModels(aiBatchProvider.value, aiBatchTransientKey.value || null)
  } catch { /* ignore */ }
  finally { aiFetchingModels.value = false }
}

async function batchGenerate() {
  aiBatchGenerating.value = true
  aiBatchResult.value = ''
  try {
    const result = await aiApi.batchGenerate(props.catalogId, {
      provider: aiBatchProvider.value,
      apiKey: aiBatchTransientKey.value || null,
      model: aiBatchModel.value || null,
      targetTotalOptions: aiBatchTarget.value,
    })
    aiBatchResult.value = t('quiz.ai.batchSuccess', { count: result.generatedCount })
    if (result.errors.length > 0) {
      aiBatchResult.value += ' ' + t('quiz.ai.batchErrors', { count: result.errors.length })
    }
  } catch (e: unknown) {
    aiBatchResult.value = e instanceof Error ? e.message : t('common.error')
  } finally {
    aiBatchGenerating.value = false
  }
}

function getProvider(): string {
  return aiBatchProvider.value
}

function getTransientKey(): string {
  return aiBatchTransientKey.value
}

function getModel(): string {
  return aiBatchModel.value
}

loadAiSettings()

defineExpose({ getProvider, getTransientKey, getModel })
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('quiz.ai.settingsTitle') }}</SectionHeader>
      <SecondaryButton :icon="['fas', 'brain']" @click="showAiSettings = !showAiSettings">
        {{ showAiSettings ? t('common.close') : t('quiz.ai.settingsTitle') }}
      </SecondaryButton>
    </div>

    <NeutralContainer v-if="showAiSettings">
      <div class="space-y-4">
        <!-- Provider + Key -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.ai.provider') }}</FieldLabel>
            <SelectInput v-model="aiBatchProvider">
              <option value="openai">{{ t('quiz.ai.providers.openai') }}</option>
              <option value="gemini">{{ t('quiz.ai.providers.gemini') }}</option>
              <option value="claude">{{ t('quiz.ai.providers.claude') }}</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel hint class="mb-1">
              {{ t('quiz.ai.model') }}
            </FieldLabel>
            <div class="flex gap-1">
              <SelectInput v-if="aiModels.length > 0" v-model="aiBatchModel" class="flex-1">
                <option value="">{{ t('quiz.ai.defaultModel') }}</option>
                <option v-for="m in aiModels" :key="m.id" :value="m.id">{{ m.name }}</option>
              </SelectInput>
              <TextInput v-else v-model="aiBatchModel" class="flex-1" placeholder="gpt-4o-mini" />
              <SecondaryButton @click="loadAiModels" :disabled="aiFetchingModels">
                <font-awesome-icon :icon="['fas', 'rotate']" />
              </SecondaryButton>
            </div>
          </div>
        </div>

        <!-- Stored key management -->
        <div class="space-y-2">
          <div class="flex items-center gap-2 flex-wrap">
            <span v-if="hasStoredKey(aiBatchProvider)" class="text-xs text-success">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
              {{ t('quiz.ai.keyStored') }}
            </span>
            <ErrorButton v-if="hasStoredKey(aiBatchProvider)" @click="removeAiKey(aiBatchProvider)">
              {{ t('quiz.ai.removeKey') }}
            </ErrorButton>
          </div>
          <div class="flex gap-2">
            <TextInput v-model="aiSaveKeyValue" type="password" :placeholder="hasStoredKey(aiBatchProvider) ? t('quiz.ai.keyStoredPlaceholder') : 'sk-...'" class="flex-1" />
            <PrimaryButton :disabled="!aiSaveKeyValue || aiSavingKey" @click="saveAiKey">
              {{ t('quiz.ai.saveKey') }}
            </PrimaryButton>
          </div>
          <p class="text-xs text-(--text-muted)">{{ t('quiz.ai.keyNotEncrypted') }}</p>
        </div>

        <!-- Transient key (for this session only) -->
        <div>
          <FieldLabel hint class="mb-1">{{ t('quiz.ai.apiKey') }} ({{ t('quiz.ai.transientKeyHint') }})</FieldLabel>
          <TextInput v-model="aiBatchTransientKey" type="password" placeholder="sk-..." />
        </div>

        <!-- Prompt -->
        <div>
          <FieldLabel hint class="mb-1">{{ t('quiz.ai.prompt') }}</FieldLabel>
          <TextAreaInput v-model="aiPromptEdit" class="text-xs" />
          <MutedText tag="p" class="mt-1">{{ t('quiz.ai.promptHint') }}</MutedText>
          <div class="flex justify-end mt-2">
            <SecondaryButton @click="saveAiPrompt">{{ t('quiz.ai.savePrompt') }}</SecondaryButton>
          </div>
        </div>

        <!-- Batch generate -->
        <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-3 space-y-3">
          <SubHeader>{{ t('quiz.ai.batchGenerate') }}</SubHeader>
          <MutedText tag="p" class="text-xs">{{ t('quiz.ai.batchHint') }}</MutedText>
          <div class="flex items-center gap-2">
            <div class="flex items-center gap-2">
              <FieldHint>{{ t('quiz.ai.batchTarget') }}</FieldHint>
              <NumberInput v-model="aiBatchTarget" class="w-16" />
            </div>
            <PrimaryButton :disabled="aiBatchGenerating" @click="batchGenerate">
              <Spinner v-if="aiBatchGenerating" size="sm" class="mr-1" />
              <font-awesome-icon v-else :icon="['fas', 'brain']" class="mr-1" />
              {{ t('quiz.ai.batchGenerate') }}
            </PrimaryButton>
          </div>
          <p v-if="aiBatchResult" class="text-xs" :class="aiBatchResult.includes('Fehler') ? 'text-error' : 'text-success'">{{ aiBatchResult }}</p>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>
