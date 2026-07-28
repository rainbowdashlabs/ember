/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useAsyncAction } from '@/composables/useAsyncAction'
import type { QuizQuestion, QuizQuestionTypeName } from '@/api/quiz'
import { QuizQuestionTypes } from '@/api/quiz'
import { quiz } from '@/api'
import { defaultConfigFor } from './questionDefaultConfig'
import { useQuestionImage } from './useQuestionImage'

/**
 * Owns the inline question editor: which card is expanded, the draft values it
 * edits and the create or update round trip that closes it again.
 */
export function useQuestionForm(
    catalogId: Ref<number>,
    onUpdated: () => void,
    onSaveFailed: () => void,
) {
  const image = useQuestionImage()

  const expandedQuestion = ref<number | 'new' | null>(null)
  const editingQuestion = ref<QuizQuestion | null>(null)
  const questionTitle = ref('')
  const questionDescription = ref('')
  const questionType = ref<QuizQuestionTypeName>(QuizQuestionTypes.MULTIPLE_CHOICE)
  const questionCategoryId = ref<number | null>(null)
  const questionPoints = ref(1)
  const questionAutoPoints = ref(true)
  const questionConfig = ref<Record<string, unknown>>({})

  function expandNewQuestion() {
    if (expandedQuestion.value === 'new') {
      expandedQuestion.value = null
      return
    }
    editingQuestion.value = null
    questionTitle.value = ''
    questionDescription.value = ''
    questionType.value = QuizQuestionTypes.MULTIPLE_CHOICE
    questionCategoryId.value = null
    questionPoints.value = 1
    questionAutoPoints.value = true
    image.reset()
    questionConfig.value = defaultConfigFor(QuizQuestionTypes.MULTIPLE_CHOICE)
    expandedQuestion.value = 'new'
  }

  function expandEditQuestion(q: QuizQuestion) {
    if (expandedQuestion.value === q.id) {
      expandedQuestion.value = null
      return
    }
    editingQuestion.value = q
    questionTitle.value = q.title
    questionDescription.value = q.description
    questionType.value = q.quizQuestionType
    questionCategoryId.value = q.categoryId
    questionPoints.value = q.points
    questionAutoPoints.value = q.autoPoints
    image.hydrate(q)
    questionConfig.value = JSON.parse(JSON.stringify(q.config ?? defaultConfigFor(q.quizQuestionType)))
    expandedQuestion.value = q.id
  }

  function onQuestionTypeChange(val: QuizQuestionTypeName) {
    questionType.value = val
    questionConfig.value = defaultConfigFor(val)
  }

  function collapseQuestion() {
    expandedQuestion.value = null
  }

  function removeImage() {
    return image.remove(editingQuestion.value?.id ?? null)
  }

  const {running: savingQuestion, run: runSaveQuestion} = useAsyncAction(async () => {
    const data: Record<string, unknown> = {
      title: questionTitle.value.trim(),
      description: questionDescription.value.trim(),
      quizQuestionType: questionType.value,
      categoryId: questionCategoryId.value,
      points: questionPoints.value,
      autoPoints: questionAutoPoints.value,
      imageUrl: image.hasImage.value ? 'uploaded' : null,
      config: questionConfig.value,
    }
    let questionId: number
    if (editingQuestion.value) {
      const updated = await quiz.updateQuestion(editingQuestion.value.id, data)
      questionId = updated.id
    } else {
      const created = await quiz.createQuestion(catalogId.value, data)
      questionId = created.id
    }
    await image.upload(questionId)
    expandedQuestion.value = null
    onUpdated()
    return true
  })

  async function saveQuestion() {
    if (!questionTitle.value.trim() || savingQuestion.value) return
    const saved = await runSaveQuestion()
    if (!saved) onSaveFailed()
  }

  return {
    expandedQuestion,
    questionTitle,
    questionDescription,
    questionType,
    questionCategoryId,
    questionPoints,
    questionAutoPoints,
    questionImagePreview: image.preview,
    questionAuthImageSrc: image.authSrc,
    questionHasImage: image.hasImage,
    questionConfig,
    expandNewQuestion,
    expandEditQuestion,
    onQuestionTypeChange,
    collapseQuestion,
    onImageSelected: image.select,
    removeImage,
    saveQuestion,
  }
}
