/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import AccountSignatureView from './AccountSignatureView.vue'
import type {SignatureSettingsResponse} from '@/api/generated/schema'

const getSignatureSettings = vi.hoisted(() => vi.fn())
const getSignatureImage = vi.hoisted(() => vi.fn())
const saveSignatureImage = vi.hoisted(() => vi.fn())
const deleteSignatureImage = vi.hoisted(() => vi.fn())
const setSignatureConsent = vi.hoisted(() => vi.fn())

vi.mock('@/api/signing', () => ({
    SIGNATURE_IMAGE_MAX_BYTES: 5 * 1024 * 1024,
    getSignatureSettings,
    getSignatureImage,
    saveSignatureImage,
    deleteSignatureImage,
    setSignatureConsent,
}))

function settings(overrides: Partial<SignatureSettingsResponse> = {}): SignatureSettingsResponse {
    return {hasImage: false, imageSource: null, imageSavedAt: null, autoSignConsentedAt: null, ...overrides}
}

async function open() {
    const view = await mountSuspended(AccountSignatureView)
    await flushPromises()
    return view
}

/**
 * The own signature in the account settings: the saved picture shown with how it was made, deleted on
 * request, and the consent given and taken back, with a warning while no picture backs it.
 */
describe('AccountSignatureView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        URL.createObjectURL = vi.fn(() => 'blob:signature')
        URL.revokeObjectURL = vi.fn()
        getSignatureImage.mockResolvedValue(new Blob(['png'], {type: 'image/png'}))
    })

    it('shows the saved picture and deletes it on request', async () => {
        getSignatureSettings.mockResolvedValue(settings({
            hasImage: true, imageSource: 'DRAWN', imageSavedAt: '2026-10-08T09:25:00Z',
        }))
        deleteSignatureImage.mockResolvedValue(settings())
        const view = await open()

        const saved = view.get('[data-testid="signature-saved"]')
        expect(saved.get('img').attributes('src')).toBe('blob:signature')
        expect(saved.text()).toContain('Gezeichnet am')

        await view.get('[data-testid="signature-delete"]').trigger('click')
        await flushPromises()

        expect(deleteSignatureImage).toHaveBeenCalled()
        expect(view.get('[data-testid="signature-saved"]').text()).toContain('Noch keine Unterschrift gespeichert')
        view.unmount()
    })

    it('gives the consent and says that letters stay unsigned while no picture is saved', async () => {
        getSignatureSettings.mockResolvedValue(settings())
        setSignatureConsent.mockResolvedValue(settings({autoSignConsentedAt: '2026-10-08T09:30:00Z'}))
        const view = await open()

        await view.get('[data-testid="signature-consent"] [role="switch"]').trigger('click')
        await flushPromises()

        expect(setSignatureConsent).toHaveBeenCalledWith(true)
        const consent = view.get('[data-testid="signature-consent"]')
        expect(consent.find('[data-testid="signature-consented-at"]').exists()).toBe(true)
        expect(consent.text()).toContain('Bis dahin bleiben deine Briefe ohne Unterschrift.')
        view.unmount()
    })

    it('offers drawing, typing and an upload for a new picture', async () => {
        getSignatureSettings.mockResolvedValue(settings())
        const view = await open()

        const fresh = view.get('[data-testid="signature-new"]')
        expect(fresh.find('[data-testid="signature-canvas"]').exists()).toBe(true)
        expect(fresh.text()).toContain('Name tippen')
        expect(fresh.text()).toContain('Foto oder Scan wählen')
        expect(view.get('[data-testid="signature-save"]').attributes('disabled')).toBeDefined()
        view.unmount()
    })
})
