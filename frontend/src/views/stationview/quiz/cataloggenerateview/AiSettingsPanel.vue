/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AiKeyField from './AiKeyField.vue'
import type {AiCredentialSummary, AiModel} from '@/api/ai'
import {ai as aiApi} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()

const showAiSettings = ref(false)
const aiProvider = ref('openai')
const aiModel = ref('')
/** A key being typed in. It leaves the browser once, on save, and is never read back. */
const aiApiKey = ref('')
const stored = ref<AiCredentialSummary | null>(null)
const saveOnServer = ref(false)
const aiModels = ref<AiModel[]>([])

/** Whether the key stored in the account is one for the provider now chosen, and still opens. */
const keptKey = computed(() => !!stored.value?.usable && stored.value.provider === aiProvider.value)

/**
 * Loads the person's own settings, and the station's where the person keeps none: the station's
 * key is what generation falls back on, so its provider and model are the sensible start.
 */
async function loadSettings() {
  try {
    stored.value = await aiApi.getAiCredential()
    if (stored.value.provider) {
      aiProvider.value = stored.value.provider
      aiModel.value = stored.value.model ?? ''
    }
  } catch { void 0 }
  try {
    const station = (await aiApi.getSettings()).providers[0]
    if (!station) return
    saveOnServer.value = true
    if (!stored.value?.provider) {
      aiProvider.value = station.provider
      aiModel.value = station.model ?? ''
    }
  } catch { void 0 }
}

const {running: aiFetchingModels, run: loadAiModels} = useAsyncAction(async () => {
  aiModels.value = await aiApi.fetchModels(aiProvider.value, aiApiKey.value || null)
})

/**
 * Saves the key into the person's account, encrypted, and for the whole station as well where that
 * is switched on and a key was typed. Without a new key the stored one is kept.
 */
const {failure: saveFailure, run: runSave} = useAsyncAction(async () => {
  stored.value = await aiApi.saveAiCredential({
    provider: aiProvider.value,
    model: aiModel.value || null,
    apiKey: aiApiKey.value || null,
  })
  if (saveOnServer.value && aiApiKey.value) {
    await aiApi.saveProvider(aiProvider.value, aiApiKey.value, aiModel.value || null)
  }
  aiApiKey.value = ''
})

const {failure: removeFailure, run: removeKey} = useAsyncAction(async () => {
  await aiApi.deleteAiCredential()
  stored.value = null
})

async function save() {
  await runSave()
  if (saveFailure.value) throw saveFailure.value
}

watch(saveOnServer, async (val) => {
  if (val) return
  try {
    await aiApi.deleteProvider(aiProvider.value)
  } catch { void 0 }
})

function getProvider(): string { return aiProvider.value }
function getModel(): string { return aiModel.value }

loadSettings()

defineExpose({getProvider, getModel})
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
            <SelectInput v-model="aiProvider">
              <option value="openai">{{ t('quiz.ai.providers.openai') }}</option>
              <option value="gemini">{{ t('quiz.ai.providers.gemini') }}</option>
              <option value="claude">{{ t('quiz.ai.providers.claude') }}</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.ai.model') }}</FieldLabel>
            <div class="flex gap-1">
              <SelectInput v-if="aiModels.length > 0" v-model="aiModel" class="flex-1">
                <option value="">{{ t('quiz.ai.defaultModel') }}</option>
                <option v-for="m in aiModels" :key="m.id" :value="m.id">{{ m.name }}</option>
              </SelectInput>
              <TextInput v-else v-model="aiModel" class="flex-1" placeholder="gpt-4o-mini"/>
              <SecondaryButton :disabled="aiFetchingModels || (!aiApiKey && !keptKey)" @click="loadAiModels">
                <Spinner v-if="aiFetchingModels" size="sm"/>
                <font-awesome-icon v-else :icon="['fas', 'rotate']"/>
              </SecondaryButton>
            </div>
          </div>
        </div>

        <AiKeyField v-model="aiApiKey" :stored="stored" :kept-key="keptKey"/>

        <div class="flex items-center gap-2">
          <ToggleInput v-model="saveOnServer"/>
          <span class="text-sm font-medium">{{ t('quiz.ai.saveOnServer') }}</span>
        </div>
        <MutedText v-if="saveOnServer" tag="p" class="text-xs">{{ t('quiz.ai.stationKeyHint') }}</MutedText>

        <FailureAlert :failure="saveFailure ?? removeFailure"/>
        <ButtonRow pair>
          <SaveButton :action="save" :disabled="!aiApiKey && !keptKey"/>
          <ErrorButton v-if="stored?.provider" :icon="['fas', 'trash']" @click="removeKey">
            {{ t('quiz.ai.removeKey') }}
          </ErrorButton>
        </ButtonRow>
      </div>
    </NeutralContainer>
  </div>
</template>
