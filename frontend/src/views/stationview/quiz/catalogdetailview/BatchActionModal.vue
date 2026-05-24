/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type {QuizCategory, QuizQuestion} from '@/api/types'
import {QuizQuestionTypes} from '@/api/types'
import {quiz, ai} from '@/api'
import {getItem} from '@/api/storage'

const {t} = useI18n()

const props = defineProps<{
  show: boolean
  action: string
  questions: QuizQuestion[]
  categories: QuizCategory[]
  catalogId: number
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  done: []
  error: [message: string]
}>()

const processing = ref(false)
const progress = ref('')

// Batch fields
const batchAutoPoints = ref(true)
const batchPoints = ref(1)
const batchPointsPerCorrect = ref(1)
const batchCategoryId = ref<string>('')
const batchAiMcCorrect = ref(1)
const batchAiMcWrong = ref(3)
const batchAiConnectPairs = ref(4)
const batchAiOrderMin = ref(4)
const batchAiOrderMax = ref(6)
const batchAiFillGaps = ref(2)
const batchAiFillSentences = ref(3)

const selectedTypesSet = computed(() => new Set(props.questions.map(q => q.questionType)))

function parseConfig(q: QuizQuestion): Record<string, unknown> {
  return typeof q.config === 'string' ? JSON.parse(q.config) : JSON.parse(JSON.stringify(q.config))
}

async function execute() {
  processing.value = true
  progress.value = ''
  try {
    const targets = props.questions
    let done = 0

    if (props.action === 'autoPoints') {
      for (const q of targets) {
        done++; progress.value = `${done}/${targets.length}`
        await quiz.updateQuestion(q.id, {
          title: q.title, description: q.description, categoryId: q.categoryId,
          questionType: q.questionType, points: q.points,
          autoPoints: batchAutoPoints.value, config: parseConfig(q),
        })
      }
    } else if (props.action === 'setPoints') {
      for (const q of targets) {
        done++; progress.value = `${done}/${targets.length}`
        await quiz.updateQuestion(q.id, {
          title: q.title, description: q.description, categoryId: q.categoryId,
          questionType: q.questionType, points: batchPoints.value,
          autoPoints: false, config: parseConfig(q),
        })
      }
    } else if (props.action === 'pointsPerCorrect') {
      for (const q of targets) {
        if (q.questionType !== QuizQuestionTypes.MULTIPLE_CHOICE) continue
        done++; progress.value = `${done}/${targets.length}`
        const config = parseConfig(q)
        config.pointsPerCorrect = batchPointsPerCorrect.value
        const correctCount = ((config.options as {correct: boolean}[]) || []).filter(o => o.correct).length
        await quiz.updateQuestion(q.id, {
          title: q.title, description: q.description, categoryId: q.categoryId,
          questionType: q.questionType, points: correctCount * batchPointsPerCorrect.value,
          autoPoints: true, config,
        })
      }
    } else if (props.action === 'setCategory') {
      const catId = batchCategoryId.value ? Number(batchCategoryId.value) : null
      for (const q of targets) {
        done++; progress.value = `${done}/${targets.length}`
        await quiz.updateQuestion(q.id, {
          title: q.title, description: q.description, categoryId: catId,
          questionType: q.questionType, points: q.points,
          autoPoints: q.autoPoints, config: parseConfig(q),
        })
      }
    } else if (props.action === 'generate') {
      await batchGenerate(targets)
    }

    emit('done')
    emit('update:show', false)
  } catch {
    emit('error', t('common.error'))
  } finally {
    processing.value = false
    progress.value = ''
  }
}

async function batchGenerate(targets: QuizQuestion[]) {
  const provider = getItem('ai_provider') || 'openai'
  const apiKey = getItem('ai_api_key') || ''
  const model = getItem('ai_model') || ''
  if (!apiKey) { emit('error', t('quiz.ai.noKeyConfigured')); return }

  let done = 0
  for (const q of targets) {
    done++; progress.value = `${done}/${targets.length}`
    const type = q.questionType
    let prompt = ''
    if (type === QuizQuestionTypes.MULTIPLE_CHOICE) {
      prompt = `Generate a multiple choice question with exactly ${batchAiMcCorrect.value} correct and ${batchAiMcWrong.value} wrong answers.`
    } else if (type === QuizQuestionTypes.CONNECT) {
      prompt = `Generate a connect/matching question with exactly ${batchAiConnectPairs.value} pairs.`
    } else if (type === QuizQuestionTypes.ORDERING) {
      prompt = `Generate an ordering question with ${batchAiOrderMin.value}-${batchAiOrderMax.value} items.`
    } else if (type === QuizQuestionTypes.FILL_IN_THE_BLANK) {
      prompt = `Generate a fill-in-the-blank question with ${batchAiFillGaps.value} gaps in ${batchAiFillSentences.value} sentences.`
    } else { continue }

    try {
      const jobId = await ai.startGenerateQuestions({
        provider, apiKey, model: model || null,
        userPrompt: prompt, catalogId: props.catalogId,
        entries: [{questionType: type, count: 1, categoryId: q.categoryId}],
      })
      while (true) {
        await new Promise(r => setTimeout(r, 1500))
        const poll = await ai.pollGenerateQuestions(jobId)
        if (poll.questions.length > 0) {
          const gen = poll.questions[0]
          await quiz.updateQuestion(q.id, {
            title: gen.title, description: q.description, categoryId: q.categoryId,
            questionType: type, points: q.points, autoPoints: q.autoPoints,
            config: typeof gen.config === 'string' ? JSON.parse(gen.config) : gen.config,
          })
        }
        if (poll.done) break
      }
    } catch { /* skip */ }
  }
}
</script>

<template>
  <Modal :model-value="show" @update:model-value="emit('update:show', $event)">
    <div class="space-y-4">
      <SubHeader>{{ t(`quiz.batch.action_${action}`) }}</SubHeader>
      <MutedText>{{ questions.length }} {{ t('quiz.batch.selected') }}</MutedText>

      <template v-if="action === 'autoPoints'">
        <div class="flex items-center gap-2">
          <ToggleInput v-model="batchAutoPoints"/>
          <span class="text-sm">{{ t('quiz.batch.autoPointsLabel') }}</span>
        </div>
      </template>

      <template v-if="action === 'setPoints'">
        <div class="flex items-center gap-2">
          <FieldLabel>{{ t('quiz.questions.points') }}</FieldLabel>
          <NumberInput v-model="batchPoints" class="w-20"/>
        </div>
      </template>

      <template v-if="action === 'pointsPerCorrect'">
        <div class="flex items-center gap-2">
          <FieldLabel>{{ t('quiz.questions.config.pointsPerCorrect') }}</FieldLabel>
          <DecimalInput v-model="batchPointsPerCorrect" step="0.5" class="w-20"/>
        </div>
      </template>

      <template v-if="action === 'setCategory'">
        <SelectInput v-model="batchCategoryId">
          <option value="">{{ t('quiz.questions.noCategory') }}</option>
          <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
        </SelectInput>
      </template>

      <template v-if="action === 'generate'">
        <div class="space-y-3">
          <template v-if="selectedTypesSet.has('MULTIPLE_CHOICE')">
            <SubHeader class="text-sm">Multiple Choice</SubHeader>
            <div class="flex items-center gap-3 flex-wrap">
              <div class="flex items-center gap-1">
                <FieldLabel hint>{{ t('quiz.batch.correctCount') }}</FieldLabel>
                <NumberInput v-model="batchAiMcCorrect" class="w-14"/>
              </div>
              <div class="flex items-center gap-1">
                <FieldLabel hint>{{ t('quiz.batch.wrongCount') }}</FieldLabel>
                <NumberInput v-model="batchAiMcWrong" class="w-14"/>
              </div>
            </div>
          </template>
          <template v-if="selectedTypesSet.has('CONNECT')">
            <SubHeader class="text-sm">{{ t('quiz.questionTypes.CONNECT') }}</SubHeader>
            <div class="flex items-center gap-1">
              <FieldLabel hint>{{ t('quiz.batch.pairCount') }}</FieldLabel>
              <NumberInput v-model="batchAiConnectPairs" class="w-14"/>
            </div>
          </template>
          <template v-if="selectedTypesSet.has('ORDERING')">
            <SubHeader class="text-sm">{{ t('quiz.questionTypes.ORDERING') }}</SubHeader>
            <div class="flex items-center gap-3 flex-wrap">
              <div class="flex items-center gap-1">
                <FieldLabel hint>Min</FieldLabel>
                <NumberInput v-model="batchAiOrderMin" class="w-14"/>
              </div>
              <div class="flex items-center gap-1">
                <FieldLabel hint>Max</FieldLabel>
                <NumberInput v-model="batchAiOrderMax" class="w-14"/>
              </div>
            </div>
          </template>
          <template v-if="selectedTypesSet.has('FILL_IN_THE_BLANK')">
            <SubHeader class="text-sm">{{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}</SubHeader>
            <div class="flex items-center gap-3 flex-wrap">
              <div class="flex items-center gap-1">
                <FieldLabel hint>{{ t('quiz.batch.gapCount') }}</FieldLabel>
                <NumberInput v-model="batchAiFillGaps" class="w-14"/>
              </div>
              <div class="flex items-center gap-1">
                <FieldLabel hint>{{ t('quiz.batch.sentenceCount') }}</FieldLabel>
                <NumberInput v-model="batchAiFillSentences" class="w-14"/>
              </div>
            </div>
          </template>
        </div>
      </template>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('update:show', false)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="processing" @click="execute">
          <Spinner v-if="processing" size="sm" class="mr-1"/>
          {{ processing ? progress : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
