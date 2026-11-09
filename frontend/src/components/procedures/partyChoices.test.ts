/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {stationMembers} from '@/api'
import {loadPartyChoices, noPartyChoices} from './partyChoices'

vi.mock('@/api', () => ({
    stationMembers: {listCompletions: vi.fn()},
}))

vi.mock('@/components/documents/generation', () => ({
    audienceLists: async () => ({groups: [{id: 1, name: 'Atemschutz'}], tags: []}),
}))

/** Whom a party can name is read in one go, members included. */
describe('partyChoices', () => {
    it('starts empty', () => {
        expect(noPartyChoices()).toEqual({members: [], groups: [], tags: []})
    })

    it('reads the members beside the groups and tags', async () => {
        vi.mocked(stationMembers.listCompletions).mockResolvedValue([{id: 5, name: 'Leon'}] as never)

        expect(await loadPartyChoices()).toEqual({
            members: [{id: 5, name: 'Leon'}],
            groups: [{id: 1, name: 'Atemschutz'}],
            tags: [],
        })
    })

    it('passes on a failure to read the members', async () => {
        vi.mocked(stationMembers.listCompletions).mockRejectedValue(new Error('offline'))

        await expect(loadPartyChoices()).rejects.toThrow('offline')
    })
})
