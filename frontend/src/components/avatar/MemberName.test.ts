/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import MemberName from './MemberName.vue'
import {createIdentity} from '@/test/mocks/factories'

/**
 * Wherever a person is named, this component names them. The avatar is stubbed because it fetches
 * an authenticated image, which is its own concern and not what any of this is about.
 */
function mountName(identity: ReturnType<typeof createIdentity> | null) {
    return mount(MemberName, {
        props: {identity},
        global: {stubs: {UserAvatar: true, StationBadge: true}},
    })
}

describe('MemberName', () => {
    it('renders the name of the identity', () => {
        expect(mountName(createIdentity({name: 'Max Mustermann'})).text()).toContain('Max Mustermann')
    })

    it('renders nothing without an identity', () => {
        expect(mountName(null).text()).toBe('')
    })

    it('applies the name colour', () => {
        const wrapper = mountName(createIdentity({name: 'Anna', nameColor: '#FF6421'}))
        const styled = wrapper.findAll('span').find(span => span.attributes('style')?.includes('color'))
        expect(styled?.attributes('style')).toContain('#FF6421')
    })

    it('shows the display tag next to the name', () => {
        const identity = createIdentity({name: 'Anna', displayTag: {name: 'Betreuer', color: '#00C507'}})
        expect(mountName(identity).text()).toContain('Betreuer')
    })

    it('falls back to the station name for a partner member without a resolved name', () => {
        const identity = createIdentity({name: null, stationName: 'Partnerwache', stationUid: 'other-station'})
        expect(mountName(identity).text()).toContain('Partnerwache')
    })
})
