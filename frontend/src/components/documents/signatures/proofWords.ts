/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StepUpProof} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'

/** The words for each proof a signing act takes; backup codes and another device never confirm one. */
const PROOF_WORDS: Readonly<Partial<Record<StepUpProof, string>>> = {
    [StepUpProof.PASSKEY]: 'signing.proofName.passkey',
    [StepUpProof.SECURITY_KEY]: 'signing.proofName.securityKey',
    [StepUpProof.TOTP]: 'signing.proofName.code',
    [StepUpProof.PASSWORD]: 'signing.proofName.password',
}

/**
 * What a signature was confirmed with, as a sentence names it ("bestätigt mit einem Passkey").
 *
 * @param proof the proof
 * @param t     the translator
 */
export function proofWords(proof: StepUpProof, t: Translate): string {
    const key = PROOF_WORDS[proof]
    return key ? t(key) : proof
}
