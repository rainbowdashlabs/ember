/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { QuizCatalogDetail } from '@/api/types'
import { quiz, ai as aiApi } from '@/api'
import type { AiSettings, AiModel } from '@/api/ai'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()

const catalogId = computed(() => Number(route.params.id))

const catalog = ref<QuizCatalogDetail | null>(null)
const loading = ref(true)
const error = ref('')

// AI question generation
const genUserPrompt = ref('')
const genGenerating = ref(false)
const genResult = ref('')
const genPhase = ref<'config' | 'review'>('config')
const genProgressTotal = ref(0)
const genRegenerating = ref<number | null>(null)

interface GenEntry {
  questionType: string
  count: number
  categoryId: number | null
}

interface GenPreview {
  title: string
  config: string
  questionType: string
  categoryId: number | null
  accepted: boolean
}

const genEntries = ref<GenEntry[]>([{ questionType: 'MULTIPLE_CHOICE', count: 5, categoryId: null }])
const genPreviews = ref<GenPreview[]>([])

// AI settings
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

// --- Data Loading ---

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    catalog.value = await quiz.getCatalog(catalogId.value)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

// --- AI Settings ---

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
    const result = await aiApi.batchGenerate(catalogId.value, {
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

// --- AI Question Generation ---

async function generateQuestions() {
  genGenerating.value = true
  genResult.value = ''
  genPreviews.value = []
  const totalCount = genEntries.value.reduce((sum, e) => sum + e.count, 0)
  genProgressTotal.value = totalCount
  genPhase.value = 'review'

  try {
    const jobId = await aiApi.startGenerateQuestions({
      provider: aiBatchProvider.value,
      apiKey: aiBatchTransientKey.value || null,
      model: aiBatchModel.value || null,
      userPrompt: genUserPrompt.value || null,
      locale: 'de',
      catalogId: catalogId.value,
      entries: genEntries.value.filter(e => e.count > 0),
    })

    // Poll for results every 3 seconds
    while (true) {
      await new Promise(r => setTimeout(r, 3000))
      const poll = await aiApi.pollGenerateQuestions(jobId)
      for (const q of poll.questions) {
        genPreviews.value.push({
          title: q.title, config: q.config,
          questionType: q.questionType, categoryId: q.categoryId, accepted: true,
        })
      }
      if (poll.done) break
    }
  } catch (e: unknown) {
    genResult.value = e instanceof Error ? e.message : t('common.error')
  }
  genGenerating.value = false
}

async function regenerateQuestion(index: number) {
  genRegenerating.value = index
  const prev = genPreviews.value[index]
  try {
    const generated = await aiApi.generateQuestions({
      provider: aiBatchProvider.value,
      apiKey: aiBatchTransientKey.value || null,
      model: aiBatchModel.value || null,
      userPrompt: genUserPrompt.value || null,
      locale: 'de',
      entries: [{ questionType: prev.questionType, count: 1, categoryId: prev.categoryId }],
    })
    if (generated.length > 0) {
      genPreviews.value[index] = {
        title: generated[0].title, config: generated[0].config,
        questionType: generated[0].questionType, categoryId: generated[0].categoryId, accepted: true,
      }
    }
  } catch (e: unknown) {
    genResult.value = e instanceof Error ? e.message : t('common.error')
  } finally {
    genRegenerating.value = null
  }
}

function toggleGenPreview(index: number) {
  genPreviews.value[index].accepted = !genPreviews.value[index].accepted
}

function genAcceptedCount(): number {
  return genPreviews.value.filter(q => q.accepted).length
}

async function saveGeneratedQuestions() {
  genGenerating.value = true
  genResult.value = ''
  try {
    const accepted = genPreviews.value.filter(q => q.accepted)
    for (const q of accepted) {
      await quiz.createQuestion(catalogId.value, {
        questionType: q.questionType,
        title: q.title,
        config: q.config,
        categoryId: q.categoryId,
      })
    }
    router.push({ name: 'quiz-catalog-detail', params: { id: catalogId.value } })
  } catch (e: unknown) {
    genResult.value = e instanceof Error ? e.message : t('common.error')
    genGenerating.value = false
  }
}

function parseGenConfig(configStr: string): Record<string, unknown> {
  try { return JSON.parse(configStr) } catch { return {} }
}

function resetGeneration() {
  genPhase.value = 'config'
  genPreviews.value = []
  genResult.value = ''
}

onMounted(() => {
  if (loaded.value) {
    loadData()
    loadAiSettings()
  }
})

watch(loaded, (isLoaded) => {
  if (isLoaded) {
    loadData()
    loadAiSettings()
  }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton @click="router.push({ name: 'quiz-catalog-detail', params: { id: catalogId } })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" />
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && catalog">
        <!-- AI Settings & Batch Generate -->
        <div class="space-y-3">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <SectionHeader>{{ t('quiz.ai.settingsTitle') }}</SectionHeader>
            <SecondaryButton @click="showAiSettings = !showAiSettings">
              <font-awesome-icon :icon="['fas', 'brain']" class="mr-1" />
              {{ showAiSettings ? t('common.close') : t('quiz.ai.settingsTitle') }}
            </SecondaryButton>
          </div>

          <NeutralContainer v-if="showAiSettings">
            <div class="space-y-4">
              <!-- Provider + Key -->
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.ai.provider') }}</label>
                  <SelectInput v-model="aiBatchProvider">
                    <option value="openai">{{ t('quiz.ai.providers.openai') }}</option>
                    <option value="gemini">{{ t('quiz.ai.providers.gemini') }}</option>
                    <option value="claude">{{ t('quiz.ai.providers.claude') }}</option>
                  </SelectInput>
                </div>
                <div>
                  <label class="text-xs text-(--text-muted) block mb-1">
                    {{ t('quiz.ai.model') }}
                  </label>
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
                <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.ai.apiKey') }} ({{ t('quiz.ai.transientKeyHint') }})</label>
                <TextInput v-model="aiBatchTransientKey" type="password" placeholder="sk-..." />
              </div>

              <!-- Prompt -->
              <div>
                <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.ai.prompt') }}</label>
                <TextAreaInput v-model="aiPromptEdit" class="text-xs" />
                <p class="text-xs text-(--text-muted) mt-1">{{ t('quiz.ai.promptHint') }}</p>
                <div class="flex justify-end mt-2">
                  <SecondaryButton @click="saveAiPrompt">{{ t('quiz.ai.savePrompt') }}</SecondaryButton>
                </div>
              </div>

              <!-- Batch generate -->
              <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-3 space-y-3">
                <SubHeader>{{ t('quiz.ai.batchGenerate') }}</SubHeader>
                <p class="text-xs text-(--text-muted)">{{ t('quiz.ai.batchHint') }}</p>
                <div class="flex items-center gap-3">
                  <div class="flex items-center gap-2">
                    <label class="text-xs text-(--text-muted)">{{ t('quiz.ai.batchTarget') }}</label>
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

        <!-- AI Question Generation -->
        <div class="space-y-3">
          <SectionHeader>{{ t('quiz.ai.generateQuestions') }}</SectionHeader>

          <!-- Phase 1: Configuration -->
          <template v-if="genPhase === 'config'">
            <div class="space-y-3">
              <div v-for="(entry, eIdx) in genEntries" :key="eIdx" class="flex flex-col sm:flex-row gap-2 items-start sm:items-end p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
                <div class="flex-1">
                  <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.questions.type') }}</label>
                  <SelectInput v-model="entry.questionType">
                    <option value="MULTIPLE_CHOICE">{{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}</option>
                    <option value="TRUE_FALSE">{{ t('quiz.questionTypes.TRUE_FALSE') }}</option>
                    <option value="FREE_ANSWER">{{ t('quiz.questionTypes.FREE_ANSWER') }}</option>
                    <option value="FILL_IN_THE_BLANK">{{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}</option>
                    <option value="CONNECT">{{ t('quiz.questionTypes.CONNECT') }}</option>
                    <option value="ORDERING">{{ t('quiz.questionTypes.ORDERING') }}</option>
                  </SelectInput>
                </div>
                <div class="w-20">
                  <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.ai.count') }}</label>
                  <NumberInput v-model="entry.count" />
                </div>
                <div class="flex-1">
                  <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.questions.category') }}</label>
                  <SelectInput :model-value="String(entry.categoryId ?? '')" @update:model-value="(v: string | undefined) => entry.categoryId = v ? Number(v) : null">
                    <option value="">{{ t('quiz.questions.noCategory') }}</option>
                    <option v-for="cat in catalog?.categories ?? []" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
                  </SelectInput>
                </div>
                <DeleteButton v-if="genEntries.length > 1" @click="genEntries.splice(eIdx, 1)" />
              </div>
              <SecondaryButton @click="genEntries.push({ questionType: 'MULTIPLE_CHOICE', count: 5, categoryId: null })">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                {{ t('quiz.ai.addEntry') }}
              </SecondaryButton>
            </div>
            <div>
              <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.ai.userPrompt') }}</label>
              <TextAreaInput v-model="genUserPrompt" :placeholder="t('quiz.ai.userPromptPlaceholder')" />
            </div>
            <p v-if="genResult" class="text-xs text-error">{{ genResult }}</p>
            <div class="flex justify-end gap-3">
              <PrimaryButton :disabled="genGenerating || genEntries.every(e => e.count < 1)" @click="generateQuestions">
                <Spinner v-if="genGenerating" size="sm" class="mr-1" />
                <font-awesome-icon v-else :icon="['fas', 'brain']" class="mr-1" />
                {{ genGenerating ? t('quiz.ai.generating') : t('quiz.ai.generateQuestions') }}
              </PrimaryButton>
            </div>
          </template>

          <!-- Phase 2: Review -->
          <template v-if="genPhase === 'review'">
            <!-- Progress bar -->
            <div v-if="genGenerating" class="space-y-2">
              <div class="flex justify-between text-xs text-(--text-muted)">
                <span>{{ t('quiz.ai.generating') }}</span>
                <span>{{ genPreviews.length }} / {{ genProgressTotal }}</span>
              </div>
              <div class="w-full h-2 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent overflow-hidden">
                <div class="h-full rounded-full bg-primary transition-all duration-300" :style="{ width: `${genProgressTotal > 0 ? (genPreviews.length / genProgressTotal) * 100 : 0}%` }" />
              </div>
            </div>

            <p v-if="!genGenerating" class="text-sm text-(--text-muted)">{{ t('quiz.ai.reviewHint') }}</p>
            <div class="space-y-2">
              <div
                v-for="(q, idx) in genPreviews"
                :key="idx"
                class="rounded-lg border transition-all"
                :class="q.accepted
                  ? 'border-success bg-success/5'
                  : 'border-bg-light-accent dark:border-bg-dark-accent opacity-50'"
              >
                <div class="flex items-start gap-3 p-3 cursor-pointer" @click="toggleGenPreview(idx)">
                  <font-awesome-icon
                    :icon="['fas', q.accepted ? 'square-check' : 'square']"
                    class="text-lg mt-0.5 shrink-0"
                    :class="q.accepted ? 'text-success' : 'text-(--text-muted)'"
                  />
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <p class="font-medium text-sm">{{ q.title }}</p>
                    <InfoBadge>{{ t(`quiz.questionTypes.${q.questionType}`) }}</InfoBadge>
                  </div>
                  <!-- MC: show options with checkmarks -->
                  <template v-if="q.questionType === 'MULTIPLE_CHOICE'">
                    <div class="mt-1 space-y-0.5">
                      <div v-for="(opt, oi) in (parseGenConfig(q.config).options as { text: string; correct: boolean }[] || [])" :key="oi" class="flex items-center gap-1 text-xs">
                        <font-awesome-icon :icon="['fas', opt.correct ? 'square-check' : 'square']" :class="opt.correct ? 'text-success' : 'text-(--text-muted)'" class="text-[10px]" />
                        <span :class="opt.correct ? 'font-medium' : 'text-(--text-muted)'">{{ opt.text }}</span>
                      </div>
                    </div>
                  </template>
                  <!-- TF: show correct answer -->
                  <template v-else-if="q.questionType === 'TRUE_FALSE'">
                    <p class="text-xs mt-1" :class="(parseGenConfig(q.config).correctAnswer as boolean) ? 'text-success' : 'text-error'">
                      {{ (parseGenConfig(q.config).correctAnswer as boolean) ? t('quiz.trueLabel') : t('quiz.falseLabel') }}
                    </p>
                  </template>
                  <!-- Free answer: show possible answers -->
                  <template v-else-if="q.questionType === 'FREE_ANSWER'">
                    <p v-for="(ans, ai2) in (parseGenConfig(q.config).answers as string[] || [])" :key="ai2" class="text-xs text-(--text-muted) mt-0.5">
                      {{ ai2 + 1 }}. {{ ans }}
                    </p>
                  </template>
                  <!-- Fill blank: show answers -->
                  <template v-else-if="q.questionType === 'FILL_IN_THE_BLANK'">
                    <p class="text-xs text-success mt-1">{{ (parseGenConfig(q.config).answers as string[] || []).join(', ') }}</p>
                    <p v-if="(parseGenConfig(q.config).distractors as string[] || []).length > 0" class="text-xs text-error mt-0.5">
                      {{ (parseGenConfig(q.config).distractors as string[] || []).join(', ') }}
                    </p>
                  </template>
                  <!-- Connect: show pairs -->
                  <template v-else-if="q.questionType === 'CONNECT'">
                    <div class="mt-1 space-y-0.5">
                      <p v-for="(pair, pi) in (parseGenConfig(q.config).pairs as { left: string; right: string }[] || [])" :key="pi" class="text-xs text-(--text-muted)">
                        {{ pair.left }} → {{ pair.right }}
                      </p>
                    </div>
                  </template>
                  <!-- Ordering: show items numbered -->
                  <template v-else-if="q.questionType === 'ORDERING'">
                    <div class="mt-1 space-y-0.5">
                      <p v-for="(item, ii) in (parseGenConfig(q.config).items as string[] || [])" :key="ii" class="text-xs text-(--text-muted)">
                        {{ ii + 1 }}. {{ item }}
                      </p>
                    </div>
                  </template>
                </div>
                </div>
                <!-- Regenerate button -->
                <div class="flex justify-end px-3 pb-2" @click.stop>
                  <button
                    class="text-xs text-(--text-muted) hover:text-primary transition-colors flex items-center gap-1"
                    :disabled="genRegenerating === idx"
                    @click="regenerateQuestion(idx)"
                  >
                    <Spinner v-if="genRegenerating === idx" size="sm" />
                    <font-awesome-icon v-else :icon="['fas', 'rotate']" />
                    {{ t('quiz.ai.regenerate') }}
                  </button>
                </div>
              </div>
            </div>
            <p v-if="genResult" class="text-xs text-error">{{ genResult }}</p>
            <div class="flex items-center justify-between pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
              <div class="flex gap-2">
                <SecondaryButton @click="resetGeneration">
                  <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" />
                  {{ t('common.back') }}
                </SecondaryButton>
              </div>
              <div class="flex items-center gap-3">
                <span class="text-xs text-(--text-muted)">{{ genAcceptedCount() }} / {{ genPreviews.length }}</span>
                <PrimaryButton :disabled="genGenerating || genAcceptedCount() === 0" @click="saveGeneratedQuestions">
                  <Spinner v-if="genGenerating" size="sm" class="mr-1" />
                  <font-awesome-icon v-else :icon="['fas', 'check']" class="mr-1" />
                  {{ t('quiz.ai.acceptSelected') }}
                </PrimaryButton>
              </div>
            </div>
          </template>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
