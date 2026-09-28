/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {useFormLayout} from './useFormLayout'
import type {FormLayout} from '@/api/forms'

const next = {kind: 'NEXT' as const}

function stored(): FormLayout {
    const page = (key: string, position: number, after: FormLayout['pages'][number]['after'] = next) =>
        ({id: position + 1, formId: 1, key, position, title: '', description: '', after})
    const question = (id: number, pageKey: string) => ({
        id, formId: 1, position: id, pageKey, formQuestionType: 'TEXT' as const, title: `Frage ${id}`,
        description: '', required: false, shuffle: false, config: {longAnswer: false},
    })
    return {
        pages: [page('a', 0, {kind: 'PAGE', page: 'c'}), page('b', 1), page('c', 2)],
        questions: [question(1, 'a'), question(2, 'b'), question(3, 'c')],
    }
}

/**
 * The editor holds each page with its questions, and every change to the shape of the form keeps
 * the questions and keeps every page leading further down.
 */
describe('useFormLayout', () => {
    it('sends every question naming its page', () => {
        const layout = useFormLayout()
        layout.load(stored())

        const request = layout.toRequest()

        expect(request.pages.map(page => page.key)).toEqual(['a', 'b', 'c'])
        expect(request.questions.map(question => [question.id, question.pageKey])).toEqual([[1, 'a'], [2, 'b'], [3, 'c']])
    })

    it('keeps the questions of a removed page on the page above', () => {
        const layout = useFormLayout()
        layout.load(stored())

        layout.removePage(1)

        expect(layout.pages.value.map(page => page.key)).toEqual(['a', 'c'])
        expect(layout.pages.value[0]!.questions.map(question => question.title)).toEqual(['Frage 1', 'Frage 2'])
    })

    it('resets a page that would lead upwards once pages are moved', () => {
        const layout = useFormLayout()
        layout.load(stored())

        layout.movePage(0, 1)
        layout.movePage(1, 1)

        expect(layout.pages.value.map(page => page.key)).toEqual(['b', 'c', 'a'])
        expect(layout.pages.value[2]!.after).toEqual({kind: 'NEXT', page: null})
    })

    it('marks a page no path leads to', () => {
        const layout = useFormLayout()
        layout.load(stored())

        expect(layout.reachable.value.has('b')).toBe(false)
        expect(layout.reachable.value.has('c')).toBe(true)
    })

    it('moves a question to the end of another page and numbers it there', () => {
        const layout = useFormLayout()
        layout.load(stored())

        layout.moveToPage(0, 0, 2)

        const moved = layout.pages.value[2]!.questions[1]!
        expect(moved.title).toBe('Frage 1')
        expect(layout.numberOf(moved)).toBe(3)
    })
})
