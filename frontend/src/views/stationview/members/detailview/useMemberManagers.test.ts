/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent, ref} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {StationMember} from '@/api/types'
import {useMemberManagers} from './useMemberManagers'

const invite = vi.fn()
const listMembers = vi.fn()
const setUserType = vi.fn()
const setManagers = vi.fn()
const getManagers = vi.fn()
const getMember = vi.fn()

vi.mock('@/api', () => ({
    members: {
        invite: (...args: unknown[]) => invite(...args),
    },
    stationMembers: {
        listMembers: (...args: unknown[]) => listMembers(...args),
        setUserType: (...args: unknown[]) => setUserType(...args),
        setManagers: (...args: unknown[]) => setManagers(...args),
        getManagers: (...args: unknown[]) => getManagers(...args),
        getMember: (...args: unknown[]) => getMember(...args),
    },
    profileFields: {
        getValues: async () => [],
    },
}))

function member(id: number, accountId: number, userType: string): StationMember {
    return {id, stationId: 'station-1', accountId, name: `Person ${id}`, userType} as StationMember
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function managersFor(all: StationMember[]) {
    let api: ReturnType<typeof useMemberManagers> | null = null
    mount(defineComponent({
        setup() {
            api = useMemberManagers(ref(1), ref(all), () => [], ref(''))
            return () => null
        },
    }))
    return api as unknown as ReturnType<typeof useMemberManagers>
}

beforeEach(() => {
    vi.clearAllMocks()
    invite.mockResolvedValue({id: 99})
    listMembers.mockResolvedValue([member(7, 99, 'MEMBER')])
    setUserType.mockResolvedValue(undefined)
    setManagers.mockResolvedValue(undefined)
    getManagers.mockResolvedValue([])
    getMember.mockResolvedValue({userType: 'GUARDIAN'})
})

describe('useMemberManagers', () => {
    it('makes somebody entered as a guardian a guardian', async () => {
        await managersFor([]).createManager({firstName: 'Petra', lastName: 'Sorge', email: 'petra@example.test'})

        expect(setUserType).toHaveBeenCalledWith(7, 'GUARDIAN')
    })

    it('links the new guardian to the member they were entered beside', async () => {
        await managersFor([]).createManager({firstName: 'Petra', lastName: 'Sorge', email: 'petra@example.test'})

        expect(setManagers).toHaveBeenCalledWith(1, {managerIds: [7]})
    })

    it('carries the answer about the setup mail through to the invitation', async () => {
        await managersFor([]).createManager({
            firstName: 'Petra',
            lastName: 'Sorge',
            email: 'petra@example.test',
            sendSetupMail: false,
        })

        expect(invite).toHaveBeenCalledWith(expect.objectContaining({sendSetupMail: false}))
    })

    /**
     * The member kind is only written for somebody being created as a guardian. Attaching a person
     * who is already at the station says nothing about what they are, and a helper or a member of
     * the team put in charge of a child keeps their own kind.
     */
    it('leaves the kind of an existing member alone when one is attached', async () => {
        await managersFor([member(4, 40, 'TEAM')]).linkManager(4)

        expect(setUserType).not.toHaveBeenCalled()
    })

    it('changes nobody when the invited account cannot be found again', async () => {
        listMembers.mockResolvedValue([])

        await managersFor([]).createManager({firstName: 'Petra', lastName: 'Sorge', email: 'petra@example.test'})

        expect(setUserType).not.toHaveBeenCalled()
        expect(setManagers).not.toHaveBeenCalled()
    })
})
