/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {SigningStartResponse} from '@/api/generated/schema'
import {useSigningAct} from './useSigningAct'

const startSigning = vi.hoisted(() => vi.fn())
const completeSigning = vi.hoisted(() => vi.fn())
const getWebAuthnCredential = vi.hoisted(() => vi.fn())

vi.mock('@/api/signing', () => ({startSigning, completeSigning}))
vi.mock('@/util/webauthn', () => ({getWebAuthnCredential}))

function started(token: string, overrides: Partial<SigningStartResponse> = {}): SigningStartResponse {
    return {
        startToken: token,
        expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
        fieldId: 4,
        requestUid: '11111111-1111-1111-1111-111111111111',
        fieldName: 'participant',
        role: 'PARTICIPANT',
        capacity: 'ACCOUNT_HOLDER',
        statement: 'Ich stimme zu.',
        signerName: 'Alex Muster',
        accountHolderName: 'Alex Muster',
        memberName: null,
        documentMemberName: 'Alex Muster',
        contentSha256: 'cd'.repeat(32),
        acceptedProofs: ['PASSKEY', 'SECURITY_KEY'],
        webAuthnOptionsJson: '{"publicKey":{"challenge":"AA"}}',
        ...overrides,
    }
}

/**
 * The act on one field: a start is reused only while it is unspent and fresh, one prompt serves a
 * passkey and a security key, and a prompt closed without an answer spends nothing.
 */
describe('useSigningAct', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        completeSigning.mockResolvedValue({state: 'SIGNED'})
        getWebAuthnCredential.mockResolvedValue('{"id":"cred"}')
    })

    it('confirms with the authenticator against the start that offered it, as a passkey where one is taken', async () => {
        startSigning.mockResolvedValue(started('first'))
        const act = useSigningAct(() => 4)
        await act.prepare()

        await act.confirmWithAuthenticator()

        expect(startSigning).toHaveBeenCalledTimes(1)
        expect(getWebAuthnCredential).toHaveBeenCalledWith('{"publicKey":{"challenge":"AA"}}')
        expect(completeSigning).toHaveBeenCalledWith(4, {startToken: 'first', proof: 'PASSKEY', credentialJson: '{"id":"cred"}'})
        expect(act.outcome.value).toEqual({state: 'SIGNED'})
    })

    it('names a security key where no passkey is taken', async () => {
        startSigning.mockResolvedValue(started('first', {acceptedProofs: ['SECURITY_KEY']}))
        const act = useSigningAct(() => 4)
        await act.prepare()

        await act.confirmWithAuthenticator()

        expect(completeSigning.mock.calls[0]?.[1].proof).toBe('SECURITY_KEY')
    })

    it('keeps the start when the prompt is closed, and starts afresh once a confirmation spent it', async () => {
        startSigning.mockResolvedValueOnce(started('first')).mockResolvedValueOnce(started('second'))
        getWebAuthnCredential.mockRejectedValueOnce(new Error('webauthn-cancelled'))
        completeSigning.mockRejectedValueOnce(new Error('refused')).mockResolvedValueOnce({state: 'SIGNED'})
        const act = useSigningAct(() => 4)
        await act.prepare()

        await expect(act.confirmWithAuthenticator()).rejects.toThrow('webauthn-cancelled')
        await expect(act.confirmWithSecret('TOTP', '111111')).rejects.toThrow('refused')
        await act.confirmWithSecret('TOTP', '424242')

        expect(startSigning).toHaveBeenCalledTimes(2)
        expect(completeSigning.mock.calls.map(call => call[1].startToken)).toEqual(['first', 'second'])
    })

    it('starts afresh when the start is about to expire', async () => {
        startSigning
            .mockResolvedValueOnce(started('stale', {expiresAt: new Date(Date.now() + 5_000).toISOString()}))
            .mockResolvedValueOnce(started('fresh'))
        const act = useSigningAct(() => 4)
        await act.prepare()

        await act.confirmWithSecret('PASSWORD', 'geheim')

        expect(completeSigning).toHaveBeenCalledWith(4, {startToken: 'fresh', proof: 'PASSWORD', secret: 'geheim'})
    })
})
