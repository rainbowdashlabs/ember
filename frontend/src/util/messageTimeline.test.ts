/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {ConversationMessageResponse} from '@/api/generated/schema'
import {timelineOf, withMessage} from './messageTimeline'

const ANNA = {uid: 'anna', firstName: 'Anna', lastName: 'Muster'}
const BEN = {uid: 'ben', firstName: 'Ben', lastName: 'Muster'}

function message(id: number, at: string, author = ANNA, systemKind: ConversationMessageResponse['systemKind'] = null): ConversationMessageResponse {
    return {
        id,
        author,
        content: `Nachricht ${id}`,
        replyTo: null,
        systemKind,
        subject: null,
        createdAt: at,
        editedAt: null,
        deleted: false,
        reactions: [],
    }
}

describe('timelineOf', () => {
    it('runs messages of one author together within seven minutes', () => {
        const items = timelineOf([
            message(1, '2026-10-15T10:00:00'),
            message(2, '2026-10-15T10:05:00'),
            message(3, '2026-10-15T10:20:00'),
            message(4, '2026-10-15T10:21:00', BEN),
        ], null, 'anna')

        const opens = items.flatMap(item => item.kind === 'message' ? [item.opensRun] : [])
        expect(opens).toEqual([true, false, true, true])
    })

    it('separates days and marks where the unread part starts', () => {
        const items = timelineOf([
            message(1, '2026-10-14T10:00:00', BEN),
            message(2, '2026-10-15T10:00:00', BEN),
            message(3, '2026-10-15T10:01:00', BEN),
        ], 2, 'anna')

        expect(items.map(item => item.kind)).toEqual(['day', 'message', 'day', 'message', 'unread', 'message'])
        const afterLine = items[5]
        expect(afterLine?.kind === 'message' && afterLine.opensRun).toBe(true)
    })

    it('never marks the reader\'s own message as the start of the unread part', () => {
        const items = timelineOf([message(5, '2026-10-15T10:00:00', ANNA)], 1, 'anna')

        expect(items.some(item => item.kind === 'unread')).toBe(false)
    })

    it('starts a new run after a system line', () => {
        const items = timelineOf([
            message(1, '2026-10-15T10:00:00'),
            message(2, '2026-10-15T10:01:00', ANNA, 'RENAMED'),
            message(3, '2026-10-15T10:02:00'),
        ], null, 'anna')

        const last = items.at(-1)
        expect(last?.kind === 'message' && last.opensRun).toBe(true)
    })
})

describe('withMessage', () => {
    it('replaces a message in place and puts a new one where its id belongs', () => {
        const list = [message(1, '2026-10-15T10:00:00'), message(3, '2026-10-15T10:02:00')]
        const changed = {...message(1, '2026-10-15T10:00:00'), content: 'Geändert'}

        expect(withMessage(list, changed).map(entry => entry.content)).toEqual(['Geändert', 'Nachricht 3'])
        expect(withMessage(list, message(2, '2026-10-15T10:01:00')).map(entry => entry.id)).toEqual([1, 2, 3])
        expect(withMessage(list, message(4, '2026-10-15T10:03:00')).map(entry => entry.id)).toEqual([1, 3, 4])
    })
})
