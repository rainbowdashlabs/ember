/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import {QuizQuestionTypes, type QuizQuestion, type QuizQuestionTypeName} from '@/api/quiz'

/**
 * Owns what the question list currently shows and which of its rows are ticked:
 * the type and category filters plus the batch selection derived from them.
 */
export function useQuestionListState(
    questions: Ref<QuizQuestion[]>,
    typeLabel: (type: QuizQuestionTypeName) => string,
) {
  const filterType = ref<string>('')
  const filterCategory = ref<string>('')

  const filteredQuestions = computed(() => {
    return questions.value.filter(q => {
      if (filterType.value && q.quizQuestionType !== filterType.value) return false
      if (filterCategory.value === 'none' && q.categoryId !== null) return false
      if (filterCategory.value && filterCategory.value !== 'none' && q.categoryId !== Number(filterCategory.value)) return false
      return true
    })
  })

  const questionTypeOptions = computed(() => {
    const types = new Set(questions.value.map(q => q.quizQuestionType))
    return [...types].map(type => ({value: type as string, label: typeLabel(type)}))
  })

  const selectedIds = ref(new Set<number>())

  function toggleSelect(id: number) {
    const s = new Set(selectedIds.value)
    if (s.has(id)) s.delete(id); else s.add(id)
    selectedIds.value = s
  }

  function selectAll() {
    selectedIds.value = new Set(filteredQuestions.value.map(q => q.id))
  }

  function deselectAll() {
    selectedIds.value = new Set()
  }

  const selectedQuestions = computed(() => questions.value.filter(q => selectedIds.value.has(q.id)))
  const hasSelection = computed(() => selectedIds.value.size > 0)
  const selectedHasMc = computed(() => selectedQuestions.value.some(q => q.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE))

  return {
    filterType,
    filterCategory,
    filteredQuestions,
    questionTypeOptions,
    selectedIds,
    toggleSelect,
    selectAll,
    deselectAll,
    selectedQuestions,
    hasSelection,
    selectedHasMc,
  }
}
