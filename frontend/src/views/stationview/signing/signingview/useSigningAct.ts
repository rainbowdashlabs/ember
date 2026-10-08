/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {StepUpProof} from '@/api/generated/schema'
import type {SigningCompleteResponse, SigningStartResponse} from '@/api/generated/schema'
import {completeSigning, startSigning} from '@/api/signing'
import {getWebAuthnCredential} from '@/util/webauthn'

/**
 * How long before its expiry a start is no longer trusted to reach the server in time, given that a
 * passkey prompt can sit open for a while before it answers.
 */
const EXPIRY_MARGIN_MS = 30_000

/** The proofs a signer types: the authenticator app's code, or the password of an account without one. */
export type TypedProof = typeof StepUpProof.TOTP | typeof StepUpProof.PASSWORD

/**
 * The signing act on one field, from the start to the outcome.
 *
 * <p>A start is kept on the server for a few minutes and spent by the first confirmation, whether the
 * confirmation passes or not. So the start that told the screen which proofs the signer may give is
 * reused only while it is unspent and fresh; a wrong code, a refused answer or a long pause leads to a
 * new start, made in the same press that confirms, without the signer seeing it. A passkey prompt the
 * signer closes sends nothing and spends nothing.
 *
 * @param fieldId the field, read at each call so a changed address is followed
 */
export function useSigningAct(fieldId: () => number) {
    const offer = ref<SigningStartResponse | null>(null)
    const spent = ref(false)
    const outcome = ref<SigningCompleteResponse | null>(null)

    /** Starts the act, which tells who signs, what is bound and which proofs are taken. */
    async function prepare(): Promise<SigningStartResponse> {
        const started = await startSigning(fieldId())
        offer.value = started
        spent.value = false
        return started
    }

    async function usableStart(): Promise<SigningStartResponse> {
        const current = offer.value
        if (current && !spent.value && Date.parse(current.expiresAt) - Date.now() > EXPIRY_MARGIN_MS) return current
        return prepare()
    }

    async function complete(start: SigningStartResponse, proof: StepUpProof, answer: {credentialJson?: string; secret?: string}) {
        spent.value = true
        outcome.value = await completeSigning(fieldId(), {startToken: start.startToken, proof, ...answer})
    }

    /**
     * Confirms with a passkey or a security key. Which of the two it was is read by the server from the
     * credential the signer picks, so one prompt serves both.
     */
    async function confirmWithAuthenticator(): Promise<void> {
        const start = await usableStart()
        if (!start.webAuthnOptionsJson) throw new Error('webauthn-unsupported')
        const credentialJson = await getWebAuthnCredential(start.webAuthnOptionsJson)
        const proof = start.acceptedProofs.includes(StepUpProof.PASSKEY) ? StepUpProof.PASSKEY : StepUpProof.SECURITY_KEY
        await complete(start, proof, {credentialJson})
    }

    /**
     * Confirms with the authenticator app's code or the password.
     *
     * @param proof  which of the two
     * @param secret what the signer typed, sent once and never kept
     */
    async function confirmWithSecret(proof: TypedProof, secret: string): Promise<void> {
        const start = await usableStart()
        await complete(start, proof, {secret})
    }

    return {
        offer: computed(() => offer.value),
        outcome: computed(() => outcome.value),
        prepare,
        confirmWithAuthenticator,
        confirmWithSecret,
    }
}
