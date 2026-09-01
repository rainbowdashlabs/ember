/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import OwnerSection from './OwnerSection.vue'
import {StationPermission} from '@/api/types'

const listMembers = vi.fn()
const listAllPermissions = vi.fn()
const getAllMemberRoles = vi.fn()

vi.mock('@/api', () => ({
    stationManage: {transferOwnership: vi.fn(), requestStationDeletion: vi.fn()},
    stationMembers: {
        listMembers: (...args: unknown[]) => listMembers(...args),
        listAllPermissions: (...args: unknown[]) => listAllPermissions(...args),
        getAllMemberRoles: (...args: unknown[]) => getAllMemberRoles(...args),
    },
}))

/**
 * Who a station can be handed to.
 *
 * <p>The list is built from the station administrator permission, and it used to be built from a
 * permission called MANAGER, which is a user type and not a permission at all. Nothing matched, so a
 * station with two administrators offered neither and said there was nobody to hand it to, which
 * left the owner with no way out of their own station.
 */
describe('OwnerSection', () => {
    const ADMIN_ROLE = {id: 7, permission: StationPermission.STATION_ADMINISTRATOR}

    beforeEach(() => {
        vi.clearAllMocks()
        listAllPermissions.mockResolvedValue([{id: 3, permission: StationPermission.LOGIN}, ADMIN_ROLE])
    })

    it('offers every administrator except the owner', async () => {
        listMembers.mockResolvedValue([
            {id: 1, name: 'Owner Person', email: 'owner@example.com'},
            {id: 2, name: 'Second Manager', email: 'second@example.com'},
            {id: 3, name: 'Ordinary Member', email: 'member@example.com'},
        ])
        getAllMemberRoles.mockResolvedValue({1: [ADMIN_ROLE], 2: [ADMIN_ROLE], 3: []})

        const section = await mountSuspended(OwnerSection, {props: {stationId: 'uid', ownerMemberId: 1}})
        const options = section.findAll('option').map(option => option.text())

        expect(options).toContain('Second Manager')
        expect(options).not.toContain('Owner Person')
        expect(options).not.toContain('Ordinary Member')
    })

    it('asks for every role in one call rather than one per member', async () => {
        listMembers.mockResolvedValue([
            {id: 1, name: 'Owner Person', email: 'owner@example.com'},
            {id: 2, name: 'Second Manager', email: 'second@example.com'},
        ])
        getAllMemberRoles.mockResolvedValue({1: [ADMIN_ROLE], 2: [ADMIN_ROLE]})

        await mountSuspended(OwnerSection, {props: {stationId: 'uid', ownerMemberId: 1}})

        expect(getAllMemberRoles).toHaveBeenCalledTimes(1)
    })

    it('says the list could not be read instead of saying there is nobody', async () => {
        listMembers.mockRejectedValue(new Error('offline'))
        getAllMemberRoles.mockResolvedValue({})

        const section = await mountSuspended(OwnerSection, {props: {stationId: 'uid', ownerMemberId: 1}})

        expect(section.text()).toContain('konnten nicht geladen werden')
    })
})
