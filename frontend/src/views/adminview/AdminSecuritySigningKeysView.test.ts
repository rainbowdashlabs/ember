/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import AdminSecuritySigningKeysView from './AdminSecuritySigningKeysView.vue'
import {SigningKeyKind} from '@/api/generated/schema'
import type {SigningKeyStatus} from '@/api/generated/schema'

const getSigningKeyStatus = vi.fn()
const recoverSigningKeys = vi.fn()

vi.mock('@/api', () => ({
    signing: {
        getSigningKeyStatus: (...args: unknown[]) => getSigningKeyStatus(...args),
        recoverSigningKeys: (...args: unknown[]) => recoverSigningKeys(...args),
    },
}))

const locked: SigningKeyStatus = {
    locked: [
        {
            kind: SigningKeyKind.AUTHORITY,
            serialNumber: 'ab12',
            sha256Fingerprint: 'AB:12',
            active: true,
            stationName: null,
            validUntil: '2046-01-01T00:00:00Z',
        },
        {
            kind: SigningKeyKind.STATION_KEY,
            serialNumber: 'cd34',
            sha256Fingerprint: 'CD:34',
            active: false,
            stationName: 'Jugendfeuerwehr Musterstadt',
            validUntil: '2031-01-01T00:00:00Z',
        },
    ],
    openKeys: 0,
    recoveries: [],
}

const recovered: SigningKeyStatus = {
    locked: [],
    openKeys: 0,
    recoveries: [{
        id: 1,
        recoveredAt: '2026-10-08T10:00:00Z',
        recoveredBy: 'Ada Admin',
        authoritySerials: ['ab12'],
        stationKeySerials: ['cd34'],
    }],
}

/**
 * The administrator's page for signing keys that no longer open: it lists them, offers the recovery
 * only behind a ticked box, and sends exactly the serial numbers it showed.
 *
 * @vitest-environment happy-dom
 */
describe('AdminSecuritySigningKeysView', () => {
    function page() {
        return mount(AdminSecuritySigningKeysView, {
            attachTo: document.body,
            global: {stubs: {ViewContent: {template: '<div><slot/></div>'}}},
        })
    }

    beforeEach(() => {
        document.body.innerHTML = ''
        getSigningKeyStatus.mockReset()
        recoverSigningKeys.mockReset()
    })

    it('says so when every key opens', async () => {
        getSigningKeyStatus.mockResolvedValue({locked: [], openKeys: 2, recoveries: []})
        const view = page()
        await flushPromises()

        expect(view.find('[data-testid="signing-keys-open"]').text()).toContain('2')
        expect(view.find('[data-testid="signing-keys-recover"]').exists()).toBe(false)
    })

    it('lists the keys that no longer open with their station and serial number', async () => {
        getSigningKeyStatus.mockResolvedValue(locked)
        const view = page()
        await flushPromises()

        const panel = view.find('[data-testid="signing-keys-locked"]')
        expect(panel.text()).toContain('Zertifizierungsstelle')
        expect(panel.text()).toContain('Jugendfeuerwehr Musterstadt')
        expect(panel.text()).toContain('ab12')
        expect(panel.text()).toContain('cd34')
    })

    it('gives the keys up only after the box is ticked, with exactly the serial numbers shown', async () => {
        getSigningKeyStatus.mockResolvedValueOnce(locked).mockResolvedValueOnce(recovered)
        recoverSigningKeys.mockResolvedValue(recovered.recoveries[0])
        const view = page()
        await flushPromises()

        await view.find('[data-testid="signing-keys-recover"]').trigger('click')
        await flushPromises()
        const confirm = () => document.querySelector<HTMLButtonElement>('[data-testid="signing-keys-confirm"]')
        expect(confirm()?.disabled).toBe(true)

        const box = document.querySelector<HTMLInputElement>('[data-testid="signing-keys-confirm-check"]')
        box!.click()
        await flushPromises()
        expect(confirm()?.disabled).toBe(false)
        confirm()!.click()
        await flushPromises()

        expect(recoverSigningKeys).toHaveBeenCalledWith(['ab12', 'cd34'])
        expect(view.find('[data-testid="signing-keys-recovered"]').exists()).toBe(true)
        expect(view.find('[data-testid="signing-keys-open"]').exists()).toBe(true)
        expect(view.text()).toContain('Ada Admin')
    })
})
