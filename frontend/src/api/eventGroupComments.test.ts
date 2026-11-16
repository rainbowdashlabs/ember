/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {StationPermission} from './generated/schema'
import {eventGroupCommentSource} from './eventGroups'
import {partnerEventGroupCommentSource} from './partnerEventGroups'

const client = vi.hoisted(() => ({
    get: vi.fn(async () => ({data: []})),
    post: vi.fn(async () => ({data: {}})),
    put: vi.fn(async () => ({data: {}})),
    delete: vi.fn(async () => ({data: {}})),
}))
const listCompletions = vi.hoisted(() => vi.fn(async () => []))
const listGroups = vi.hoisted(() => vi.fn(async () => []))

vi.mock('./client', () => ({default: client}))
vi.mock('./stationMembers', () => ({listCompletions}))
vi.mock('./memberGroups', () => ({listGroups}))

/**
 * The thread of an event group is read and written under that event group, here and at a partner;
 * here an appointment manager moderates it, at a partner only authors do.
 */
describe('event group comment sources', () => {
    beforeEach(() => vi.clearAllMocks())

    it('reads and writes the station\'s own thread under the event group', async () => {
        const source = eventGroupCommentSource(70)

        await source.list()
        await source.create(null, 'Hallo')
        await source.update(5, 'neu')
        await source.remove(5)
        await source.mentionables()

        expect(client.get).toHaveBeenCalledWith('/events/groups/70/comments')
        expect(client.post).toHaveBeenCalledWith('/events/groups/70/comments', {parentId: null, content: 'Hallo'})
        expect(client.put).toHaveBeenCalledWith('/events/groups/70/comments/5', {content: 'neu'})
        expect(client.delete).toHaveBeenCalledWith('/events/groups/70/comments/5')
        expect(listCompletions).toHaveBeenCalledWith({type: 'EVENT_GROUP_VIEW', entityId: 70})
        expect(source.moderator).toBe(StationPermission.EVENT_MANAGER)
    })

    it('reads and writes a partner\'s thread at the partner, where nobody here moderates', async () => {
        const source = partnerEventGroupCommentSource('abc', 70)

        await source.list()
        await source.create(3, 'Antwort')
        await source.update(5, 'neu')
        await source.remove(5)

        expect(client.get).toHaveBeenCalledWith('/federated/abc/events/groups/70/comments')
        expect(client.post).toHaveBeenCalledWith('/federated/abc/events/groups/70/comments', {parentId: 3, content: 'Antwort'})
        expect(client.put).toHaveBeenCalledWith('/federated/abc/events/groups/70/comments/5', {content: 'neu'})
        expect(client.delete).toHaveBeenCalledWith('/federated/abc/events/groups/70/comments/5')
        expect(source.moderator).toBeNull()
    })
})
