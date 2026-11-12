/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import StationLogo from './StationLogo.vue'

const UID = '00000000-0000-0000-0000-000000000007'

/** The logo of a station on the list, or its initials where it has none. */
describe('StationLogo', () => {
    function logo() {
        return mount(StationLogo, {props: {uid: UID, name: 'Freiwillige Feuerwehr Nord'}})
    }

    it('shows the initials until the logo has arrived, then only the logo', async () => {
        const view = logo()

        expect(view.find('img').attributes('src')).toBe(`/api/v1/public/stations/${UID}/logo?size=128`)
        expect(view.find('img').attributes('alt')).toBe('')
        expect(view.find('img').attributes('style')).toContain('display: none')
        expect(view.find('[data-testid="station-initials"]').text()).toBe('FF')

        await view.find('img').trigger('load')

        expect(view.find('img').attributes('style') ?? '').not.toContain('display: none')
        expect(view.find('[data-testid="station-initials"]').exists()).toBe(false)
    })

    it('keeps the initials once the logo cannot be loaded', async () => {
        const view = logo()

        await view.find('img').trigger('error')

        expect(view.find('img').exists()).toBe(false)
        expect(view.find('[data-testid="station-initials"]').text()).toBe('FF')
    })
})
