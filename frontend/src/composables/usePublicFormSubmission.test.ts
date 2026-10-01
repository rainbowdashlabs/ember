/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {defineComponent, ref} from 'vue'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import type {PublicForm} from '@/api/generated/schema'
import {readFormDraft, saveFormDraft} from '@/util/formDrafts'
import {usePublicFormSubmission} from './usePublicFormSubmission'

vi.mock('vue-i18n', async (original) => ({
    ...(await original<typeof import('vue-i18n')>()),
    useI18n: () => ({t: (key: string) => key}),
}))

const getPublicForm = vi.fn()
const submitPublicResponse = vi.fn()

vi.mock('@/api', () => ({
    publicForms: {
        getPublicForm: (...args: unknown[]) => getPublicForm(...args),
        getSharedForm: vi.fn(),
        submitPublicResponse: (...args: unknown[]) => submitPublicResponse(...args),
        submitSharedResponse: vi.fn(),
    },
}))

const DRAFT_KEY = 'st/pf'
const next = {kind: 'NEXT' as const}

/** Two pages: a name on the first, a ranking that starts full on the second. */
function twoPageForm(): PublicForm {
    return {
        publicUid: 'pf',
        title: 'Sommerfest',
        description: '',
        purpose: 'POLL',
        state: 'OPEN',
        closedSince: null,
        completion: null,
        shuffleQuestions: false,
        pages: [
            {key: 'p0', title: '', description: '', after: next},
            {key: 'p1', title: '', description: '', after: next},
        ],
        questions: [
            {id: 1, questionType: 'TEXT', title: 'Name', description: '', required: false, shuffle: false, pageKey: 'p0', config: {questionType: 'TEXT', longAnswer: false}, branch: null},
            {
                id: 2, questionType: 'RANKING', title: 'Essen', description: '', required: false, shuffle: false, pageKey: 'p1',
                config: {questionType: 'RANKING', options: [{key: 'a', label: 'Grillen'}, {key: 'b', label: 'Kuchen'}]},
                branch: null,
            },
        ],
    }
}

/** The composable reaches for lifecycle hooks, so it is used from inside a component as the pages do. */
function open(preloaded: PublicForm | null = null) {
    let api: ReturnType<typeof usePublicFormSubmission> | null = null
    mount(defineComponent({
        setup() {
            api = usePublicFormSubmission(ref('st'), ref('pf'), ref(null), ref(preloaded))
            return () => null
        },
    }))
    return api as unknown as ReturnType<typeof usePublicFormSubmission>
}

/**
 * A public form keeps what a visitor filled in, in their browser, from the first change on: it
 * continues on the page they stopped on, keeps nothing for merely opening it, and nothing once sent.
 *
 * @vitest-environment happy-dom
 */
describe('usePublicFormSubmission', () => {
    beforeEach(() => {
        localStorage.clear()
        localStorage.setItem('storage_consent', 'accepted')
        vi.useFakeTimers()
        getPublicForm.mockReset().mockResolvedValue(twoPageForm())
        submitPublicResponse.mockReset().mockResolvedValue({responseId: 1})
    })

    afterEach(() => vi.useRealTimers())

    it('continues on the page the kept answers stopped on', async () => {
        saveFormDraft(DRAFT_KEY, {answers: {1: {type: 'TEXT', text: 'Kim'}}, path: ['p0', 'p1']})
        const form = open()

        await form.load()
        await vi.advanceTimersByTimeAsync(2000)

        expect(form.walk.current.value).toBe('p1')
        expect(form.answers.value[1]).toEqual({type: 'TEXT', text: 'Kim'})
        expect(form.resumedFrom.value).not.toBeNull()
        expect(readFormDraft(DRAFT_KEY)?.path).toEqual(['p0', 'p1'])
    })

    it('continues on that page too where the form came with the page', async () => {
        saveFormDraft(DRAFT_KEY, {answers: {1: {type: 'TEXT', text: 'Kim'}}, path: ['p0', 'p1']})

        const form = open(twoPageForm())
        await vi.advanceTimersByTimeAsync(2000)

        expect(form.walk.current.value).toBe('p1')
        expect(readFormDraft(DRAFT_KEY)?.path).toEqual(['p0', 'p1'])
    })

    it('keeps nothing for opening the form', async () => {
        const form = open()

        await form.load()
        await vi.advanceTimersByTimeAsync(2000)

        expect(readFormDraft(DRAFT_KEY)).toBeNull()
    })

    it('keeps an answer once the visitor pauses, and forgets it once taken back', async () => {
        const form = open()
        await form.load()

        form.updateText(twoPageForm().questions[0]!, 'Kim')
        await vi.advanceTimersByTimeAsync(999)
        expect(readFormDraft(DRAFT_KEY)).toBeNull()
        await vi.advanceTimersByTimeAsync(1)
        expect(readFormDraft(DRAFT_KEY)?.answers[1]).toEqual({type: 'TEXT', text: 'Kim'})

        form.updateText(twoPageForm().questions[0]!, '')
        await vi.advanceTimersByTimeAsync(2000)
        expect(readFormDraft(DRAFT_KEY)).toBeNull()
    })

    it('keeps nothing once the answers are sent', async () => {
        const form = open()
        await form.load()
        form.updateText(twoPageForm().questions[0]!, 'Kim')
        form.consentAccepted.value = true

        form.submit()
        await vi.advanceTimersByTimeAsync(2000)

        expect(submitPublicResponse).toHaveBeenCalledOnce()
        expect(form.submitted.value).toBe(true)
        expect(readFormDraft(DRAFT_KEY)).toBeNull()
    })
})
