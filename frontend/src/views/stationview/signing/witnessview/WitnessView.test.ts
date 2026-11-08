/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {DOMWrapper, flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import WitnessView from './WitnessView.vue'
import type {BatchStartResponse, WitnessFieldResponse} from '@/api/generated/schema'

const getWitnessPeople = vi.hoisted(() => vi.fn())
const getWitnessOffers = vi.hoisted(() => vi.fn())
const generateForWitness = vi.hoisted(() => vi.fn())
const getWitnessField = vi.hoisted(() => vi.fn())
const getWitnessDocument = vi.hoisted(() => vi.fn())
const getWitnessFillIns = vi.hoisted(() => vi.fn())
const startWitnessedSigning = vi.hoisted(() => vi.fn())
const completeWitnessedSigning = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {} as Record<string, string>}))

vi.mock('@/api/witnessSigning', () => ({
    getWitnessPeople,
    getWitnessOffers,
    generateForWitness,
    getWitnessField,
    getWitnessDocument,
    getWitnessFillIns,
    startWitnessedSigning,
    completeWitnessedSigning,
}))

vi.mock('@/api/memberGroups', () => ({listGroups: vi.fn(async () => [])}))

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRoute: () => route,
}))

const STUBS = {DocumentPdfFrame: true, DevCodeHint: true, SignaturePad: true}
const DRAWN = {dataUrl: 'data:image/png;base64,VElN', source: 'DRAWN' as const}
const TIM = {memberId: 5, name: 'Tim Probe', userType: 'TRIAL' as const}
const GRETA = {memberId: 6, name: 'Greta Probe', userType: 'MEMBER' as const}
const OLAF = {memberId: 7, name: 'Olaf Probe', userType: 'MEMBER' as const}

/** A person as the witness's list names them, with how many signatures wait on them. */
function listed(person: typeof TIM | typeof GRETA, openFields: number) {
    return {
        ...person,
        identity: {stationUid: '00000000-0000-4000-a000-000000000001', memberUid: `00000000-0000-0000-0000-00000000000${person.memberId}`},
        groupIds: [],
        openFields,
        openDocuments: openFields > 0 ? ['Fotoerlaubnis'] : [],
    }
}

function field(fieldId: number, overrides: Partial<WitnessFieldResponse> = {}): WitnessFieldResponse {
    return {
        fieldId,
        requestUid: '11111111-1111-1111-1111-111111111111',
        documentId: 3,
        documentTitle: 'Fotoerlaubnis',
        memberId: TIM.memberId,
        memberName: TIM.name,
        fieldName: 'participant',
        role: 'PARTICIPANT',
        statement: 'Ich erlaube Fotos.',
        signers: [TIM],
        ...overrides,
    }
}

function started(): BatchStartResponse {
    return {
        startToken: 'token-w',
        expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
        batchUid: '33333333-3333-3333-3333-333333333333',
        fields: [],
        acceptedProofs: ['TOTP'],
        webAuthnOptionsJson: null,
    }
}

async function open() {
    const view = await mountSuspended(WitnessView, {global: {stubs: STUBS}, attachTo: document.body})
    await flushPromises()
    return view
}

type View = Awaited<ReturnType<typeof open>>

async function click(view: View, testId: string) {
    await view.get(`[data-testid="${testId}"]`).trigger('click')
    await flushPromises()
}

/** A control on the screens the person holds the device for. */
function handedOver(testId: string): DOMWrapper<Element> {
    const element = document.body.querySelector(`[data-testid="witness-handed-over-screen"] [data-testid="${testId}"]`)
    if (!element) throw new Error(`${testId} is not on the handed over screen`)
    return new DOMWrapper(element)
}

async function clickHandedOver(testId: string) {
    await handedOver(testId).trigger('click')
    await flushPromises()
}

/** The person reads and agrees, draws and hands the device back. */
async function signAsThePerson(view: View) {
    await clickHandedOver('witness-handed-over')
    await handedOver('witness-agree').setValue(true)
    await clickHandedOver('signing-next')
    view.findComponent({name: 'SignaturePad'}).vm.$emit('update:modelValue', DRAWN)
    await flushPromises()
    await clickHandedOver('signing-next')
    await clickHandedOver('witness-taken-back')
}

/**
 * Signing in front of a witness: the witness picks the person and a field, the sidebar and header go inert
 * while the person holds the device, the person agrees and draws, and the witness confirms with their own code; a
 * field any guardian may sign asks who is there; a field opened from its tile starts at the hand over; a
 * document made from a template leads straight to its one field.
 */
describe('WitnessView', () => {
    let chrome: HTMLElement

    beforeEach(() => {
        vi.clearAllMocks()
        route.query = {}
        chrome = document.createElement('nav')
        chrome.setAttribute('data-shell-chrome', '')
        document.body.appendChild(chrome)
        URL.createObjectURL = vi.fn(() => 'blob:witness-document')
        URL.revokeObjectURL = vi.fn()
        getWitnessPeople.mockResolvedValue([listed(GRETA, 0), listed(TIM, 1)])
        getWitnessOffers.mockResolvedValue({fields: [field(9)], templates: [{templateId: 4, name: 'Fotoerlaubnis'}]})
        getWitnessFillIns.mockResolvedValue([])
        getWitnessDocument.mockResolvedValue(new Blob(['%PDF-1.7'], {type: 'application/pdf'}))
        startWitnessedSigning.mockResolvedValue(started())
        completeWitnessedSigning.mockResolvedValue({fields: [{}]})
    })

    afterEach(() => {
        chrome.remove()
    })

    it('collects a trial user\'s signature and confirms it with the witness\'s code', async () => {
        const view = await open()

        expect(view.get('[data-testid="witness-people"]').text()).toContain('Fotoerlaubnis')
        await click(view, 'witness-person-5')
        expect(getWitnessOffers).toHaveBeenCalledWith(5)
        await click(view, 'witness-field-9')

        expect(chrome.hasAttribute('inert'), 'the sidebar and header stay but are inert').toBe(true)
        expect(handedOver('signing-progress').text()).toBe('Schritt 4 von 8')
        await signAsThePerson(view)
        expect(chrome.hasAttribute('inert')).toBe(false)

        expect(view.get('[data-testid="witness-statement"]').text()).toBe('Ich bestätige, dass Tim Probe vor mir unterschrieben hat.')
        expect(startWitnessedSigning).toHaveBeenCalledWith(9, 5, [])
        await view.get('[data-testid="signing-proof"] input').setValue('424242')
        await view.get('[data-testid="signing-proof"] form').trigger('submit')
        await flushPromises()

        expect(completeWitnessedSigning).toHaveBeenCalledWith({
            startToken: 'token-w',
            proof: 'TOTP',
            secret: '424242',
            pictures: [{memberId: 5, signatureImage: 'VElN', signatureSource: 'DRAWN'}],
        })
        expect(view.get('[data-testid="witness-done"]').text()).toContain('Tim Probe hat unterschrieben')
        expect(view.find('[data-testid="witness-same-person"]').exists(), 'nothing else waits on Tim').toBe(false)
        expect(view.find('[data-testid="witness-continue"]').exists()).toBe(false)
        getWitnessPeople.mockResolvedValue([listed(GRETA, 0), listed(TIM, 0)])
        await click(view, 'witness-next-person')
        expect(view.find('[data-testid="witness-people"]').exists()).toBe(true)
        expect(view.find('[data-testid="witness-person-6"]').exists(), 'with nothing left open the list shows everybody')
            .toBe(true)
        view.unmount()
    })

    /** The list opens on those something waits on; everybody else is one tab away. */
    it('lists those something waits on first, and everybody on the other tab', async () => {
        const view = await open()

        expect(view.find('[data-testid="witness-person-5"]').exists()).toBe(true)
        expect(view.find('[data-testid="witness-person-6"]').exists()).toBe(false)
        await click(view, 'witness-tab-all')
        expect(view.find('[data-testid="witness-person-6"]').exists()).toBe(true)
        view.unmount()
    })

    /** The search finds people by the documents that wait on them, and says so when it finds nobody. */
    it('finds people by a waiting document and says when the search finds nobody', async () => {
        const view = await open()
        await click(view, 'witness-tab-all')
        const search = view.get('[data-testid="witness-person-search"] input')

        await search.setValue('Fotoerlaubnis')
        expect(view.find('[data-testid="witness-person-5"]').exists()).toBe(true)
        expect(view.find('[data-testid="witness-person-6"]').exists()).toBe(false)
        await search.setValue('niemand hier')
        expect(view.get('[data-testid="witness-people"]').text()).toContain('Niemand passt zur Suche.')
        view.unmount()
    })

    /** Where nothing waits on anybody, the list opens on everybody, since a document can still be made. */
    it('opens on everybody where nothing waits on anyone', async () => {
        getWitnessPeople.mockResolvedValue([listed(GRETA, 0), listed(TIM, 0)])
        const view = await open()

        expect(view.find('[data-testid="witness-person-6"]').exists()).toBe(true)
        expect(view.find('[data-testid="witness-person-5"]').exists()).toBe(true)
        view.unmount()
    })

    /** After the child signs, the guardian's field on the same document comes next, one press away. */
    it('goes on with the guardian once the child has signed the same document', async () => {
        const view = await open()
        await click(view, 'witness-person-5')
        await click(view, 'witness-field-9')
        await signAsThePerson(view)
        getWitnessOffers.mockResolvedValue({
            fields: [field(10, {fieldName: 'anyGuardian', role: 'ANY_GUARDIAN', signers: [GRETA]})],
            templates: [],
        })
        await view.get('[data-testid="signing-proof"] input').setValue('424242')
        await view.get('[data-testid="signing-proof"] form').trigger('submit')
        await flushPromises()

        expect(view.get('[data-testid="witness-following"]').text()).toContain('Greta Probe')
        await click(view, 'witness-continue')
        expect(handedOver('signing-progress').text()).toContain('Schritt')
        expect(view.text()).toContain('Greta Probe')
        view.unmount()
    })

    it('asks who is there for a field any guardian may sign', async () => {
        getWitnessOffers.mockResolvedValue({
            fields: [field(10, {role: 'ANY_GUARDIAN', fieldName: 'anyGuardian', signers: [GRETA, OLAF]})],
            templates: [],
        })
        const view = await open()

        await click(view, 'witness-person-5')
        await click(view, 'witness-field-10')
        await click(view, 'witness-signer-7')
        await signAsThePerson(view)

        expect(startWitnessedSigning).toHaveBeenCalledWith(10, 7, [])
        view.unmount()
    })

    it('gives the device back without signing and starts nothing', async () => {
        const view = await open()

        await click(view, 'witness-person-5')
        await click(view, 'witness-field-9')
        await clickHandedOver('witness-handed-over')
        await clickHandedOver('witness-give-back')
        expect(handedOver('witness-taken-back').exists()).toBe(true)
        await clickHandedOver('witness-taken-back')

        expect(view.find('[data-testid="witness-open-fields"]').exists()).toBe(true)
        expect(startWitnessedSigning).not.toHaveBeenCalled()
        view.unmount()
    })

    it('opens a field from its tile straight at the hand over', async () => {
        route.query = {field: '9'}
        getWitnessField.mockResolvedValue(field(9))
        const view = await open()

        expect(getWitnessField).toHaveBeenCalledWith(9)
        expect(handedOver('witness-handed-over').text()).toBe('Ich bin Tim Probe')
        view.unmount()
    })

    it('makes a document from a template and leads to its one field', async () => {
        generateForWitness.mockResolvedValue([field(12)])
        const view = await open()

        await click(view, 'witness-person-5')
        await click(view, 'witness-template-4')

        expect(generateForWitness).toHaveBeenCalledWith(5, 4)
        expect(handedOver('witness-handed-over').exists()).toBe(true)
        view.unmount()
    })
})
