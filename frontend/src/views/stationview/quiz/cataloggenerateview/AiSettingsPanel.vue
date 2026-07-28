/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {AiModel} from '@/api/ai'
import {ai as aiApi} from '@/api'
import {getItem, setItem} from '@/api/storage'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()

const showAiSettings = ref(false)
const aiBatchProvider = ref(getItem('ai_provider') || 'openai')
const aiBatchApiKey = ref(getItem('ai_api_key') || '')
const aiBatchModel = ref(getItem('ai_model') || '')
const saveOnServer = ref(false)
const aiModels = ref<AiModel[]>([])

watch(aiBatchProvider, v => setItem('ai_provider', v))
watch(aiBatchApiKey, v => setItem('ai_api_key', v))
watch(aiBatchModel, v => setItem('ai_model', v))

/**
 * Loads the server-side provider settings and applies them only when no local key is set.
 */
async function loadServerSettings() {
  try {
    const settings = await aiApi.getSettings()
    const p = settings.providers[0]
    if (p && !aiBatchApiKey.value) {
      aiBatchProvider.value = p.provider
      aiBatchModel.value = p.model ?? ''
      saveOnServer.value = true
    }
  } catch { void 0 }
}

const {running: aiFetchingModels, run: runLoadAiModels} = useAsyncAction(async () => {
  aiModels.value = await aiApi.fetchModels(aiBatchProvider.value, aiBatchApiKey.value)
})

async function loadAiModels() {
  if (!aiBatchApiKey.value) return
  await runLoadAiModels()
}

const {run: runSaveToServer} = useAsyncAction(() =>
  aiApi.saveProvider(aiBatchProvider.value, aiBatchApiKey.value, aiBatchModel.value || null),
)

async function saveToServer() {
  if (!aiBatchApiKey.value) return
  await runSaveToServer()
}

async function removeFromServer() {
  try {
    await aiApi.deleteProvider(aiBatchProvider.value)
    saveOnServer.value = false
  } catch { void 0 }
}

watch(saveOnServer, async (val) => {
  if (val && aiBatchApiKey.value) {
    await saveToServer()
  } else if (!val) {
    await removeFromServer()
  }
})

function getProvider(): string { return aiBatchProvider.value }
function getTransientKey(): string { return aiBatchApiKey.value }
function getModel(): string { return aiBatchModel.value }

loadServerSettings()

defineExpose({getProvider, getTransientKey, getModel})
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SubHeader>{{ t('quiz.ai.settingsTitle') }}</SubHeader>
      <SecondaryButton :icon="['fas', 'brain']" @click="showAiSettings = !showAiSettings">
        {{ showAiSettings ? t('common.close') : t('quiz.ai.settingsTitle') }}
      </SecondaryButton>
    </div>

    <NeutralContainer v-if="showAiSettings">
      <div class="space-y-4">
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
            <FieldLabel hint class="mb-1">{{ t('quiz.ai.model') }}</FieldLabel>
            <div class="flex gap-1">
              <SelectInput v-if="aiModels.length > 0" v-model="aiBatchModel" class="flex-1">
                <option value="">{{ t('quiz.ai.defaultModel') }}</option>
                <option v-for="m in aiModels" :key="m.id" :value="m.id">{{ m.name }}</option>
              </SelectInput>
              <TextInput v-else v-model="aiBatchModel" class="flex-1" placeholder="gpt-4o-mini"/>
              <SecondaryButton @click="loadAiModels" :disabled="aiFetchingModels || !aiBatchApiKey">
                <Spinner v-if="aiFetchingModels" size="sm"/>
                <font-awesome-icon v-else :icon="['fas', 'rotate']"/>
              </SecondaryButton>
            </div>
          </div>
        </div>

        <div>
          <FieldLabel hint class="mb-1">{{ t('quiz.ai.apiKey') }}</FieldLabel>
          <TextInput v-model="aiBatchApiKey" type="password" placeholder="sk-..."/>
          <MutedText tag="p" class="mt-1">{{ t('quiz.ai.keyLocalHint') }}</MutedText>
        </div>

        <div class="flex items-center gap-2">
          <ToggleInput v-model="saveOnServer"/>
          <span class="text-sm font-medium">{{ t('quiz.ai.saveOnServer') }}</span>
        </div>
        <MutedText v-if="saveOnServer" tag="p" class="text-xs">{{ t('quiz.ai.keyNotEncrypted') }}</MutedText>
      </div>
    </NeutralContainer>
  </div>
</template>
