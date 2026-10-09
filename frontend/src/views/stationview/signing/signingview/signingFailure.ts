/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describeFailure, FailureKind, type Failure, type Translate} from '@/util/failure'
import {webauthnErrorKey} from '@/util/webauthn'

/** What the WebAuthn helpers throw on their own, before the browser is asked anything. */
const OWN_AUTHENTICATOR_ERRORS: ReadonlySet<string> = new Set(['webauthn-cancelled', 'webauthn-unsupported'])

/**
 * Whether a failure came from the passkey or security key prompt rather than from the server: the
 * browser's own refusals, or the helpers' cancelled and unsupported.
 */
function fromAuthenticator(e: unknown): boolean {
    if (typeof DOMException !== 'undefined' && e instanceof DOMException) return true
    return e instanceof Error && OWN_AUTHENTICATOR_ERRORS.has(e.message)
}

/**
 * A failed confirmation, said in a way the signer can act on.
 *
 * <p>A closed passkey prompt, a browser without passkeys and a mistyped code or password are the
 * signer's to retry, never a fault worth reporting, and nothing was signed in any of them. Everything
 * else is described as any failure is.
 *
 * @param e the thing that was thrown
 * @param t the translator
 */
export function describeSigningFailure(e: unknown, t: Translate): Failure {
    if (fromAuthenticator(e)) {
        return {
            kind: FailureKind.REJECTED,
            message: t(webauthnErrorKey(e, 'get')),
            guidance: t('signing.proof.tryAgain'),
            reportable: false,
        }
    }
    const described = describeFailure(e, t)
    if (described.kind !== FailureKind.REJECTED && described.kind !== FailureKind.DENIED) return described
    return {...described, guidance: t('signing.proof.tryAgain'), reportable: false}
}
