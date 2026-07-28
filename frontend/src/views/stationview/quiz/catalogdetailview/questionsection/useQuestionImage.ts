/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref } from 'vue'
import type { QuizQuestion } from '@/api/quiz'
import { quiz } from '@/api'

/**
 * Owns the image attached to the question currently open in the editor: the
 * locally picked file, what the editor previews and the upload or removal.
 */
export function useQuestionImage() {
  const file = ref<File | null>(null)
  const preview = ref<string | null>(null)
  const authSrc = ref<string | null>(null)
  const hasImage = ref(false)

  function reset() {
    file.value = null
    preview.value = null
    authSrc.value = null
    hasImage.value = false
  }

  function hydrate(question: QuizQuestion) {
    file.value = null
    preview.value = null
    authSrc.value = question.imageUrl ? quiz.questionImageUrl(question.id, 300) : null
    hasImage.value = !!question.imageUrl
  }

  function select(event: Event) {
    const input = event.target as HTMLInputElement
    const picked = input.files?.[0]
    if (!picked) return
    file.value = picked
    preview.value = URL.createObjectURL(picked)
    authSrc.value = null
    hasImage.value = true
  }

  async function remove(editingQuestionId: number | null) {
    if (editingQuestionId !== null && hasImage.value && !file.value) {
      try {
        await quiz.deleteQuestionImage(editingQuestionId)
      } catch {
        void 0
      }
    }
    reset()
  }

  async function upload(questionId: number) {
    if (file.value) {
      await quiz.uploadQuestionImage(questionId, file.value)
    }
  }

  return {
    file,
    preview,
    authSrc,
    hasImage,
    reset,
    hydrate,
    select,
    remove,
    upload,
  }
}
