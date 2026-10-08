/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import SigningView from './SigningView.vue'
import type {OpenSignatureResponse, SigningCompleteResponse, SigningStartResponse} from '@/api/generated/schema'

const getSigningField = vi.hoisted(() => vi.fn())
const getSigningDocument = vi.hoisted(() => vi.fn())
const startSigning = vi.hoisted(() => vi.fn())
const completeSigning = vi.hoisted(() => vi.fn())
const getSignatureImage = vi.hoisted(() => vi.fn())
const getSigningFillIns = vi.hoisted(() => vi.fn())

vi.mock('@/api/signing', () => ({
    getSigningField,
    getSigningFillIns,
    getSigningDocument,
    startSigning,
    completeSigning,
    getSignatureImage,
    getSignatureSettings: vi.fn(),
    saveSignatureImage: vi.fn(),
    deleteSignatureImage: vi.fn(),
    setSignatureConsent: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRoute: () => ({params: {fieldId: '7'}}),
}))

const STUBS = {DocumentPdfFrame: true, DevCodeHint: true}

function field(overrides: Partial<OpenSignatureResponse> = {}): OpenSignatureResponse {
    return {
        fieldId: 7,
        requestUid: '11111111-1111-1111-1111-111111111111',
        documentId: 3,
        documentTitle: 'Einverständnis Zeltlager',
        memberName: 'Mia Beispiel',
        fieldName: 'guardian1',
        role: 'GUARDIAN',
        capacity: 'GUARDIAN',
        memberId: 12,
        signerName: 'Jana Beispiel',
        statement: 'Ich bin erziehungsberechtigt und stimme zu.',
        ...overrides,
    }
}

function started(overrides: Partial<SigningStartResponse> = {}): SigningStartResponse {
    return {
        startToken: 'token-1',
        expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
        fieldId: 7,
        requestUid: '11111111-1111-1111-1111-111111111111',
        fieldName: 'guardian1',
        role: 'GUARDIAN',
        capacity: 'GUARDIAN',
        statement: 'Ich bin erziehungsberechtigt und stimme zu.',
        signerName: 'Jana Beispiel',
        accountHolderName: 'Jana Beispiel',
        memberName: 'Mia Beispiel',
        documentMemberName: 'Mia Beispiel',
        contentSha256: 'ab'.repeat(32),
        acceptedProofs: ['TOTP'],
        webAuthnOptionsJson: null,
        ...overrides,
    }
}

function signed(overrides: Partial<SigningCompleteResponse> = {}): SigningCompleteResponse {
    return {
        fieldId: 7,
        fieldName: 'guardian1',
        state: 'SIGNED',
        settledAt: '2026-10-08T10:00:00Z',
        requestUid: '11111111-1111-1111-1111-111111111111',
        requestState: 'OPEN',
        proof: 'TOTP',
        bound: false,
        ...overrides,
    }
}

async function open() {
    const view = await mountSuspended(SigningView, {global: {stubs: STUBS}, attachTo: document.body})
    await flushPromises()
    return view
}

async function tickAndProceed(view: Awaited<ReturnType<typeof open>>) {
    await view.get('input[type="checkbox"]').setValue(true)
    await view.get('[data-testid="signing-proceed"]').trigger('click')
    await flushPromises()
}

async function typeCode(view: Awaited<ReturnType<typeof open>>, code: string) {
    await view.get('[data-testid="signing-proof"] input').setValue(code)
    await view.get('[data-testid="signing-proof"] form').trigger('submit')
    await flushPromises()
}

/**
 * Signing one field: the document, the statement and the proof on one page, the proof offered only
 * once the statement is ticked, the focus following each step, a confirmation that fails leaving a
 * fresh start for the next try, and a field that no longer waits saying so.
 */
describe('SigningView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        URL.createObjectURL = vi.fn(() => 'blob:signing-document')
        URL.revokeObjectURL = vi.fn()
        getSigningField.mockResolvedValue(field())
        getSigningDocument.mockResolvedValue(new Blob(['%PDF-1.7'], {type: 'application/pdf'}))
        startSigning.mockResolvedValue(started())
        completeSigning.mockResolvedValue(signed())
        getSignatureImage.mockResolvedValue(new Blob(['png'], {type: 'image/png'}))
        getSigningFillIns.mockResolvedValue([])
    })

    it('asks for the fields to fill in, waits for the required ones and binds what was typed', async () => {
        getSigningFillIns.mockResolvedValue([
            {name: 'fill-guardian1-0', label: 'Telefon im Notfall', required: true, maxLength: 30},
            {name: 'fill-guardian1-1', label: 'Allergien', required: false, maxLength: 500},
        ])
        const view = await open()

        const group = view.get('[data-testid="signing-fill-ins"]')
        expect(group.element.tagName).toBe('FIELDSET')
        const phone = view.get('[data-testid="signing-fill-in-fill-guardian1-0"]')
        expect(phone.attributes('aria-required')).toBe('true')
        expect(phone.attributes('maxlength')).toBe('30')
        expect(view.get(`label[for="${phone.attributes('id')}"]`).text()).toBe('Telefon im Notfall (Pflichtangabe)')
        await view.get('input[type="checkbox"]').setValue(true)
        expect(view.get('[data-testid="signing-proceed"]').attributes('disabled')).toBeDefined()

        await phone.setValue('0171 2345678')
        await tickAndProceed(view)

        expect(startSigning).toHaveBeenCalledWith(7, [{field: 'fill-guardian1-0', value: '0171 2345678'}])
        expect(view.get('[data-testid="signing-fill-in-fill-guardian1-0"]').attributes('disabled')).toBeDefined()
        view.unmount()
    })

    it('signs with the saved signature picture unless the signer draws a new one', async () => {
        const view = await open()
        await tickAndProceed(view)

        const mark = view.get('[data-testid="signing-mark"]')
        expect(mark.find('img').attributes('alt')).toBe('Deine gespeicherte Unterschrift')
        expect(view.find('[data-testid="signature-pad"]').exists()).toBe(false)

        await view.get('[data-testid="signing-mark-new"]').trigger('click')

        expect(view.find('[data-testid="signature-pad"]').exists()).toBe(true)
        expect(view.get('[data-testid="signing-proof"] form input').attributes('disabled')).toBeDefined()
        view.unmount()
    })

    it('asks for a picture where none is saved and waits for it before the proof', async () => {
        getSignatureImage.mockResolvedValue(null)
        const view = await open()
        await tickAndProceed(view)

        expect(view.find('[data-testid="signature-pad"]').exists()).toBe(true)
        expect(view.text()).toContain('Für das nächste Mal speichern')
        expect(view.get('[data-testid="signing-proof"] form input').attributes('disabled')).toBeDefined()
        view.unmount()
    })

    it('lets a child signing through the account draw its own picture and never keeps it', async () => {
        startSigning.mockResolvedValue(started({capacity: 'MEMBER_THROUGH_ACCOUNT'}))
        const view = await open()
        await tickAndProceed(view)

        const mark = view.get('[data-testid="signing-mark"]')
        expect(mark.text()).toContain('Mia Beispiel zeichnet die Unterschrift hier selbst.')
        expect(mark.find('img').exists()).toBe(false)
        expect(mark.text()).not.toContain('Für das nächste Mal speichern')
        view.unmount()
    })

    it('shows the document, the capacity and the statement, and waits for the tick', async () => {
        const view = await open()

        expect(getSigningField).toHaveBeenCalledWith(7)
        expect(getSigningDocument).toHaveBeenCalledWith(7)
        expect(view.get('[data-testid="signing-capacity"]').text())
            .toBe('Du unterschreibst als erziehungsberechtigte Person für Mia Beispiel.')
        expect(view.get('[data-testid="signing-statement"]').text()).toBe('Ich bin erziehungsberechtigt und stimme zu.')
        expect(view.text()).toContain('Dokument speichern')
        expect(view.text()).toContain('Im PDF-Betrachter öffnen')
        expect(view.get('[data-testid="signing-proceed"]').attributes('disabled')).toBeDefined()
        expect(view.find('[data-testid="signing-proof"]').exists()).toBe(false)
        view.unmount()
    })

    it('labels the box with the statement it confirms', async () => {
        const view = await open()

        const box = view.get('input[type="checkbox"]')
        const label = view.get(`label[for="${box.attributes('id')}"]`)
        expect(label.text()).toBe('Ich habe das Dokument gelesen und gebe diese Erklärung ab.')
        expect(box.attributes('aria-describedby')).toBe(view.get('[data-testid="signing-statement"]').attributes('id'))
        view.unmount()
    })

    it('starts the act once ticked, moves the focus to the proof and announces it', async () => {
        const view = await open()

        await tickAndProceed(view)

        expect(startSigning).toHaveBeenCalledWith(7, [])
        const proof = view.get('[data-testid="signing-proof"]')
        expect(proof.text()).toContain('Es unterschreibt Jana Beispiel, bestätigt über das Konto von Jana Beispiel.')
        expect(proof.text()).toContain('Code aus deiner Authenticator-App')
        expect(proof.text()).not.toContain('Passwort')
        expect(document.activeElement?.textContent?.trim()).toBe('3. Unterschrift bestätigen')
        expect(view.get('[data-testid="signing-announcement"]').text())
            .toBe('Die Unterschrift ist vorbereitet. Wähle, wie du sie bestätigst.')
        view.unmount()
    })

    it('offers the password where the account has no second factor', async () => {
        startSigning.mockResolvedValue(started({acceptedProofs: ['PASSWORD']}))
        const view = await open()

        await tickAndProceed(view)

        const proof = view.get('[data-testid="signing-proof"]')
        expect(proof.text()).toContain('Dein Passwort')
        expect(proof.find('input[type="password"]').exists()).toBe(true)
        expect(proof.find('[data-testid="signing-authenticator"]').exists()).toBe(false)
        view.unmount()
    })

    it('signs with the code and shows the result in place of the steps', async () => {
        const view = await open()
        await tickAndProceed(view)

        await typeCode(view, '424242')

        expect(completeSigning).toHaveBeenCalledWith(7, {startToken: 'token-1', proof: 'TOTP', secret: '424242'})
        const done = view.get('[data-testid="signing-done"]')
        expect(done.text()).toContain('Unterschrieben')
        expect(done.text()).toContain('Bestätigt mit dem Code aus der Authenticator-App.')
        expect(done.text()).toContain('Das Dokument wartet noch auf weitere Unterschriften.')
        expect(document.activeElement?.textContent?.trim()).toBe('Unterschrieben')
        expect(view.get('[data-testid="signing-announcement"]').text()).toBe('Unterschrieben.')
        view.unmount()
    })

    it('starts afresh after a wrong code, since the refused start is spent', async () => {
        completeSigning.mockRejectedValueOnce(Object.assign(new Error('wrong'), {
            response: {status: 403, data: {code: 'D-158', message: 'wrong'}},
        }))
        startSigning.mockResolvedValueOnce(started()).mockResolvedValueOnce(started({startToken: 'token-2'}))
        const view = await open()
        await tickAndProceed(view)

        await typeCode(view, '111111')

        expect(view.text()).toContain('Dieser Code war nicht richtig, es wurde nichts unterschrieben')
        expect(view.text()).toContain('Es wurde nichts unterschrieben. Versuche es noch einmal.')

        await typeCode(view, '424242')

        expect(startSigning).toHaveBeenCalledTimes(2)
        expect(completeSigning).toHaveBeenLastCalledWith(7, {startToken: 'token-2', proof: 'TOTP', secret: '424242'})
        expect(view.find('[data-testid="signing-done"]').exists()).toBe(true)
        view.unmount()
    })

    it('asks for no signature on a document that could not be opened', async () => {
        getSigningDocument.mockRejectedValue(Object.assign(new Error('changed'), {
            response: {status: 409, data: {code: 'D-148', message: 'changed'}},
        }))
        const view = await open()

        await view.get('input[type="checkbox"]').setValue(true)

        expect(view.get('[data-testid="signing-proceed"]').attributes('disabled')).toBeDefined()
        expect(view.text()).not.toContain('Dokument speichern')
        view.unmount()
    })

    it('says plainly when the field no longer waits, with the way back', async () => {
        getSigningField.mockRejectedValue(Object.assign(new Error('not open'), {
            response: {status: 409, data: {code: 'D-143', message: 'not open'}},
        }))
        const view = await open()

        const unavailable = view.get('[data-testid="signing-unavailable"]')
        expect(unavailable.text()).toContain('Zu den offenen Aufgaben')
        expect(getSigningDocument).not.toHaveBeenCalled()
        view.unmount()
    })
})
