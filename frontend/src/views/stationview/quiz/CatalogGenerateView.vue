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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { QuizCatalogDetail } from '@/api/types'
import { quiz, ai as aiApi } from '@/api'
import { useSession } from '@/composables/useSession'
import AiSettingsPanel from './cataloggenerateview/AiSettingsPanel.vue'
import GenerationConfigForm from './cataloggenerateview/GenerationConfigForm.vue'
import GenerationReviewPanel from './cataloggenerateview/GenerationReviewPanel.vue'
import type { GenEntry } from './cataloggenerateview/GenerationConfigForm.vue'
import type { GenPreview } from './cataloggenerateview/GenerationReviewPanel.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()

const catalogId = computed(() => Number(route.params.id))

const catalog = ref<QuizCatalogDetail | null>(null)
const loading = ref(true)
const error = ref('')

// AI question generation
const genGenerating = ref(false)
const genResult = ref('')
const genPhase = ref<'config' | 'review'>('config')
const genProgressTotal = ref(0)
const genRegenerating = ref<number | null>(null)
const genPreviews = ref<GenPreview[]>([])

const aiSettingsRef = ref<InstanceType<typeof AiSettingsPanel> | null>(null)

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

// --- AI Question Generation ---

function getAiParams() {
  const panel = aiSettingsRef.value
  return {
    provider: panel?.getProvider() ?? 'openai',
    apiKey: panel?.getTransientKey() || null,
    model: panel?.getModel() || null,
  }
}

async function generateQuestions(entries: GenEntry[], userPrompt: string) {
  genGenerating.value = true
  genResult.value = ''
  genPreviews.value = []
  const totalCount = entries.reduce((sum, e) => sum + e.count, 0)
  genProgressTotal.value = totalCount
  genPhase.value = 'review'

  try {
    const ai = getAiParams()
    const jobId = await aiApi.startGenerateQuestions({
      ...ai,
      userPrompt: userPrompt || null,
      locale: 'de',
      catalogId: catalogId.value,
      entries: entries.filter(e => e.count > 0),
    })

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
    const ai = getAiParams()
    const generated = await aiApi.generateQuestions({
      ...ai,
      userPrompt: null,
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

function resetGeneration() {
  genPhase.value = 'config'
  genPreviews.value = []
  genResult.value = ''
}

onMounted(() => {
  if (loaded.value) {
    loadData()
  }
})

watch(loaded, (isLoaded) => {
  if (isLoaded) {
    loadData()
  }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'quiz-catalog-detail', params: { id: catalogId } })">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && catalog">
        <AiSettingsPanel ref="aiSettingsRef" :catalog-id="catalogId" />

        <template v-if="genPhase === 'config'">
          <GenerationConfigForm
            :categories="catalog?.categories ?? []"
            :generating="genGenerating"
            :error="genResult"
            @generate="generateQuestions"
          />
        </template>

        <template v-if="genPhase === 'review'">
          <div class="space-y-3">
            <SectionHeader>{{ t('quiz.ai.generateQuestions') }}</SectionHeader>
            <GenerationReviewPanel
              :previews="genPreviews"
              :generating="genGenerating"
              :regenerating-index="genRegenerating"
              :progress-total="genProgressTotal"
              :error="genResult"
              @toggle="toggleGenPreview"
              @regenerate="regenerateQuestion"
              @save="saveGeneratedQuestions"
              @back="resetGeneration"
            />
          </div>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
