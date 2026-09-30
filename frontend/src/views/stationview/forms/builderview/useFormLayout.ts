/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref } from 'vue'
import { PageTargetKind, QuestionTypes, type FormLayout, type FormLayoutRequest, type PageTarget, type QuestionType } from '@/api/forms'
import { freshKey, optionKeysOf } from '@/util/formOptions'
import { reachablePages } from '@/util/formPath'
import { defaultConfig } from './questionDefaults'
import { storedDraftId, storedQuestionId, type PageDraft, type QuestionDraft } from './types'

const NEXT: PageTarget = { kind: PageTargetKind.NEXT, page: null }

/** An empty page with the given key, which leads on to the page below. */
export function blankPage(key: string): PageDraft {
  return { key, title: '', description: '', after: { ...NEXT }, questions: [] }
}

/**
 * Whether a target still leads further down from the page at the given position. A chosen page that
 * now stands above, or no longer exists, does not.
 */
function leadsForward(pages: readonly PageDraft[], from: number, target: PageTarget): boolean {
  if (target.kind !== PageTargetKind.PAGE) return true
  const to = pages.findIndex(page => page.key === target.page)
  return to > from
}

/**
 * Puts every page that no longer leads further down back to leading on to the page below, and drops
 * every answer's target that no longer does.
 *
 * <p>Moving and removing pages can leave a page pointing at one that now stands above it or is gone.
 * Pages only ever lead forward, so such a target is reset rather than kept as a loop.
 */
export function keepTargetsForward(pages: PageDraft[]) {
  pages.forEach((page, index) => {
    if (!leadsForward(pages, index, page.after)) page.after = { ...NEXT }
    for (const question of page.questions) {
      if (!question.branch) continue
      question.branch = Object.fromEntries(Object.entries(question.branch)
          .filter(([, target]) => leadsForward(pages, index, target)))
    }
  })
}

/** Whether a question can decide where its page leads: a choice that takes one answer. */
export function canDecide(question: QuestionDraft): boolean {
  return question.questionType === QuestionTypes.CHOICE && !question.config.multiSelect
}

/**
 * The branch a question is saved with: only on a question that can decide, and only for the options
 * it still has, since options can be removed and a choice turned into several answers after a branch
 * was set.
 */
function savedBranch(question: QuestionDraft): Record<string, PageTarget> | null {
  if (!question.branch || !canDecide(question)) return null
  const options = new Set(optionKeysOf(question.config))
  return Object.fromEntries(Object.entries(question.branch).filter(([key]) => options.has(key)))
}

/**
 * The pages and questions of the form being edited, and everything that changes their shape.
 *
 * <p>Each page holds its own questions, which is what lets a question be dragged or moved from one
 * page to another. The server gets them back as two lists, each question naming its page.
 */
export function useFormLayout() {
  const pages = ref<PageDraft[]>([blankPage('p0')])

  const allQuestions = computed(() => pages.value.flatMap(page => page.questions))

  let lastTempId = 0

  /**
   * An id for a question that is not saved yet, after every one the editor holds and every one it
   * handed out. The pages can be replaced wholesale, as restoring unsaved questions from the browser
   * does, so what is there counts as much as what this editor gave out itself.
   */
  function freshTempId(): string {
    const held = allQuestions.value.map(question => Number(/^temp-(\d+)$/.exec(question.id)?.[1] ?? 0))
    lastTempId = Math.max(lastTempId, ...held) + 1
    return `temp-${lastTempId}`
  }

  /** Whether the form has more than one page, which is when anything about pages is shown at all. */
  const paged = computed(() => pages.value.length > 1)

  /** The pages some path reaches, whatever is answered. */
  const reachable = computed(() => reachablePages(pages.value, pages.value.flatMap(page => page.questions
    .map(question => ({ id: 0, pageKey: page.key, branch: savedBranch(question) })))))

  /** Takes the form as the server holds it. */
  function load(stored: FormLayout) {
    const byPage = new Map<string, QuestionDraft[]>()
    for (const question of stored.questions) {
      const list = byPage.get(question.pageKey) ?? []
      list.push({
        id: storedDraftId(question.id),
        questionType: question.formQuestionType,
        title: question.title,
        description: question.description,
        required: question.required,
        shuffle: question.shuffle,
        config: typeof question.config === 'object' ? { ...question.config } : {},
        branch: question.branch ? { ...question.branch } : null,
      })
      byPage.set(question.pageKey, list)
    }
    pages.value = stored.pages.length === 0
      ? [blankPage('p0')]
      : stored.pages.map(page => ({
        key: page.key,
        title: page.title,
        description: page.description,
        after: { ...page.after },
        questions: byPage.get(page.key) ?? [],
      }))
  }

  /** The form as the server takes it. */
  function toRequest(): FormLayoutRequest {
    return {
      pages: pages.value.map(page => ({
        key: page.key, title: page.title, description: page.description, after: page.after,
      })),
      questions: pages.value.flatMap(page => page.questions.map(q => ({
        id: storedQuestionId(q),
        pageKey: page.key,
        questionType: q.questionType,
        title: q.title,
        description: q.description,
        required: q.required,
        shuffle: q.shuffle,
        config: { ...q.config, questionType: q.questionType },
        branch: savedBranch(q),
      }))),
    }
  }

  /**
   * Takes the ids the server gave new questions, which it answers in the order they were sent. A
   * second save after a later step failed then changes those questions instead of adding them again.
   */
  function adoptIds(stored: FormLayout) {
    allQuestions.value.forEach((draft, index) => {
      const question = stored.questions[index]
      if (question) draft.id = storedDraftId(question.id)
    })
  }

  function addPage(afterIndex: number) {
    const key = freshKey(new Set(pages.value.map(page => page.key)))
    pages.value.splice(afterIndex + 1, 0, blankPage(key))
  }

  /**
   * Removes a page and keeps its questions, which join the page above it, or the page below where it
   * was the first. They stop deciding where a page leads, as a question moved to another page does:
   * the page they decided for is gone, and the page they join may have its own deciding question. The
   * last page is never removed: a form always has one.
   */
  function removePage(index: number) {
    if (pages.value.length < 2) return
    const [removed] = pages.value.splice(index, 1)
    const neighbour = pages.value[Math.max(0, index - 1)]
    if (removed && neighbour) {
      for (const question of removed.questions) question.branch = null
      if (index === 0) neighbour.questions.unshift(...removed.questions)
      else neighbour.questions.push(...removed.questions)
    }
    keepTargetsForward(pages.value)
  }

  /** Takes the pages in the order dragging them left them in. */
  function reorderPages(next: PageDraft[]) {
    pages.value = next
  }

  /** Puts a page in place of the one at the given index, as an editor of that page hands it back. */
  function replacePage(index: number, page: PageDraft) {
    pages.value[index] = page
  }

  function movePage(index: number, direction: -1 | 1) {
    const to = index + direction
    const page = pages.value[index]
    const target = pages.value[to]
    if (!page || !target) return
    pages.value[index] = target
    pages.value[to] = page
    keepTargetsForward(pages.value)
  }

  function addQuestion(pageIndex: number, type: QuestionType) {
    pages.value[pageIndex]?.questions.push({
      id: freshTempId(),
      questionType: type,
      title: '',
      description: '',
      required: false,
      shuffle: false,
      config: defaultConfig(type),
      branch: null,
    })
  }

  function removeQuestion(pageIndex: number, index: number) {
    pages.value[pageIndex]?.questions.splice(index, 1)
  }

  function moveQuestion(pageIndex: number, index: number, direction: -1 | 1) {
    const questions = pages.value[pageIndex]?.questions
    const current = questions?.[index]
    const target = questions?.[index + direction]
    if (!questions || !current || !target) return
    questions[index] = target
    questions[index + direction] = current
  }

  /**
   * Moves a question to the end of another page. It stops deciding where its old page leads, since the
   * page it decided for is not the one it stands on any more.
   */
  function moveToPage(pageIndex: number, index: number, targetPageIndex: number) {
    const [question] = pages.value[pageIndex]?.questions.splice(index, 1) ?? []
    if (!question) return
    question.branch = null
    pages.value[targetPageIndex]?.questions.push(question)
  }

  /**
   * Makes the given question the one that decides where its page leads, or none where no question is
   * given. A page has one such question at most.
   */
  function setDeciding(pageIndex: number, questionId: string | null) {
    for (const question of pages.value[pageIndex]?.questions ?? []) {
      if (question.id === questionId) question.branch = question.branch ?? {}
      else question.branch = null
    }
  }

  /**
   * Puts a copy of a question directly below it. The copy's options get keys of their own, so its
   * answers can never be taken for the original's, and it decides nothing about where the page leads.
   */
  function duplicateQuestion(pageIndex: number, index: number) {
    const questions = pages.value[pageIndex]?.questions
    const original = questions?.[index]
    if (!questions || !original) return
    const config = JSON.parse(JSON.stringify(original.config)) as Record<string, unknown>
    for (const field of ['options', 'statements'] as const) {
      const list = config[field]
      if (!Array.isArray(list)) continue
      const used = new Set<string>()
      config[field] = list.map(option => {
        const key = freshKey(used)
        used.add(key)
        return { ...option, key }
      })
    }
    questions.splice(index + 1, 0, { ...original, id: freshTempId(), config, branch: null })
  }

  /** The number a question is shown with, counted across every page. */
  function numberOf(question: QuestionDraft): number {
    return allQuestions.value.indexOf(question) + 1
  }

  return {
    pages, allQuestions, paged, reachable,
    load, toRequest, adoptIds, addPage, removePage, movePage, reorderPages, replacePage,
    addQuestion, removeQuestion, moveQuestion, moveToPage, setDeciding, duplicateQuestion, numberOf,
  }
}

/** The editor's pages and questions, as the components that draw them are handed it. */
export type FormLayoutEditor = ReturnType<typeof useFormLayout>
