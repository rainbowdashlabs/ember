/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import BoardFederationTargetList from './BoardFederationTargetList.vue'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import type {FederationTarget} from '@/api/boards'

/**
 * Who on a partner station may see a shared board is a user type there, which is what the backend
 * stores and what it refuses anything else in place of.
 */
describe('BoardFederationTargetList', () => {
    async function mountWith(target: FederationTarget) {
        return mountSuspended(BoardFederationTargetList, {
            props: {targets: [target], partnerName: () => 'Partnerwache'},
        })
    }

    it('offers every user type by its name', async () => {
        const list = await mountWith({partnerId: 1, shareMode: 'READ_ONLY', requiredUserType: StationUserType.MEMBER})

        const options = list.get('[data-testid="federation-target-user-type"]').findAll('option')
        expect(options.map(option => option.attributes('value'))).toEqual(Object.values(StationUserType))
        expect(options.map(option => option.text())).toEqual(Object.values(StationUserTypeLabels))
    })

    it('keeps the chosen user type on the target and nothing in its place', async () => {
        const target: FederationTarget = {partnerId: 1, shareMode: 'FULL', requiredUserType: StationUserType.MEMBER}
        const list = await mountWith(target)

        await list.get('[data-testid="federation-target-user-type"]').setValue(StationUserType.TEAM)

        expect(target).toEqual({partnerId: 1, shareMode: 'FULL', requiredUserType: StationUserType.TEAM})
    })
})
