/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import InvitePersonSearch from './InvitePersonSearch.vue'
import {FindableBecause, type CandidateResponse} from '@/api/generated/schema'
import * as members from '@/api/members'

vi.mock('@/api/members', () => ({searchInviteCandidates: vi.fn()}))

function candidate(firstName: string, flags: Partial<CandidateResponse> = {}): CandidateResponse {
    return {
        accountUid: `00000000-0000-0000-0000-00000000000${firstName.length}`,
        firstName,
        lastName: 'Schmidt',
        because: FindableBecause.SHARED_STATION,
        through: 'Nord',
        alreadyHere: false,
        alreadyAsked: false,
        declinedLately: false,
        ...flags,
    }
}

async function typed(wrapper: ReturnType<typeof mount>, text: string) {
    await wrapper.get('input').setValue(text)
    await wrapper.get('input').trigger('input')
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()
}

describe('InvitePersonSearch', () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => {
        vi.useRealTimers()
        vi.mocked(members.searchInviteCandidates).mockReset()
    })

    it('waits for a word of three letters before it asks', async () => {
        const wrapper = mount(InvitePersonSearch, {props: {found: null}})

        await typed(wrapper, 'a e n')

        expect(members.searchInviteCandidates).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain('mindestens drei Buchstaben')
    })

    it('shows what ties the reader to each person and hands the picked one on', async () => {
        vi.mocked(members.searchInviteCandidates).mockResolvedValue([candidate('Anna')])
        const wrapper = mount(InvitePersonSearch, {props: {found: null}})

        await typed(wrapper, 'anna')
        expect(wrapper.text()).toContain('Wache Nord')
        await wrapper.get('[data-testid="invite-candidate"] button').trigger('click')

        expect(wrapper.emitted('update:found')?.at(-1)).toEqual([candidate('Anna')])
    })

    it('lists somebody here, asked or having declined lately but does not let them be picked', async () => {
        vi.mocked(members.searchInviteCandidates).mockResolvedValue([
            candidate('Hier', {alreadyHere: true}),
            candidate('Gefragt', {alreadyAsked: true}),
            candidate('Nein', {declinedLately: true}),
        ])
        const wrapper = mount(InvitePersonSearch, {props: {found: null}})

        await typed(wrapper, 'schmidt')

        const rows = wrapper.findAll('[data-testid="invite-candidate"]')
        expect(rows).toHaveLength(3)
        for (const row of rows) expect(row.get('button').attributes('disabled')).toBeDefined()
        expect(wrapper.text()).toContain('Schon an dieser Wache')
        expect(wrapper.text()).toContain('Schon gefragt')
        expect(wrapper.text()).toContain('Hat vor Kurzem abgelehnt')
    })

    it('says a failed search failed rather than that nobody was found', async () => {
        vi.mocked(members.searchInviteCandidates).mockRejectedValue(new Error('down'))
        const wrapper = mount(InvitePersonSearch, {props: {found: null}})

        await typed(wrapper, 'anna')

        expect(wrapper.text()).not.toContain('Niemand gefunden')
        expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    })

    it('names the person on each pick button and starts empty once a choice is cleared', async () => {
        vi.mocked(members.searchInviteCandidates).mockResolvedValue([candidate('Anna')])
        const wrapper = mount(InvitePersonSearch, {props: {found: null}})

        await typed(wrapper, 'anna')
        expect(wrapper.get('[data-testid="invite-candidate"] button').attributes('aria-label'))
            .toBe('Anna Schmidt auswählen')
        await wrapper.setProps({found: candidate('Anna')})
        await wrapper.setProps({found: null})

        expect(wrapper.findAll('[data-testid="invite-candidate"]')).toHaveLength(0)
        expect((wrapper.get('input').element as HTMLInputElement).value).toBe('')
    })
})
