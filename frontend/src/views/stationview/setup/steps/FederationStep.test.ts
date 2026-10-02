/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import FederationStep from './FederationStep.vue'

const getStationInfo = vi.fn()
const updateStationName = vi.fn()
const push = vi.fn()

vi.mock('@/api', () => ({
    stationManage: {
        getStationInfo: (...args: unknown[]) => getStationInfo(...args),
        updateStationName: (...args: unknown[]) => updateStationName(...args),
    },
}))

vi.mock('@/composables/useSetupStatus', () => ({
    useSetupStatus: () => ({reload: async () => undefined}),
}))

vi.mock('vue-router', async (original) => ({
    ...await original<typeof import('vue-router')>(),
    useRouter: () => ({push}),
}))

/**
 * The discovery step of the setup: the station sees what each listing means and what a public one
 * hands out, and can keep the setting it started with.
 *
 * @vitest-environment happy-dom
 */
describe('FederationStep', () => {
    const SetupLayout = {
        emits: ['save'],
        template: '<div><slot/><button data-testid="save" @click="$emit(\'save\')">save</button></div>',
    }

    function step() {
        return mount(FederationStep, {global: {stubs: {SetupLayout}}})
    }

    beforeEach(() => {
        getStationInfo.mockReset()
        updateStationName.mockReset()
        push.mockReset()
        getStationInfo.mockResolvedValue({
            name: 'Wache Neu',
            discoveryVisibility: 'PUBLIC',
            discoveryDescription: null,
            discoveryShowKb: true,
        })
        updateStationName.mockResolvedValue(undefined)
    })

    it('explains every listing and what a public one shows and where it goes', async () => {
        const view = step()
        await flushPromises()

        for (const value of ['PUBLIC', 'INSTANCE', 'NONE']) {
            expect(view.find(`[data-testid="visibility-${value}"]`).exists()).toBe(true)
        }
        const contents = view.find('[data-testid="public-listing-contents"]').text()
        expect(contents).toContain('der Name der Wache')
        expect(contents).toContain('die Position auf der Karte')
        expect(contents).toContain('die ungefähre Mitgliederzahl')
        expect(contents).toContain('Andere Instanzen holen diese Angaben regelmäßig ab')
        expect(contents).toContain('Föderation → Einstellungen')
    })

    it('opens on the setting the station has', async () => {
        getStationInfo.mockResolvedValue({name: 'Alte Wache', discoveryVisibility: 'NONE', discoveryDescription: null})
        const view = step()
        await flushPromises()

        const checked = view.find('[data-testid="visibility-NONE"] input').element as HTMLInputElement
        expect(checked.checked).toBe(true)
    })

    it('saves the default unchanged and moves on', async () => {
        const view = step()
        await flushPromises()

        await view.find('[data-testid="save"]').trigger('click')
        await flushPromises()

        expect(updateStationName).toHaveBeenCalledWith({
            name: 'Wache Neu',
            discoveryVisibility: 'PUBLIC',
            discoveryDescription: '',
            discoveryShowKb: true,
        })
        expect(push).toHaveBeenCalledWith({name: 'station-setup-invites'})
    })

    it('saves the listing the station chose instead', async () => {
        const view = step()
        await flushPromises()

        await view.find('[data-testid="visibility-INSTANCE"] input').setValue(true)
        await view.find('[data-testid="save"]').trigger('click')
        await flushPromises()

        expect(updateStationName).toHaveBeenCalledWith(expect.objectContaining({discoveryVisibility: 'INSTANCE'}))
    })
})
