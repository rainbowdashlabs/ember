/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {DateKind, PlaceholderCategory, TemplateQuestionKind} from '@/api/generated/schema'
import {NO_LIMITS} from '@/components/documents/questionLimits'
import {answerPlaceholders, keyFromLabel, limitsFor, newQuestion} from './templateQuestions'

/** The questions of a template: their names, and their answers as placeholders the picker offers. */
describe('template questions', () => {
    it('name a question after its label, umlauts written out, counted on where the name is taken', () => {
        expect(keyFromLabel('Datum der Änderung', [])).toBe('datum_der_aenderung')
        expect(keyFromLabel('Straße', ['strasse'])).toBe('strasse_2')
        expect(keyFromLabel('  ', [])).toBe('abfrage')
        expect(keyFromLabel('', ['abfrage', 'abfrage_2'])).toBe('abfrage_3')
    })

    it('start a new question empty under a free name', () => {
        const first = newQuestion(TemplateQuestionKind.CHECK, [])

        expect(first).toEqual({key: 'abfrage', label: '', kind: TemplateQuestionKind.CHECK, suggestion: '', required: false, limits: NO_LIMITS})
        expect(newQuestion(TemplateQuestionKind.TEXT, [first]).key).toBe('abfrage_2')
    })

    it('start a choice with one entry to write and every other kind without limits', () => {
        expect(limitsFor(TemplateQuestionKind.CHOICE).choices).toEqual([''])
        expect(limitsFor(TemplateQuestionKind.DATE)).toEqual(NO_LIMITS)
    })

    it('offer each answer below the questions, a date with the date formats, and none still without a label', () => {
        const offered = answerPlaceholders([
            {key: 'am', label: 'Ausgetreten am', kind: TemplateQuestionKind.DATE, suggestion: '', required: false, limits: NO_LIMITS},
            {key: 'grund', label: 'Grund', kind: TemplateQuestionKind.TEXT, suggestion: '', required: false, limits: NO_LIMITS},
            {key: 'leer', label: '', kind: TemplateQuestionKind.TEXT, suggestion: '', required: false, limits: NO_LIMITS},
        ], 'Abfragen')

        expect(offered.map(placeholder => placeholder.key)).toEqual(['ask.am', 'ask.grund'])
        expect(offered[0]).toMatchObject({
            category: PlaceholderCategory.QUESTION,
            path: ['Abfragen', 'Ausgetreten am'],
            dateKind: DateKind.DATE,
        })
        expect(offered[1]?.dateKind).toBeNull()
    })
})
