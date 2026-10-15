/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {StepUpProof} from '@/api/generated/schema'
import type {
    BatchCompleteRequest,
    BatchCompleteResponse,
    BatchFieldChoice,
    BatchPicture,
    BatchStartResponse,
} from '@/api/generated/schema'
import {completeBatchSigning, startBatchSigning} from '@/api/signing'
import {getWebAuthnCredential} from '@/util/webauthn'
import {draftBase64, type SignatureDraft} from '@/util/signatureDraft'

/**
 * How long before its expiry a start is no longer trusted to reach the server in time, given that a
 * passkey prompt can sit open for a while before it answers.
 */
const EXPIRY_MARGIN_MS = 30_000

/** The proofs a signer types: the authenticator app's code, or the password of an account without one. */
export type TypedProof = typeof StepUpProof.TOTP | typeof StepUpProof.PASSWORD

/**
 * The account holder's signature picture: one made on the screen for the act, or the saved one.
 *
 * <p>`keep` saves the picture made for the act as the signer's own afterwards.
 */
export interface SigningMarkChoice {
    draft: SignatureDraft | null
    useSaved: boolean
    keep: boolean
}

/** The pictures of one confirmation: the account holder's and those drawn by each member through the account. */
export interface SigningPictures {
    holder: SigningMarkChoice
    members: Readonly<Record<number, SignatureDraft | null>>
}

/**
 * The pictures as the confirmation carries them: the account holder's only where one was made for the act
 * (the server takes the saved one otherwise), and one for each member signing through the account.
 *
 * @param pictures the pictures chosen on the screens
 */
export function picturesOf(pictures: SigningPictures): BatchPicture[] {
    const sent: BatchPicture[] = []
    const own = pictures.holder.draft
    if (own) {
        sent.push({signatureImage: draftBase64(own), signatureSource: own.source, keepSignature: pictures.holder.keep})
    }
    for (const [memberId, draft] of Object.entries(pictures.members)) {
        if (draft) sent.push({memberId: Number(memberId), signatureImage: draftBase64(draft), signatureSource: draft.source})
    }
    return sent
}

/**
 * One signing act over every chosen field of the reader, from the start to the outcome.
 *
 * <p>A start binds the chosen fields and what was typed for them, and is kept on the server for a few
 * minutes, spent by the first confirmation whether it passes or not. So the start that told the screen
 * which proofs the signer may give is reused only while it is unspent, fresh and still for the same
 * fields; anything else leads to a new start, made in the same press that confirms, without the signer
 * seeing it. A passkey prompt the signer closes sends nothing and spends nothing.
 *
 * @param choices the chosen fields with what was typed for each, read at each start
 */
export function useBatchSigning(choices: () => BatchFieldChoice[]) {
    return useSigningAct({
        start: () => startBatchSigning(choices()),
        boundTo: () => JSON.stringify(choices()),
        complete: completeBatchSigning,
    })
}

/** Where an act starts and completes, and what a start is bound to. */
export interface SigningActEnds {
    /** Starts the act on the server. */
    start: () => Promise<BatchStartResponse>
    /** What the next start would bind, so a start made for anything else is not reused. */
    boundTo: () => string
    /** Completes a started act with its confirmation. */
    complete: (confirmation: BatchCompleteRequest) => Promise<BatchCompleteResponse>
}

/**
 * One signing act from the start to the outcome, wherever it starts and completes: the reader's own fields
 * in one go, or a field signed in front of a witness. A start is reused as {@link useBatchSigning} says.
 *
 * @param ends where the act starts and completes
 */
export function useSigningAct(ends: SigningActEnds) {
    const offer = ref<BatchStartResponse | null>(null)
    const offeredFor = ref('')
    const spent = ref(false)
    const outcome = ref<BatchCompleteResponse | null>(null)

    /** Starts the act, which tells which proofs are taken. */
    async function prepare(): Promise<BatchStartResponse> {
        const boundTo = ends.boundTo()
        const started = await ends.start()
        offer.value = started
        offeredFor.value = boundTo
        spent.value = false
        return started
    }

    async function usableStart(): Promise<BatchStartResponse> {
        const current = offer.value
        const fresh = current && Date.parse(current.expiresAt) - Date.now() > EXPIRY_MARGIN_MS
        if (current && fresh && !spent.value && offeredFor.value === ends.boundTo()) return current
        return prepare()
    }

    async function complete(
        start: BatchStartResponse,
        proof: StepUpProof,
        answer: {credentialJson?: string; secret?: string},
        pictures: SigningPictures,
    ) {
        spent.value = true
        outcome.value = await ends.complete({
            startToken: start.startToken,
            proof,
            ...answer,
            pictures: picturesOf(pictures),
        })
    }

    /**
     * Confirms with a passkey or a security key. Which of the two it was is read by the server from the
     * credential the signer picks, so one prompt serves both.
     *
     * @param pictures the signature pictures the act leaves in its fields
     */
    async function confirmWithAuthenticator(pictures: SigningPictures): Promise<void> {
        const start = await usableStart()
        if (!start.webAuthnOptionsJson) throw new Error('webauthn-unsupported')
        const credentialJson = await getWebAuthnCredential(start.webAuthnOptionsJson)
        const proof = start.acceptedProofs.includes(StepUpProof.PASSKEY) ? StepUpProof.PASSKEY : StepUpProof.SECURITY_KEY
        await complete(start, proof, {credentialJson}, pictures)
    }

    /**
     * Confirms with the authenticator app's code or the password.
     *
     * @param proof    which of the two
     * @param secret   what the signer typed, sent once and never kept
     * @param pictures the signature pictures the act leaves in its fields
     */
    async function confirmWithSecret(proof: TypedProof, secret: string, pictures: SigningPictures): Promise<void> {
        const start = await usableStart()
        await complete(start, proof, {secret}, pictures)
    }

    /** Forgets the start and the outcome, for a next act on the same screen. */
    function reset() {
        offer.value = null
        offeredFor.value = ''
        spent.value = false
        outcome.value = null
    }

    return {
        offer: computed(() => offer.value),
        outcome: computed(() => outcome.value),
        prepare,
        confirmWithAuthenticator,
        confirmWithSecret,
        reset,
    }
}
