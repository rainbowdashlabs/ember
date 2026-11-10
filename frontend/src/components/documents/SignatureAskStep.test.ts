/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {FieldRole, SignerCapacity, StationPermission} from '@/api/generated/schema'
import SignatureAskStep from './SignatureAskStep.vue'

const getSignatureAsk = vi.hoisted(() => vi.fn())
const askInPerson = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())
const held = vi.hoisted(() => new Set<string>())

vi.mock('@/api', () => ({
    signing: {getSignatureAsk, requestSignatures: vi.fn()},
}))
vi.mock('@/api/witnessSigning', () => ({askInPerson}))
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: (permission: string) => held.has(permission)}),
}))
vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRouter: () => ({push}),
}))

const CHILD = {
    fieldName: 'participant', role: FieldRole.PARTICIPANT, signerName: 'Kim Berger',
    capacity: SignerCapacity.ACCOUNT_HOLDER, statement: 'Ich bin einverstanden.', state: null,
}

async function mountStep() {
    const step = await mountSuspended(SignatureAskStep, {props: {generationId: 7}})
    await flushPromises()
    return step
}

/**
 * Right after generating, whoever may collect signatures in person can have the document signed at once:
 * it is asked for without notifying anybody and opens on the witness page at its first field.
 */
describe('SignatureAskStep', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        held.clear()
        getSignatureAsk.mockResolvedValue({request: null, fields: [CHILD]})
    })

    it('opens the first field on the witness page for someone who collects in person', async () => {
        held.add(StationPermission.DOCUMENT_SIGN_OTHERS)
        askInPerson.mockResolvedValue([{fieldId: 31}, {fieldId: 32}])
        const step = await mountStep()

        await step.get('[data-testid="signature-ask-in-person"]').trigger('click')
        await flushPromises()

        expect(askInPerson).toHaveBeenCalledWith(7)
        expect(push).toHaveBeenCalledWith('/station/signing/witness?field=31')
    })

    it('offers nothing in person without the permission', async () => {
        const step = await mountStep()

        expect(step.find('[data-testid="signature-ask-in-person"]').exists()).toBe(false)
    })

    it('offers nothing in person once the signatures were asked for', async () => {
        held.add(StationPermission.DOCUMENT_SIGN_OTHERS)
        getSignatureAsk.mockResolvedValue({
            request: {uid: 'u', state: 'OPEN', retentionMonths: 24, copyAttached: true},
            fields: [{...CHILD, state: 'OPEN'}],
        })
        const step = await mountStep()

        expect(step.find('[data-testid="signature-ask-in-person"]').exists()).toBe(false)
    })
})
