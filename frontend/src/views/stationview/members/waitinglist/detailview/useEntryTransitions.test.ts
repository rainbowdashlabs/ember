/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {ref} from 'vue'
import {describe, expect, it, vi} from 'vitest'
import type {InvitationRecipients, WaitingListEntryWithScore} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'
import {useEntryTransitions} from './useEntryTransitions'

const invitationRecipients = vi.fn()

vi.mock('@/api', () => ({
    waitingList: {
        invitationRecipients: (...args: unknown[]) => invitationRecipients(...args),
    },
}))
vi.mock('vue-i18n', () => ({useI18n: () => ({t: (key: string) => key})}))
vi.mock('vue-router', () => ({useRouter: () => ({push: () => undefined})}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({hasPermission: () => false})}))
vi.mock('@/composables/useSidebarCounts', () => ({useSidebarCounts: () => ({refresh: () => undefined})}))

const entry = {entry: {id: 5}, score: 0} as WaitingListEntryWithScore

describe('useEntryTransitions', () => {
    it('shows where the mail goes once the addresses arrive, and then lets the start of the trial be confirmed', async () => {
        const found: InvitationRecipients = {sendsMail: true, addresses: ['eltern@example.org']}
        invitationRecipients.mockResolvedValue(found)
        const transitions = useEntryTransitions(ref(3), ref([entry]), ref<Failure | null>(null))

        await transitions.moveToTesting(5)

        expect(invitationRecipients).toHaveBeenCalledWith(3, 5)
        expect(transitions.recipients.value).toEqual(found)
        expect(transitions.canConfirm.value).toBe(true)
    })
})
