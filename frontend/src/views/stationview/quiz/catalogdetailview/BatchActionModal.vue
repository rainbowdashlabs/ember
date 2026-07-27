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
import Spinner from '@/components/feedback/Spinner.vue'
import BatchActionFields from '@/views/stationview/quiz/catalogdetailview/batchactionmodal/BatchActionFields.vue'
import BatchGenerateOptions from '@/views/stationview/quiz/catalogdetailview/batchactionmodal/BatchGenerateOptions.vue'
import type {QuizCategory, QuizQuestion} from '@/api/types'
import {QuizQuestionTypes} from '@/api/types'
import {quiz, ai} from '@/api'
import {getItem} from '@/api/storage'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()

const show = defineModel<boolean>('show', {required: true})

const props = defineProps<{
  action: string
  questions: QuizQuestion[]
  categories: QuizCategory[]
  catalogId: number
}>()

const emit = defineEmits<{
  done: []
  error: [message: string]
}>()

const progress = ref('')

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

const selectedTypesSet = computed(() => new Set(props.questions.map(q => q.quizQuestionType)))

function parseConfig(q: QuizQuestion): Record<string, unknown> {
  return { ...(q.config ?? {}) }
}

const {running: processing, run: runExecute} = useAsyncAction(async () => {
  const targets = props.questions
  let done = 0

  if (props.action === 'autoPoints') {
    for (const q of targets) {
      done++; progress.value = `${done}/${targets.length}`
      await quiz.updateQuestion(q.id, {
        title: q.title, description: q.description, categoryId: q.categoryId,
        quizQuestionType: q.quizQuestionType, points: q.points,
        autoPoints: batchAutoPoints.value, config: parseConfig(q),
      })
    }
  } else if (props.action === 'setPoints') {
    for (const q of targets) {
      done++; progress.value = `${done}/${targets.length}`
      await quiz.updateQuestion(q.id, {
        title: q.title, description: q.description, categoryId: q.categoryId,
        quizQuestionType: q.quizQuestionType, points: batchPoints.value,
        autoPoints: false, config: parseConfig(q),
      })
    }
  } else if (props.action === 'pointsPerCorrect') {
    for (const q of targets) {
      if (q.quizQuestionType !== QuizQuestionTypes.MULTIPLE_CHOICE) continue
      done++; progress.value = `${done}/${targets.length}`
      const config = parseConfig(q)
      config.pointsPerCorrect = batchPointsPerCorrect.value
      const correctCount = ((config.options as {correct: boolean}[]) || []).filter(o => o.correct).length
      await quiz.updateQuestion(q.id, {
        title: q.title, description: q.description, categoryId: q.categoryId,
        quizQuestionType: q.quizQuestionType, points: correctCount * batchPointsPerCorrect.value,
        autoPoints: true, config,
      })
    }
  } else if (props.action === 'setCategory') {
    const catId = batchCategoryId.value ? Number(batchCategoryId.value) : null
    for (const q of targets) {
      done++; progress.value = `${done}/${targets.length}`
      await quiz.updateQuestion(q.id, {
        title: q.title, description: q.description, categoryId: catId,
        quizQuestionType: q.quizQuestionType, points: q.points,
        autoPoints: q.autoPoints, config: parseConfig(q),
      })
    }
  } else if (props.action === 'generate') {
    await batchGenerate(targets)
  }

  emit('done')
  show.value = false
  return true
})

async function execute() {
  if (processing.value) return
  const ok = await runExecute()
  progress.value = ''
  if (!ok) emit('error', t('common.error'))
}

async function batchGenerate(targets: QuizQuestion[]) {
  const provider = getItem('ai_provider') || 'openai'
  const apiKey = getItem('ai_api_key') || ''
  const model = getItem('ai_model') || ''
  if (!apiKey) { emit('error', t('quiz.ai.noKeyConfigured')); return }

  let done = 0
  for (const q of targets) {
    done++; progress.value = `${done}/${targets.length}`
    const type = q.quizQuestionType
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
            quizQuestionType: type, points: q.points, autoPoints: q.autoPoints,
            config: gen.config,
          })
        }
        if (poll.done) break
      }
    } catch {
      continue
    }
  }
}
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SubHeader>{{ t(`quiz.batch.action_${action}`) }}</SubHeader>
      <MutedText>{{ questions.length }} {{ t('quiz.batch.selected') }}</MutedText>

      <BatchActionFields
        :action="action"
        :categories="categories"
        v-model:auto-points="batchAutoPoints"
        v-model:points="batchPoints"
        v-model:points-per-correct="batchPointsPerCorrect"
        v-model:category-id="batchCategoryId"
      />

      <template v-if="action === 'generate'">
        <BatchGenerateOptions
          :selected-types="selectedTypesSet"
          v-model:mc-correct="batchAiMcCorrect"
          v-model:mc-wrong="batchAiMcWrong"
          v-model:connect-pairs="batchAiConnectPairs"
          v-model:order-min="batchAiOrderMin"
          v-model:order-max="batchAiOrderMax"
          v-model:fill-gaps="batchAiFillGaps"
          v-model:fill-sentences="batchAiFillSentences"
        />
      </template>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="processing" @click="execute">
          <Spinner v-if="processing" size="sm" class="mr-1"/>
          {{ processing ? progress : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
