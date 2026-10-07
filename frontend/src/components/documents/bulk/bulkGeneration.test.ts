/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {JobMemberStatus, type GenerationJobSummary, type JobMemberResult} from '@/api/generated/schema'
import {emptyRestriction} from '@/components/input/restriction'
import {doneOf, failuresOf, failureText, gapText, isRunning, selectionFor} from './bulkGeneration'

const t = (key: string, named?: Record<string, unknown>) => (named ? `${key} ${JSON.stringify(named)}` : key)

const running: GenerationJobSummary = {
    id: 1,
    templateId: 8,
    templateName: 'Bescheinigung',
    startedByName: 'Nora Fülling',
    acceptMissing: false,
    startedAt: '2026-10-02T10:00:00Z',
    finishedAt: null,
    total: 5,
    filed: 2,
    failed: 1,
}

function result(memberId: number, status: JobMemberStatus, refusalCode: string | null = null): JobMemberResult {
    return {memberId, name: `Mitglied ${memberId}`, status, documentId: null, refusalCode, detail: null}
}

describe('the members a run is for', () => {
    it('takes the members chosen in the list over the audience', () => {
        const audience = emptyRestriction()
        expect(selectionFor([3, 4], audience)).toEqual({memberIds: [3, 4], audience: null})
        expect(selectionFor(null, audience)).toEqual({memberIds: null, audience})
    })
})

describe('the progress of a run', () => {
    it('counts the filed and the failed members as done', () => {
        expect(doneOf(running)).toBe(3)
        expect(isRunning(running)).toBe(true)
        expect(isRunning({...running, finishedAt: '2026-10-02T10:05:00Z'})).toBe(false)
    })

    it('lists the members that failed with the reason', () => {
        const members = [
            result(1, JobMemberStatus.FILED),
            result(2, JobMemberStatus.FAILED, 'D-091'),
            result(3, JobMemberStatus.WAITING),
        ]
        expect(failuresOf(members).map(member => member.memberId)).toEqual([2])
        expect(failureText(members[1]!, t)).toBe('D-091')
        expect(failureText(members[0]!, t)).toBe('')
    })
})

describe('what a member lacks before a run', () => {
    it('names the missing data, or the refusal that keeps the document from being drawn', () => {
        const missing = [{key: 'profile.4', label: 'Schule'}, {key: 'member.birthDate', label: 'Geburtsdatum'}]
        expect(gapText({memberId: 2, name: 'Ben', missing, refusalCode: null}, t))
            .toBe('documentTemplates.bulk.gapMissing {"values":"Schule, Geburtsdatum"}')
        expect(gapText({memberId: 3, name: 'Carla', missing: [], refusalCode: 'D-072'}, t)).toBe('D-072')
    })
})
