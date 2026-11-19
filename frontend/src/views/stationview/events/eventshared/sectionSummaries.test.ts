/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {
    audienceSummary, documentsSummary, fieldsSummary, questionsSummary, remindersSummary, sharingSummary, turnoutSummary,
} from './sectionSummaries'

const t = (key: string, values?: Record<string, unknown>) => values ? `${key} ${JSON.stringify(values)}` : key

const nobody = {mode: 'AND' as const, userTypes: [], groupIds: [], tagIds: [], memberIds: []}

/** A closed section says what it holds, and that nothing is set where nothing is. */
describe('sectionSummaries', () => {
    it('says nothing is set for an empty section', () => {
        expect(remindersSummary([], t)).toBe('formSections.nothingSet')
        expect(questionsSummary(0, t)).toBe('formSections.nothingSet')
        expect(fieldsSummary('', 0, t)).toBe('formSections.nothingSet')
        expect(turnoutSummary(null, null, null, t)).toBe('formSections.nothingSet')
        expect(documentsSummary(0, 0, t)).toBe('formSections.nothingSet')
    })

    it('names the reminders in order and counts the questions', () => {
        expect(remindersSummary([7, 1], t)).toBe('formSections.remindersSet {"days":"1, 7"}')
        expect(questionsSummary(3, t)).toBe('formSections.questionsSet {"count":3}')
    })

    it('joins the attendance list and the fields, and the documents and the files', () => {
        expect(fieldsSummary('Dienstabend', 2, t))
            .toBe('formSections.sheetSet {"name":"Dienstabend"} · formSections.fieldsSet {"count":2}')
        expect(documentsSummary(1, 0, t)).toBe('formSections.documentsSet {"count":1}')
        expect(documentsSummary(0, 2, t)).toBe('formSections.filesSet {"count":2}')
    })

    it('names the minimum with the days before, and the closing of a repeating appointment', () => {
        expect(turnoutSummary(4, 2, null, t)).toBe('formSections.minimumBefore {"minimum":4,"days":2}')
        expect(turnoutSummary(4, null, 3, t))
            .toBe('formSections.minimumSet {"minimum":4} · formSections.closeDaysSet {"days":3}')
    })

    it('says who may sign up and who may see the appointment', () => {
        expect(audienceSummary(nobody, nobody, t)).toBe('formSections.signUpAll · formSections.viewAll')
        expect(audienceSummary({...nobody, groupIds: [2]}, nobody, t)).toBe('formSections.signUpRestricted · formSections.viewAll')
    })

    it('says with whom the appointment is shared', () => {
        expect(sharingSummary(false, 'ALL_PARTNERS', [], t)).toBe('formSections.notShared')
        expect(sharingSummary(true, 'ALL_PARTNERS', [], t)).toBe('formSections.sharedWithAll')
        expect(sharingSummary(true, 'SPECIFIC', [1, 2], t)).toBe('formSections.sharedWith {"count":2}')
    })

    it('names a single day as one', () => {
        expect(remindersSummary([1], t)).toBe('formSections.remindersSetOne')
        expect(turnoutSummary(4, 1, 1, t))
            .toBe('formSections.minimumBeforeOne {"minimum":4} · formSections.closeDaysSetOne')
    })

    it('says a part follows the attendance list of its event group, and a share with nobody is none', () => {
        expect(fieldsSummary('', 0, t, true)).toBe('formSections.sheetFromGroup')
        expect(sharingSummary(true, 'SPECIFIC', [], t)).toBe('formSections.notShared')
    })
})
