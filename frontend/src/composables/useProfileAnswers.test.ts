/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import {
    useProfileAnswers,
    type ProfileAnswerEntry,
    type ProfileAnswersPort,
    type ProfileAnswersSnapshot,
} from './useProfileAnswers'

const stationQuestion: LaidOutField = {id: 7, name: 'Spind', fieldType: 'TEXT', config: {}, origin: 'STATION'}
const associationQuestion: LaidOutField = {id: 7, name: 'Lehrgang', fieldType: 'TEXT', config: {}, origin: 'CLUSTER'}
const lockedQuestion: LaidOutField = {id: 8, name: 'Dienstgrad', fieldType: 'TEXT', config: {}, readonly: true}

/** A member asked a station question and an association question that share a number. */
const twoOwners: ProfileAnswersSnapshot = {
    fields: [stationQuestion, associationQuestion, lockedQuestion],
    values: [
        {fieldId: 7, value: '"12"', origin: 'STATION'},
        {fieldId: 7, value: '"TM1"', origin: 'CLUSTER'},
        {fieldId: 8, value: '"Brandmeister"'},
    ],
}

/** A port that serves the snapshot and remembers what it was asked to save. */
function fakePort(): ProfileAnswersPort & {saved: ProfileAnswerEntry[][]} {
    const saved: ProfileAnswerEntry[][] = []
    return {
        saved,
        load: () => Promise.resolve(twoOwners),
        save: (_memberId, answers) => {
            saved.push(answers)
            return Promise.resolve()
        },
        canEdit: field => !field.readonly,
    }
}

describe('useProfileAnswers', () => {
    it('holds a station answer and an association answer of the same number apart', async () => {
        const answers = useProfileAnswers(fakePort())
        await answers.load(1)

        expect(answers.valueOf(stationQuestion)).toBe('12')
        expect(answers.valueOf(associationQuestion)).toBe('TM1')

        answers.update(associationQuestion, 'TM2')
        expect(answers.valueOf(stationQuestion)).toBe('12')
        expect(answers.valueOf(associationQuestion)).toBe('TM2')
    })

    it('saves every answer the reader may write, with the table its question lives in', async () => {
        const port = fakePort()
        const answers = useProfileAnswers(port)
        await answers.load(1)
        answers.update(associationQuestion, 'TM2')
        expect(answers.dirty.value).toBe(true)

        await answers.save(1)

        expect(port.saved).toEqual([[
            {fieldId: 7, value: '"12"', origin: 'STATION'},
            {fieldId: 7, value: '"TM2"', origin: 'CLUSTER'},
        ]])
        expect(answers.dirty.value).toBe(false)
    })

    it('keeps what was typed when the questions are put again, and forgets it when the answers are read again', async () => {
        const answers = useProfileAnswers(fakePort())
        await answers.load(1)
        answers.update(stationQuestion, '13')

        answers.reask([stationQuestion])
        expect(answers.fields.value).toEqual([stationQuestion])
        expect(answers.valueOf(stationQuestion)).toBe('13')

        await answers.load(1)
        expect(answers.valueOf(stationQuestion)).toBe('12')
        expect(answers.dirty.value).toBe(false)
    })
})
