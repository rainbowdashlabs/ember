/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {passkeys} from '@/api'
import {createWebAuthnCredential, getWebAuthnCredential, webauthnErrorKey} from '@/util/webauthn'

export type PasskeyCreationPhase = 'idle' | 'creating' | 'trial' | 'trialRunning' | 'done' | 'trialSkipped'

/**
 * The creation flow with the test drive straight after, shared by the offer screen and the
 * security section: create the credential, then run the sign-in ceremony against the session
 * that is already open, so a passkey is proven within seconds of being made instead of weeks
 * later or never. A cancelled trial costs nothing; the member never stopped being signed in.
 */
export function usePasskeyCreation() {
    const {t} = useI18n()
    const phase = ref<PasskeyCreationPhase>('idle')
    const error = ref('')

    /** Starts the ceremony. A cancelled OS sheet returns to idle with the gentle sentence. */
    async function start(label: string): Promise<boolean> {
        error.value = ''
        phase.value = 'creating'
        try {
            const begin = await passkeys.passkeyCreateBegin()
            const credentialJson = await createWebAuthnCredential(begin.optionsJson)
            await passkeys.passkeyCreateFinish(begin.challengeToken, credentialJson, label)
            phase.value = 'trial'
            return true
        } catch (e) {
            error.value = t(webauthnErrorKey(e, 'create'))
            phase.value = 'idle'
            return false
        }
    }

    async function runTrial() {
        error.value = ''
        phase.value = 'trialRunning'
        try {
            const begin = await passkeys.trialBegin()
            const credentialJson = await getWebAuthnCredential(begin.optionsJson)
            const outcome = await passkeys.trialFinish(begin.challengeToken, credentialJson)
            if (outcome === 'OK') {
                phase.value = 'done'
            } else if (outcome === 'FOREIGN_CREDENTIAL') {
                error.value = t('passkeys.errors.foreignCredential')
                phase.value = 'trial'
            } else {
                phase.value = 'trialSkipped'
            }
        } catch {
            phase.value = 'trialSkipped'
        }
    }

    function skipTrial() {
        phase.value = 'trialSkipped'
    }

    function reset() {
        phase.value = 'idle'
        error.value = ''
    }

    return {phase: readonly(phase), error: readonly(error), start, runTrial, skipTrial, reset}
}
