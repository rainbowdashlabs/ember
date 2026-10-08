/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import DevCodeHint from '@/components/feedback/DevCodeHint.vue'
import SigningSecretForm from './SigningSecretForm.vue'
import SigningBindingDetails from './SigningBindingDetails.vue'
import {StepUpProof} from '@/api/generated/schema'
import type {SigningStartResponse} from '@/api/generated/schema'
import type {TypedProof} from './useSigningAct'
import type {Failure} from '@/util/failure'
import {isWebAuthnSupported} from '@/util/webauthn'

/**
 * The proof that confirms the signature, offered in the ways the signer's account allows: a passkey or
 * security key, the authenticator app's code, or, for an account without a second factor, the password.
 *
 * <p>What the act leaves in its field, the signature picture, is chosen in the slot under the heading, before
 * the proof that confirms it.
 *
 * <p>The heading takes the focus when the section appears, so a keyboard or screen reader user lands on
 * the step that now waits for them instead of being left on the button that is gone.
 */
const props = defineProps<{
  offer: SigningStartResponse
  busy: boolean
  failure: Failure | null
}>()

defineEmits<{authenticator: []; secret: [proof: TypedProof, secret: string]}>()

const {t} = useI18n()
const headingId = useId()
const webAuthnSupported = isWebAuthnSupported()

const accepts = (proof: StepUpProof) => props.offer.acceptedProofs.includes(proof)

const takesPasskey = computed(() => accepts(StepUpProof.PASSKEY))
const takesSecurityKey = computed(() => accepts(StepUpProof.SECURITY_KEY))
const takesAuthenticator = computed(() => (takesPasskey.value || takesSecurityKey.value) && webAuthnSupported)
const authenticatorUnsupported = computed(() => (takesPasskey.value || takesSecurityKey.value) && !webAuthnSupported)

const authenticatorLabel = computed(() => {
  if (takesPasskey.value && takesSecurityKey.value) return t('signing.proof.withPasskeyOrKey')
  return takesPasskey.value ? t('signing.proof.withPasskey') : t('signing.proof.withKey')
})

/** Puts the focus on the step's heading, for the moment the step appears. */
function focusHeading() {
  document.getElementById(headingId)?.focus()
}

defineExpose({focusHeading})
</script>

<template>
  <section :aria-labelledby="headingId" class="space-y-4" data-testid="signing-proof">
    <SectionHeader :id="headingId" tabindex="-1" class="focus:outline-none">
      {{ t('signing.proof.heading') }}
    </SectionHeader>
    <MutedText tag="p" size="sm">{{ t('signing.proof.signerLine', {signer: offer.signerName, holder: offer.accountHolderName}) }}</MutedText>
    <slot/>

    <div v-if="takesAuthenticator" class="space-y-2">
      <PrimaryButton
          type="button"
          :icon="['fas', 'fingerprint']"
          :disabled="busy"
          data-testid="signing-authenticator"
          @click="$emit('authenticator')"
      >
        {{ authenticatorLabel }}
      </PrimaryButton>
      <MutedText tag="p" size="sm">{{ t('signing.proof.boundHint') }}</MutedText>
    </div>
    <Alert v-if="authenticatorUnsupported" variant="info">{{ t('signing.proof.authenticatorUnsupported') }}</Alert>

    <template v-if="accepts(StepUpProof.TOTP)">
      <SigningSecretForm
          :label="t('signing.proof.codeLabel')"
          :submit-label="t('signing.proof.withCode')"
          :disabled="busy"
          @submit="secret => $emit('secret', StepUpProof.TOTP, secret)"
      />
      <DevCodeHint/>
    </template>
    <SigningSecretForm
        v-if="accepts(StepUpProof.PASSWORD)"
        :label="t('signing.proof.passwordLabel')"
        :submit-label="t('signing.proof.withPassword')"
        hidden
        :disabled="busy"
        @submit="secret => $emit('secret', StepUpProof.PASSWORD, secret)"
    />

    <FailureAlert :failure="failure"/>
    <SigningBindingDetails :offer="offer"/>
  </section>
</template>
