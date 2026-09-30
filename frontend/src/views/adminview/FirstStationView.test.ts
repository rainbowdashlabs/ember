/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import FirstStationView from './FirstStationView.vue'

const foundFirstStation = vi.fn()
const push = vi.fn()
const setActiveStation = vi.fn()
const forgetStations = vi.fn()
const forgetSession = vi.fn()

vi.mock('@/api/stations', () => ({
    foundFirstStation: (...args: unknown[]) => foundFirstStation(...args),
}))

vi.mock('@/composables/useStations', () => ({
    useStations: () => ({setActiveStation, clear: forgetStations}),
}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({clear: forgetSession}),
}))

vi.mock('vue-router', async (original) => ({
    ...await original<typeof import('vue-router')>(),
    useRouter: () => ({push}),
}))

/**
 * The page where the administrator of a fresh instance founds its first station.
 *
 * @vitest-environment happy-dom
 */
describe('FirstStationView', () => {
    function page() {
        return mount(FirstStationView, {global: {stubs: {ViewContent: {template: '<div><slot/></div>'}}}})
    }

    beforeEach(() => {
        foundFirstStation.mockReset()
        push.mockReset()
        setActiveStation.mockReset()
        foundFirstStation.mockResolvedValue({stationUid: 'uid-1', name: 'Jugendfeuerwehr Musterstadt'})
    })

    it('explains that the first station is about to be founded', () => {
        const view = page()

        expect(view.text()).toContain('noch keine Wache')
        expect(view.text()).toContain('Verwalter')
    })

    it('founds nothing without a name', async () => {
        const view = page()

        expect(view.find('[data-testid="first-station-found"]').attributes('disabled')).toBeDefined()
        await view.find('form').trigger('submit')
        expect(foundFirstStation).not.toHaveBeenCalled()
    })

    it('founds the station, makes it current and leads into its setup', async () => {
        const view = page()

        await view.find('input').setValue('  Jugendfeuerwehr Musterstadt ')
        await view.find('form').trigger('submit')
        await flushPromises()

        expect(foundFirstStation).toHaveBeenCalledWith('Jugendfeuerwehr Musterstadt')
        expect(forgetSession).toHaveBeenCalled()
        expect(setActiveStation).toHaveBeenCalledWith('uid-1')
        expect(push).toHaveBeenCalledWith({name: 'station-setup'})
    })

    it('shows a refusal and stays on the page', async () => {
        foundFirstStation.mockRejectedValue({
            response: {status: 409, data: {code: 'S-046', message: 'This instance already has a station'}},
        })
        const view = page()
        const before = view.text()

        await view.find('input').setValue('Noch eine')
        await view.find('form').trigger('submit')
        await flushPromises()

        expect(push).not.toHaveBeenCalled()
        expect(view.text()).not.toBe(before)
        expect(view.text()).toContain('bereits eine Wache')
    })
})
