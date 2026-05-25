/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import AiSettingsPanel from './cataloggenerateview/AiSettingsPanel.vue'
import {quiz, ai} from '@/api'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'
import {QuizQuestionTypes} from '@/api/types'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const catalogId = computed(() => Number(route.params.id))
const catalogName = ref('')
const loading = ref(true)
const error = ref('')

// Config phase
const countMode = ref<string>('fillTo')
const count = ref(6)

// Generation phase
const generating = ref(false)
const generatingProgress = ref('')

interface ReviewQuestion {
  questionId: number
  title: string
  description: string
  categoryId: number | null
  points: number
  autoPoints: boolean
  existingOptions: { text: string; correct: boolean }[]
  existingConfig: Record<string, unknown>
  newAnswers: string[]
}

const reviewItems = ref<ReviewQuestion[]>([])
const phase = ref<'config' | 'review'>('config')

// Save phase
const saving = ref(false)
const savedCount = ref(0)

async function loadData() {
  loading.value = true
  try {
    const detail = await quiz.getCatalog(catalogId.value)
    catalogName.value = detail.name
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function generate() {
  const provider = getItem('ai_provider') || 'openai'
  const apiKey = getItem('ai_api_key') || ''
  const model = getItem('ai_model') || ''
  if (!apiKey) {
    error.value = t('quiz.ai.noKeyConfigured')
    return
  }

  generating.value = true
  error.value = ''
  reviewItems.value = []

  try {
    const questions = await quiz.listQuestions(catalogId.value)
    const mcQuestions = questions.filter(q => q.questionType === QuizQuestionTypes.MULTIPLE_CHOICE)
    let done = 0

    for (const q of mcQuestions) {
      done++
      generatingProgress.value = `${done}/${mcQuestions.length}`
      const config = typeof q.config === 'string' ? JSON.parse(q.config) : JSON.parse(JSON.stringify(q.config))
      const options: { text: string; correct: boolean }[] = config.options || []
      const correctAnswers = options.filter(o => o.correct).map(o => o.text)
      if (correctAnswers.length === 0) continue

      const needed = countMode.value === 'fillTo'
          ? Math.max(0, count.value - options.length)
          : count.value
      if (needed <= 0) continue

      try {
        const results = await ai.generate({
          provider, apiKey, model: model || null,
          question: q.title, correctAnswer: correctAnswers.join(', '), count: needed,
        })
        if (results.length > 0) {
          reviewItems.value.push({
            questionId: q.id,
            title: q.title,
            description: q.description,
            categoryId: q.categoryId,
            points: q.points,
            autoPoints: q.autoPoints,
            existingOptions: options,
            existingConfig: config,
            newAnswers: results,
          })
        }
      } catch { /* skip */ }
    }

    phase.value = 'review'
  } catch {
    error.value = t('common.error')
  } finally {
    generating.value = false
    generatingProgress.value = ''
  }
}

function removeAnswer(qIdx: number, aIdx: number) {
  reviewItems.value[qIdx].newAnswers.splice(aIdx, 1)
}

function editAnswer(qIdx: number, aIdx: number, value: string) {
  reviewItems.value[qIdx].newAnswers[aIdx] = value
}

function removeQuestion(qIdx: number) {
  reviewItems.value.splice(qIdx, 1)
}

const totalNewAnswers = computed(() => reviewItems.value.reduce((sum, q) => sum + q.newAnswers.length, 0))

async function saveAll() {
  saving.value = true
  savedCount.value = 0
  try {
    for (const item of reviewItems.value) {
      if (item.newAnswers.length === 0) continue
      const allOptions = [
        ...item.existingOptions,
        ...item.newAnswers.map(text => ({text, correct: false})),
      ]
      const updatedConfig = {...item.existingConfig, options: allOptions}
      // Recalculate points if autoPoints is enabled
      let points = item.points
      if (item.autoPoints) {
        const correctCount = allOptions.filter(o => o.correct).length
        const ppc = (item.existingConfig.pointsPerCorrect as number) ?? 1
        points = correctCount * ppc
      }
      await quiz.updateQuestion(item.questionId, {
        title: item.title,
        description: item.description,
        categoryId: item.categoryId,
        questionType: 'MULTIPLE_CHOICE',
        points,
        autoPoints: item.autoPoints,
        config: updatedConfig,
      })
      savedCount.value++
    }
    router.push({name: 'quiz-catalog-detail', params: {id: catalogId.value}})
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

watch(loaded, v => { if (v) loadData() }, {immediate: true})
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({name: 'quiz-catalog-detail', params: {id: catalogId}})">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1"/>
        {{ catalogName || t('common.back') }}
      </SecondaryButton>
      <SectionHeader>{{ t('quiz.ai.fillMcAnswers') }}</SectionHeader>
    </div>

    <Spinner v-if="loading"/>
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <!-- Config phase -->
    <template v-if="phase === 'config' && !loading">
      <AiSettingsPanel :catalog-id="catalogId" class="mb-4"/>

      <NeutralContainer class="space-y-4 max-w-md">
        <MutedText tag="p">{{ t('quiz.ai.fillMcHint') }}</MutedText>

        <div class="flex items-center gap-3">
          <ToggleSwitch v-model="countMode" option-a="add" option-b="fillTo"
                        :label-a="t('quiz.ai.modeAdd')" :label-b="t('quiz.ai.modeFillTo')"/>
          <NumberInput v-model="count" class="w-16"/>
        </div>

        <PrimaryButton :disabled="generating" @click="generate">
          <Spinner v-if="generating" size="sm" class="mr-1"/>
          <font-awesome-icon v-else :icon="['fas', 'brain']" class="mr-1"/>
          {{ generating ? generatingProgress : t('quiz.ai.generate') }}
        </PrimaryButton>
      </NeutralContainer>
    </template>

    <!-- Review phase -->
    <template v-if="phase === 'review'">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
        <MutedText>
          {{ reviewItems.length }} {{ t('quiz.ai.questionsWithNewAnswers') }} · {{ totalNewAnswers }} {{ t('quiz.ai.newAnswersTotal') }}
        </MutedText>
        <div class="flex gap-2">
          <SecondaryButton @click="phase = 'config'">{{ t('common.back') }}</SecondaryButton>
          <PrimaryButton :disabled="saving || totalNewAnswers === 0" @click="saveAll">
            <Spinner v-if="saving" size="sm" class="mr-1"/>
            {{ t('common.save') }}
          </PrimaryButton>
        </div>
      </div>

      <div v-if="reviewItems.length === 0" class="text-center py-8 text-(--text-muted)">
        {{ t('quiz.ai.noNewAnswers') }}
      </div>

      <div class="space-y-3">
        <NeutralContainer v-for="(item, qIdx) in reviewItems" :key="item.questionId">
          <div class="flex items-start justify-between gap-2 mb-3">
            <SubHeader>{{ item.title }}</SubHeader>
            <IconButton :icon="['fas', 'xmark']" :label="t('common.remove')" class="text-(--text-muted) hover:text-error shrink-0" @click="removeQuestion(qIdx)"/>
          </div>

          <div class="space-y-1.5">
            <!-- Existing correct answers -->
            <div v-for="opt in item.existingOptions.filter(o => o.correct)" :key="'c-' + opt.text"
                 class="flex items-center gap-2 px-3 py-1.5 rounded bg-success/10 border border-success/30">
              <font-awesome-icon :icon="['fas', 'check']" class="text-success text-xs shrink-0"/>
              <span class="text-sm">{{ opt.text }}</span>
            </div>
            <!-- Existing wrong answers -->
            <div v-for="opt in item.existingOptions.filter(o => !o.correct)" :key="'e-' + opt.text"
                 class="flex items-center gap-2 px-3 py-1.5 rounded bg-[var(--bg-accent)]">
              <font-awesome-icon :icon="['fas', 'xmark']" class="text-(--text-muted) text-xs shrink-0"/>
              <span class="text-sm text-(--text-muted)">{{ opt.text }}</span>
            </div>
            <!-- New generated answers (highlighted) -->
            <div v-for="(answer, aIdx) in item.newAnswers" :key="'n-' + aIdx"
                 class="flex items-center gap-2 rounded border-2 border-primary/40 bg-primary/5">
              <font-awesome-icon :icon="['fas', 'star']" class="text-primary text-xs shrink-0 ml-3"/>
              <TextInput :model-value="answer" class="flex-1 !border-0 !bg-transparent !ring-0 !shadow-none"
                         @update:model-value="(v: string | undefined) => editAnswer(qIdx, aIdx, v ?? '')"/>
              <DeleteButton @click="removeAnswer(qIdx, aIdx)" class="mr-1"/>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </template>
  </ViewContent>
</template>
