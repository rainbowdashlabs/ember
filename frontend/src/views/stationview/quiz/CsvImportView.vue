/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import CsvImportWizard from '@/components/csv/CsvImportWizard.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { requireCsvFile, useCsvImport } from '@/composables/useCsvImport'
import type { QuizCategory } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz, ai, util } from '@/api'
import { getItem } from '@/api/storage'
import CsvColumnMapping from './csvimportview/CsvColumnMapping.vue'
import CsvQuestionPreview from './csvimportview/CsvQuestionPreview.vue'
import AiAnswerOptions from './csvimportview/AiAnswerOptions.vue'
import {
  buildImportQuestions,
  buildQuestionConfig,
  createQuizCsvMapping,
  type ImportQuestion,
  type QuizCsvMapping,
} from './csvimportview/quizCsvImport'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const { loaded } = useSession()

const catalogId = computed(() => Number(route.params.id))
const catalogName = ref('')
const categories = ref<QuizCategory[]>([])

const generateWrongAnswers = ref(false)
const wrongAnswerCount = ref(3)
const aiPrompt = ref('')
const aiStatus = ref('')
const hasAiKey = computed(() => !!getItem('ai_api_key'))

const { loading, error, reload: loadData } = useAsyncLoader(async () => {
  const detail = await quiz.getCatalog(catalogId.value)
  catalogName.value = detail.name
  categories.value = detail.categories
}, { autoLoad: false })

const importer = useCsvImport<QuizCsvMapping, ImportQuestion[], number>({
  separator: ',',
  parse: ({ file, separator }) => util.parseCsv(requireCsvFile(file), separator),
  createMapping: createQuizCsvMapping,
  validateMapping: ({ mapping }) => mapping.questionColumn ? null : t('quiz.csv.questionColumnRequired'),
  loadPreview: async ({ headers, rows, mapping }) => buildImportQuestions(headers, rows, mapping),
  validateCommit: () => questions.value.some(question => question.included) ? null : t('quiz.csv.noQuestionsSelected'),
  commit: () => importQuestions(),
  formatError: () => t('common.error'),
})

const { mapping, headers, preview, result: importedCount } = importer

const questions = computed<ImportQuestion[]>(() => preview.value ?? [])

async function findOrCreateCategory(name: string): Promise<number | null> {
  const trimmed = name.trim()
  if (!trimmed) return null
  const existing = categories.value.find(category => category.name.toLowerCase() === trimmed.toLowerCase())
  if (existing) return existing.id
  const created = await quiz.createCategory({ name: trimmed, description: '', position: categories.value.length })
  categories.value.push(created)
  return created.id
}

async function importQuestions(): Promise<number> {
  aiStatus.value = ''
  const selected = questions.value.filter(question => question.included)
  const multipleChoice: { question: ImportQuestion; id: number }[] = []
  for (const [index, question] of selected.entries()) {
    const categoryId = await findOrCreateCategory(question.category)
    const autoPoints = question.type !== QuizQuestionTypes.FREE_ANSWER && question.type !== QuizQuestionTypes.IMAGE_TEXT
    const created = await quiz.createQuestion(catalogId.value, {
      categoryId,
      quizQuestionType: question.type,
      title: question.title,
      description: '',
      points: question.points,
      autoPoints,
      config: buildQuestionConfig(question),
      position: index,
    })
    if (generateWrongAnswers.value && question.type === QuizQuestionTypes.MULTIPLE_CHOICE) {
      multipleChoice.push({ question, id: created.id })
    }
  }
  await generateMissingAnswers(multipleChoice)
  return selected.length
}

async function generateMissingAnswers(created: { question: ImportQuestion; id: number }[]) {
  const apiKey = getItem('ai_api_key') ?? ''
  if (!generateWrongAnswers.value || created.length === 0 || !apiKey) return
  const provider = getItem('ai_provider') ?? 'openai'
  const model = getItem('ai_model') ?? ''
  const catalogContext = t('quiz.csv.aiCatalogContext', { name: catalogName.value })
  for (const [index, entry] of created.entries()) {
    aiStatus.value = `${t('quiz.csv.generatingAnswers')} (${index + 1}/${created.length})`
    try {
      const config = JSON.parse(buildQuestionConfig(entry.question))
      const options: { text: string; correct: boolean }[] = config.options ?? []
      const context = aiPrompt.value ? `${aiPrompt.value}\n${catalogContext}` : catalogContext
      const wrongAnswers = await ai.generate({
        provider,
        apiKey,
        model: model || null,
        question: `${context}\n\n${entry.question.title}`,
        correctAnswer: options.filter(option => option.correct).map(option => option.text).join(', '),
        count: wrongAnswerCount.value,
      })
      for (const wrong of wrongAnswers) options.push({ text: wrong, correct: false })
      await quiz.updateQuestion(entry.id, { config: { ...config, options } })
    } catch {
      continue
    }
  }
  aiStatus.value = ''
}

function backToCatalog() {
  router.push({ name: 'quiz-catalog-detail', params: { id: catalogId.value } })
}

watch(loaded, isLoaded => { if (isLoaded) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent :title="t('pages.quiz-catalog-import.title')" :subtitle="t('pages.quiz-catalog-import.subtitle')">
    <div class="space-y-6">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="backToCatalog">
          {{ catalogName || t('common.back') }}
        </SecondaryButton>
        <SubHeader>{{ t('quiz.csv.import') }}</SubHeader>
      </div>

      <Spinner v-if="loading" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <CsvImportWizard :importer="importer" accept=".csv,.tsv,.txt">
        <template #mapping>
          <CsvColumnMapping v-model:mapping="mapping" :headers="headers" />
          <AiAnswerOptions
              v-if="hasAiKey"
              v-model:enabled="generateWrongAnswers"
              v-model:count="wrongAnswerCount"
              v-model:prompt="aiPrompt"
          />
        </template>

        <template #preview>
          <CsvQuestionPreview :questions="questions" :status="aiStatus" />
        </template>

        <template #done>
          <SuccessContainer class="space-y-4">
            <SubHeader>{{ t('quiz.csv.importSuccess', { count: importedCount ?? 0 }) }}</SubHeader>
            <div class="flex gap-3">
              <PrimaryButton :icon="['fas', 'chevron-left']" @click="backToCatalog">
                {{ catalogName || t('common.back') }}
              </PrimaryButton>
              <SecondaryButton :icon="['fas', 'rotate']" @click="importer.reset()">
                {{ t('csvImport.startOver') }}
              </SecondaryButton>
            </div>
          </SuccessContainer>
        </template>
      </CsvImportWizard>
    </div>
  </ViewContent>
</template>
