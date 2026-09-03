/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {shallowRef} from 'vue'

export type StepUpCategory =
    | 'ACCOUNT_SECURITY'
    | 'FEDERATION'
    | 'INSTANCE_CONFIG'
    | 'ROLE_CHANGE'

export const StepUpProof = {
    TOTP: 'TOTP',
    SECURITY_KEY: 'SECURITY_KEY',
    BACKUP_CODE: 'BACKUP_CODE',
    PASSKEY: 'PASSKEY',
    PASSWORD: 'PASSWORD',
} as const

export type StepUpProofName = (typeof StepUpProof)[keyof typeof StepUpProof]

/** Thrown into the original caller when the reader dismissed the prompt instead of answering it. */
export class StepUpCancelledError extends Error {
    constructor() {
        super('Step-up cancelled')
        this.name = 'StepUpCancelledError'
    }
}

interface Waiter {
    resolve: () => void
    reject: (reason: unknown) => void
}

/**
 * What the modal renders. Only the category and the proof set live in the ref: the promise
 * plumbing is plain state, because handing callbacks to a reactive proxy and then mutating them
 * through it is a trap nobody reading the modal would expect.
 */
const current = shallowRef<{category: StepUpCategory | null, proofs: StepUpProofName[] | null} | null>(null)
let waiters: Waiter[] = []

/**
 * Opens the step-up prompt for the given category and resolves once the reader has answered it
 * with one of the proofs the refusal named. Rejects with {@link StepUpCancelledError} when they
 * dismiss it. A null category still prompts, with the generic wording: the challenge is the same
 * one either way. A null proof set falls back to the second-factor proofs, which is what every
 * refusal offered before the server started naming them.
 *
 * Calls made while a prompt is already in flight share its outcome - this prevents a burst of
 * parallel ACCOUNT_SECURITY requests from stacking multiple prompts on top of each other. The
 * category of the prompt already showing wins, because that is the one being answered.
 */
export function requestStepUp(category: StepUpCategory | null, proofs: StepUpProofName[] | null = null): Promise<void> {
    if (!current.value) {
        current.value = {category, proofs}
    }
    return new Promise<void>((resolve, reject) => {
        waiters.push({resolve, reject})
    })
}

/** Whether a prompt is on screen right now. */
export function stepUpPending(): boolean {
    return current.value !== null
}

/**
 * UI-side hook for the step-up prompt. Returns the prompt being shown and helpers to mark the
 * challenge complete or cancelled.
 */
export function useStepUpPrompt() {
    function settle(outcome: (waiter: Waiter) => void) {
        const pending = waiters
        waiters = []
        current.value = null
        pending.forEach(outcome)
    }

    return {
        current,
        complete: () => settle((waiter) => waiter.resolve()),
        cancel: () => settle((waiter) => waiter.reject(new StepUpCancelledError())),
    }
}
