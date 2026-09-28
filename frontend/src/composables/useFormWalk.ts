/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {followingPage, longestFrom, type PathPage} from '@/util/formPath'
import {isEmptyAnswer, type AnswerValue} from '@/util/formAnswers'

/** A question as the walk needs it: where it stands, what kind it is, and whether it must be answered. */
export interface WalkQuestion {
    id: number
    pageKey: string
    required: boolean
}

/**
 * Going through a form one page at a time, shared by every screen a form is filled in on.
 *
 * <p>Back goes to the page the reader actually came from, which with pages that skip ahead is not
 * always the page above. Answers stay for every page visited and are only sent at the end; the
 * required questions of a page are checked when it is left, so nobody is told on page four that page
 * one was incomplete.
 *
 * <p>Progress measures against the longest path still possible, since how many pages are left
 * depends on answers not given yet. It never goes backwards on the way forward.
 *
 * @param pages     the form's pages, in their order
 * @param questions the form's questions, page by page and in the order this reader sees them
 * @param answers   the answers given so far, by question id
 * @param typeOf    what kind a question is, which decides when its answer counts as empty
 */
export function useFormWalk<P extends PathPage, Q extends WalkQuestion>(
    pages: Ref<P[]>,
    questions: Ref<Q[]>,
    answers: Ref<Record<number, AnswerValue>>,
    typeOf: (question: Q) => string,
) {
    const {t} = useI18n()

    const history = ref<string[]>([])
    const current = ref<string | null>(null)
    const errors = ref<Record<number, string>>({})

    /** Starts at the first page again, with nothing marked. */
    function restart() {
        history.value = []
        current.value = pages.value[0]?.key ?? null
        errors.value = {}
    }

    watch(pages, restart, {immediate: true})

    const currentPage = computed(() => pages.value.find(page => page.key === current.value) ?? null)
    const currentQuestions = computed(() => questions.value.filter(question => question.pageKey === current.value))
    const following = computed(() => (current.value ? followingPage(pages.value, current.value) : null))

    /** Whether the page shown is the one the form is sent from. */
    const isLast = computed(() => following.value === null)

    /** Whether the form has more than one page, which is the only case anything about pages is shown. */
    const paged = computed(() => pages.value.length > 1)

    /** The number of the page shown along the reader's own path, counting from one. */
    const pageNumber = computed(() => history.value.length + 1)

    /** How far along the reader is, from above zero up to one on the page the form is sent from. */
    const progress = computed(() => {
        if (!current.value) return 0
        const remaining = longestFrom(pages.value, current.value)
        return pageNumber.value / (pageNumber.value - 1 + Math.max(1, remaining))
    })

    /** Every page visited so far and the one shown, in the order the reader went through them. */
    const path = computed(() => (current.value ? [...history.value, current.value] : [...history.value]))

    /**
     * Marks the required questions of the page shown that are still empty.
     *
     * @return whether the page may be left
     */
    function checkCurrent(): boolean {
        const missing: Record<number, string> = {}
        for (const question of currentQuestions.value) {
            if (question.required && isEmptyAnswer(typeOf(question), answers.value[question.id])) {
                missing[question.id] = t('forms.fill.required')
            }
        }
        errors.value = missing
        return Object.keys(missing).length === 0
    }

    /** Goes on to the page that follows, once the page shown is complete. */
    function next(): boolean {
        if (!checkCurrent() || !current.value || following.value === null) return false
        history.value = [...history.value, current.value]
        current.value = following.value
        return true
    }

    /** Goes back to the page the reader came from. */
    function back() {
        const previous = history.value[history.value.length - 1]
        if (previous === undefined) return
        history.value = history.value.slice(0, -1)
        current.value = previous
        errors.value = {}
    }

    /**
     * Puts the reader on a page of their path with the given questions marked, which is how an answer
     * the server refused is shown: on its page, at its question.
     *
     * @param walked the pages visited, the page to show last
     * @param marked what to say at each question, by question id
     */
    function showAt(walked: string[], marked: Record<number, string>) {
        const target = walked[walked.length - 1]
        if (target === undefined) return
        history.value = walked.slice(0, -1)
        current.value = target
        errors.value = marked
    }

    return {
        current, currentPage, currentQuestions, isLast, paged, pageNumber, progress, path, errors,
        checkCurrent, next, back, restart, showAt,
    }
}
