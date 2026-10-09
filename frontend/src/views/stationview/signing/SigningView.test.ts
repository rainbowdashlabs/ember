/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import SigningView from './SigningView.vue'
import type {BatchStartResponse, OpenSignatureResponse} from '@/api/generated/schema'

const getOpenSignatures = vi.hoisted(() => vi.fn())
const getSigningFillIns = vi.hoisted(() => vi.fn())
const getSigningDocument = vi.hoisted(() => vi.fn())
const startBatchSigning = vi.hoisted(() => vi.fn())
const completeBatchSigning = vi.hoisted(() => vi.fn())
const getSignatureImage = vi.hoisted(() => vi.fn())
const getWebAuthnCredential = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {} as Record<string, string>}))

vi.mock('@/api/signing', () => ({
    getOpenSignatures,
    getSigningFillIns,
    getSigningDocument,
    startBatchSigning,
    completeBatchSigning,
    getSignatureImage,
    getSignatureSettings: vi.fn(),
    saveSignatureImage: vi.fn(),
    deleteSignatureImage: vi.fn(),
    setSignatureConsent: vi.fn(),
}))

vi.mock('@/util/webauthn', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@/util/webauthn')>()),
    getWebAuthnCredential,
    isWebAuthnSupported: () => true,
}))

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRoute: () => route,
}))

const STUBS = {DocumentPdfFrame: true, DevCodeHint: true, SignaturePad: true}
const CAMP = '11111111-1111-1111-1111-111111111111'
const PHOTOS = '22222222-2222-2222-2222-222222222222'
const DRAWN = {dataUrl: 'data:image/png;base64,QkVO', source: 'DRAWN' as const}

function field(fieldId: number, overrides: Partial<OpenSignatureResponse>): OpenSignatureResponse {
    return {
        fieldId,
        requestUid: CAMP,
        documentId: 3,
        documentTitle: 'Zeltlager',
        memberName: 'Ben Beispiel',
        fieldName: 'guardian1',
        role: 'GUARDIAN',
        capacity: 'GUARDIAN',
        memberId: 21,
        signerName: 'Jana Beispiel',
        statement: 'Ich stimme zu.',
        ...overrides,
    }
}

/** A guardian with two children: their field on Ben's camp form, Ben's own through the account, Mia's photos. */
const OPEN = [
    field(1, {}),
    field(2, {fieldName: 'participant', role: 'PARTICIPANT', capacity: 'MEMBER_THROUGH_ACCOUNT', signerName: 'Ben Beispiel'}),
    field(3, {requestUid: PHOTOS, documentId: 4, documentTitle: 'Fotoerlaubnis', memberName: 'Mia Beispiel', memberId: 22}),
]

function started(overrides: Partial<BatchStartResponse> = {}): BatchStartResponse {
    return {
        startToken: 'token-1',
        expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
        batchUid: '33333333-3333-3333-3333-333333333333',
        fields: [],
        acceptedProofs: ['TOTP'],
        webAuthnOptionsJson: null,
        ...overrides,
    }
}

async function open() {
    const view = await mountSuspended(SigningView, {global: {stubs: STUBS}, attachTo: document.body})
    await flushPromises()
    return view
}

type View = Awaited<ReturnType<typeof open>>

async function click(view: View, testId: string) {
    await view.get(`[data-testid="${testId}"]`).trigger('click')
    await flushPromises()
}

function progress(view: View): string {
    return view.get('[data-testid="signing-progress"]').text()
}

/** Walks through the document screens, agreeing to every field and typing the phone number where asked. */
async function agreeToDocuments(view: View, documents: number) {
    for (let index = 0; index < documents; index++) {
        for (const box of view.findAll('[data-testid^="signing-agree-"]')) await box.setValue(true)
        const phone = view.find('[data-testid="signing-fill-in-fill-guardian1-0"]')
        if (phone.exists()) await phone.setValue('0171 2345678')
        await click(view, 'signing-next')
    }
}

/** Lets Ben draw his own signature on his picture step, the reader keeping their saved one. */
async function pictures(view: View) {
    await click(view, 'signing-next')
    view.findComponent({name: 'SignaturePad'}).vm.$emit('update:modelValue', DRAWN)
    await flushPromises()
    await click(view, 'signing-next')
}

/**
 * Signing in one go: every open field across two documents and two children ticked on the overview, each
 * document agreed to on its own screen, the reader's picture once and Ben's on his own step, a check, and
 * one confirmation that starts every ticked field in order and sends one picture per person.
 */
describe('SigningView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        route.params = {}
        route.query = {}
        URL.createObjectURL = vi.fn(() => 'blob:signing-document')
        URL.revokeObjectURL = vi.fn()
        getOpenSignatures.mockResolvedValue(OPEN)
        getSigningFillIns.mockImplementation(async (fieldId: number) => fieldId === 1
            ? [{name: 'fill-guardian1-0', label: 'Telefon im Notfall', required: true, maxLength: 30}]
            : [])
        getSigningDocument.mockResolvedValue(new Blob(['%PDF-1.7'], {type: 'application/pdf'}))
        getSignatureImage.mockResolvedValue(new Blob(['png'], {type: 'image/png'}))
        startBatchSigning.mockResolvedValue(started())
        completeBatchSigning.mockResolvedValue({fields: [{}, {}, {}]})
        getWebAuthnCredential.mockResolvedValue('{"id":"cred"}')
    })

    it('signs two documents of two children with one code and one picture per person', async () => {
        const view = await open()

        expect(progress(view)).toBe('Schritt 1 von 7')
        expect(view.get('[data-testid="signing-overview"]').text()).toContain('Fotoerlaubnis: du als erziehungsberechtigte Person für Mia Beispiel')
        expect(view.get('[data-testid="signing-overview"]').text()).toContain('Zeltlager: Ben Beispiel selbst')
        await click(view, 'signing-start')
        expect(view.text()).toContain('Dokument 1 von 2: Zeltlager')
        await agreeToDocuments(view, 2)
        await pictures(view)
        expect(view.get('[data-testid="signing-check-summary"]').text()).toBe('Du unterschreibst jetzt 3 Felder in 2 Dokumenten.')
        await click(view, 'signing-next')

        expect(startBatchSigning).toHaveBeenCalledWith([
            {fieldId: 1, entries: [{field: 'fill-guardian1-0', value: '0171 2345678'}]},
            {fieldId: 2, entries: []},
            {fieldId: 3, entries: []},
        ])
        await view.get('[data-testid="signing-proof"] input').setValue('424242')
        await view.get('[data-testid="signing-proof"] form').trigger('submit')
        await flushPromises()

        expect(completeBatchSigning).toHaveBeenCalledWith({
            startToken: 'token-1',
            proof: 'TOTP',
            secret: '424242',
            pictures: [{memberId: 21, signatureImage: 'QkVO', signatureSource: 'DRAWN'}],
        })
        expect(view.get('[data-testid="signing-done"]').text()).toContain('Du bekommst eine Kopie per Mail.')
        view.unmount()
    })

    it('leaves an unticked field out and confirms the rest with one passkey answer', async () => {
        startBatchSigning.mockResolvedValue(started({acceptedProofs: ['PASSKEY'], webAuthnOptionsJson: '{"publicKey":{}}'}))
        const view = await open()

        await view.get('[data-testid="signing-choose-3"]').setValue(false)
        await click(view, 'signing-start')
        expect(progress(view)).toBe('Schritt 2 von 6')
        await agreeToDocuments(view, 1)
        await pictures(view)
        await click(view, 'signing-next')
        await click(view, 'signing-authenticator')

        expect(startBatchSigning).toHaveBeenCalledWith([
            {fieldId: 1, entries: [{field: 'fill-guardian1-0', value: '0171 2345678'}]},
            {fieldId: 2, entries: []},
        ])
        expect(getWebAuthnCredential).toHaveBeenCalledTimes(1)
        expect(completeBatchSigning).toHaveBeenCalledWith(expect.objectContaining({
            proof: 'PASSKEY',
            credentialJson: '{"id":"cred"}',
            pictures: [{memberId: 21, signatureImage: 'QkVO', signatureSource: 'DRAWN'}],
        }))
        view.unmount()
    })

    it('keeps what was ticked and typed when going back and forth', async () => {
        const view = await open()
        await click(view, 'signing-start')
        for (const box of view.findAll('[data-testid^="signing-agree-"]')) await box.setValue(true)
        await view.get('[data-testid="signing-fill-in-fill-guardian1-0"]').setValue('0171 2345678')
        await click(view, 'signing-next')

        expect(view.text()).toContain('Dokument 2 von 2: Fotoerlaubnis')
        await click(view, 'signing-back')
        expect((view.get('[data-testid="signing-fill-in-fill-guardian1-0"]').element as HTMLInputElement).value).toBe('0171 2345678')
        expect(view.findAll('[data-testid^="signing-agree-"]').every(box => (box.element as HTMLInputElement).checked)).toBe(true)
        await click(view, 'signing-back')
        expect(view.findAll('[data-testid="signing-overview"] input').every(box => (box.element as HTMLInputElement).checked)).toBe(true)
        view.unmount()
    })

    it('puts the document of the field it was opened for first, and ticks only the fields the address names', async () => {
        route.params = {fieldId: '3'}
        const first = await open()
        expect(first.get('[data-testid="signing-overview"] li').text()).toContain('Fotoerlaubnis')
        first.unmount()

        route.params = {}
        route.query = {fields: '3'}
        const named = await open()
        const ticked = named.findAll('[data-testid="signing-overview"] input')
            .filter(box => (box.element as HTMLInputElement).checked)
        expect(ticked).toHaveLength(1)
        expect(progress(named)).toBe('Schritt 1 von 5')
        named.unmount()
    })

    it('says plainly when nothing waits', async () => {
        getOpenSignatures.mockResolvedValue([])
        const view = await open()
        expect(view.find('[data-testid="signing-nothing-open"]').exists()).toBe(true)
        view.unmount()
    })
})
