/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {useFormLayout} from './useFormLayout'
import type {FormLayout} from '@/api/generated/schema'

const next = {kind: 'NEXT' as const}

function stored(): FormLayout {
    const page = (key: string, position: number, after: FormLayout['pages'][number]['after'] = next) =>
        ({id: position + 1, formId: 1, key, position, title: '', description: '', after})
    const question = (id: number, pageKey: string) => ({
        id, formId: 1, position: id, pageKey, formQuestionType: 'TEXT' as const, title: `Frage ${id}`,
        description: '', required: false, shuffle: false, config: {questionType: 'TEXT' as const, longAnswer: false},
        branch: null,
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

        expect(request.pages?.map(page => page.key)).toEqual(['a', 'b', 'c'])
        expect(request.questions?.map(question => [question.id, question.pageKey])).toEqual([[1, 'a'], [2, 'b'], [3, 'c']])
    })

    it('keeps the questions of a removed page on the page above', () => {
        const layout = useFormLayout()
        layout.load(stored())

        layout.removePage(1)

        expect(layout.pages.value.map(page => page.key)).toEqual(['a', 'c'])
        expect(layout.pages.value[0]!.questions.map(question => question.title)).toEqual(['Frage 1', 'Frage 2'])
    })

    it('leaves no second deciding question on the page a removed page joins', () => {
        const layout = useFormLayout()
        layout.load(stored())
        layout.addQuestion(0, 'CHOICE')
        layout.addQuestion(1, 'CHOICE')
        layout.setDeciding(0, layout.pages.value[0]!.questions[1]!.id)
        layout.setDeciding(1, layout.pages.value[1]!.questions[1]!.id)

        layout.removePage(1)

        const deciding = layout.pages.value[0]!.questions.filter(question => question.branch)
        expect(deciding).toHaveLength(1)
        expect(layout.toRequest().questions?.filter(question => question.branch)).toHaveLength(1)
    })

    it('gives a new question an id after every unsaved one the pages hold', () => {
        const layout = useFormLayout()
        layout.addQuestion(0, 'TEXT')
        layout.pages.value = [{...layout.pages.value[0]!, questions: [
            {...layout.pages.value[0]!.questions[0]!, id: 'temp-5'},
        ]}]

        layout.addQuestion(0, 'TEXT')
        layout.duplicateQuestion(0, 1)

        expect(layout.allQuestions.value.map(question => question.id)).toEqual(['temp-5', 'temp-6', 'temp-7'])
    })

    it('resets a page that would lead upwards once pages are moved', () => {
        const layout = useFormLayout()
        layout.load(stored())

        layout.movePage(0, 1)
        layout.movePage(1, 1)

        expect(layout.pages.value.map(page => page.key)).toEqual(['b', 'c', 'a'])
        expect(layout.pages.value[2]!.after).toEqual({kind: 'NEXT'})
    })

    it('marks a page no path leads to', () => {
        const layout = useFormLayout()
        layout.load(stored())

        expect(layout.reachable.value.has('b')).toBe(false)
        expect(layout.reachable.value.has('c')).toBe(true)
    })

    it('lets one question of a page decide where it leads and saves only the options it has', () => {
        const layout = useFormLayout()
        layout.load(stored())
        layout.addQuestion(0, 'CHOICE')
        const choice = layout.pages.value[0]!.questions[1]!
        const option = (choice.config.options as {key: string}[])[0]!.key

        layout.setDeciding(0, choice.id)
        choice.branch![option] = {kind: 'PAGE', page: 'b'}
        choice.branch!.gone = {kind: 'SUBMIT'}

        const saved = layout.toRequest().questions?.find(question => question.id === undefined)
        expect(saved?.branch).toEqual({[option]: {kind: 'PAGE', page: 'b'}})
        expect(layout.reachable.value.has('b')).toBe(true)

        layout.setDeciding(0, null)
        expect(choice.branch).toBeNull()
    })

    it('drops an answer\'s target that no longer leads further down', () => {
        const layout = useFormLayout()
        layout.load(stored())
        layout.addQuestion(1, 'CHOICE')
        const choice = layout.pages.value[1]!.questions[1]!
        layout.setDeciding(1, choice.id)
        choice.branch!.x = {kind: 'PAGE', page: 'c'}

        layout.movePage(2, -1)

        expect(choice.branch).toEqual({})
    })

    it('puts a copy of a question below it with keys of its own', () => {
        const layout = useFormLayout()
        layout.load(stored())
        layout.addQuestion(0, 'CHOICE')
        const original = layout.pages.value[0]!.questions[1]!
        layout.setDeciding(0, original.id)

        layout.duplicateQuestion(0, 1)

        const copy = layout.pages.value[0]!.questions[2]!
        const keysOf = (config: Record<string, unknown>) => (config.options as {key: string}[]).map(option => option.key)
        expect(copy.id).not.toBe(original.id)
        expect(copy.branch).toBeNull()
        expect(keysOf(copy.config)).not.toEqual(keysOf(original.config))
        expect(keysOf(copy.config)).toHaveLength(1)
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
