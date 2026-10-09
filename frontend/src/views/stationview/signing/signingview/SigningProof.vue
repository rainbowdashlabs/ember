/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import DevCodeHint from '@/components/feedback/DevCodeHint.vue'
import SigningSecretForm from './SigningSecretForm.vue'
import {StepUpProof} from '@/api/generated/schema'
import type {BatchStartResponse} from '@/api/generated/schema'
import type {TypedProof} from './useBatchSigning'
import {isWebAuthnSupported} from '@/util/webauthn'

/**
 * The one action that confirms every chosen signature, in the way the reader's account allows, said in
 * plain words: with a passkey or security key, with the code from the authenticator app, or, for an account
 * without a second factor, with the password.
 */
const props = defineProps<{
  offer: BatchStartResponse
  busy: boolean
}>()

defineEmits<{authenticator: []; secret: [proof: TypedProof, secret: string]}>()

const {t} = useI18n()
const webAuthnSupported = isWebAuthnSupported()

const accepts = (proof: StepUpProof) => props.offer.acceptedProofs.includes(proof)

const takesPasskey = computed(() => accepts(StepUpProof.PASSKEY))
const takesKey = computed(() => takesPasskey.value || accepts(StepUpProof.SECURITY_KEY))
const authenticatorLabel = computed(() => takesPasskey.value
    ? t('signing.flow.confirm.withPasskey')
    : t('signing.flow.confirm.withKey'))
</script>

<template>
  <div class="space-y-4" data-testid="signing-proof">
    <PrimaryButton
        v-if="takesKey && webAuthnSupported"
        type="button"
        :icon="['fas', 'fingerprint']"
        :disabled="busy"
        data-testid="signing-authenticator"
        @click="$emit('authenticator')"
    >
      {{ authenticatorLabel }}
    </PrimaryButton>
    <Alert v-if="takesKey && !webAuthnSupported" variant="info">{{ t('signing.proof.authenticatorUnsupported') }}</Alert>
    <template v-if="accepts(StepUpProof.TOTP)">
      <SigningSecretForm
          :label="t('signing.flow.confirm.codeLabel')"
          :submit-label="t('signing.flow.confirm.submit')"
          :disabled="busy"
          @submit="secret => $emit('secret', StepUpProof.TOTP, secret)"
      />
      <DevCodeHint/>
    </template>
    <SigningSecretForm
        v-if="accepts(StepUpProof.PASSWORD)"
        :label="t('signing.flow.confirm.passwordLabel')"
        :submit-label="t('signing.flow.confirm.submit')"
        hidden
        :disabled="busy"
        @submit="secret => $emit('secret', StepUpProof.PASSWORD, secret)"
    />
  </div>
</template>
