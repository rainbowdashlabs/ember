/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {nextTick, ref} from 'vue'
import {describe, expect, it, vi} from 'vitest'
import type {PathPage} from '@/util/formPath'
import type {AnswerValue} from '@/util/formAnswers'
import {useFormWalk, type WalkQuestion} from './useFormWalk'

vi.mock('vue-i18n', async (original) => ({
    ...(await original<typeof import('vue-i18n')>()),
    useI18n: () => ({t: (key: string) => key}),
}))

const next = {kind: 'NEXT' as const, page: null}

function page(key: string, after: PathPage['after'] = next): PathPage {
    return {key, after}
}

interface Question extends WalkQuestion {
    type: string
}

/** A form asking whether somebody comes: yes leads to what they bring, no skips to the end. */
function comingForm() {
    const pages = ref<PathPage[]>([page('ask'), page('bring', {kind: 'SUBMIT', page: null}), page('why')])
    const questions = ref<Question[]>([
        {id: 1, pageKey: 'ask', required: true, type: 'CHOICE', branch: {yes: {kind: 'PAGE', page: 'bring'}, no: {kind: 'PAGE', page: 'why'}}},
        {id: 2, pageKey: 'bring', required: false, type: 'TEXT'},
        {id: 3, pageKey: 'why', required: false, type: 'TEXT'},
    ])
    const answers = ref<Record<number, AnswerValue>>({1: {selected: [], other: ''}, 2: {text: ''}, 3: {text: ''}})
    const walk = useFormWalk(pages, questions, answers, question => question.type)
    return {pages, questions, answers, walk}
}

/**
 * Going through a form page by page: the answer decides the next page, back goes where the reader came
 * from, and a page is left only once its required questions are answered.
 */
describe('useFormWalk', () => {
    it('starts on the first page and follows the answer given', () => {
        const {answers, walk} = comingForm()

        expect(walk.current.value).toBe('ask')
        expect(walk.next()).toBe(false)
        expect(walk.errors.value[1]).toBe('forms.fill.required')

        answers.value[1] = {selected: ['no'], other: ''}
        expect(walk.next()).toBe(true)

        expect(walk.current.value).toBe('why')
        expect(walk.path.value).toEqual(['ask', 'why'])
        expect(walk.isLast.value).toBe(true)
    })

    it('goes back to the page the reader came from', () => {
        const {answers, walk} = comingForm()
        answers.value[1] = {selected: ['no'], other: ''}
        walk.next()

        walk.back()

        expect(walk.current.value).toBe('ask')
        expect(walk.pageNumber.value).toBe(1)
    })

    it('keeps a page it was put on right after the pages arrived', async () => {
        const pages = ref<PathPage[]>([])
        const walk = useFormWalk(pages, ref<Question[]>([]), ref({}), question => question.type)

        pages.value = [page('p0'), page('p1')]
        walk.showAt(['p0', 'p1'], {})
        await nextTick()

        expect(walk.current.value).toBe('p1')
        expect(walk.path.value).toEqual(['p0', 'p1'])
    })

    it('stays where it is when the same pages arrive again, and starts over when they change', async () => {
        const {pages, answers, walk} = comingForm()
        answers.value[1] = {selected: ['yes'], other: ''}
        walk.next()

        pages.value = pages.value.map(existing => ({...existing}))
        await nextTick()
        expect(walk.current.value).toBe('bring')

        pages.value = [page('other')]
        await nextTick()
        expect(walk.current.value).toBe('other')
        expect(walk.path.value).toEqual(['other'])
    })

    it('measures progress against the longest path still possible', () => {
        const {answers, walk} = comingForm()

        expect(walk.progress.value).toBeCloseTo(1 / 2)
        answers.value[1] = {selected: ['yes'], other: ''}
        walk.next()

        expect(walk.progress.value).toBe(1)
    })
})
