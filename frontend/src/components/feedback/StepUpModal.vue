/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {
  passkeyStepUpBegin,
  passkeyStepUpFinish,
  passwordStepUp,
  stepUp,
  webauthnStepUpBegin,
  webauthnStepUpFinish,
} from '@/api/twoFactor'
import {getWebAuthnCredential, isWebAuthnSupported} from '@/util/webauthn'
import {useStepUpPrompt, type StepUpProofName} from '@/util/stepUp'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()
const {current, complete, cancel} = useStepUpPrompt()

const code = ref('')
const password = ref('')
const useBackupCode = ref(false)
const webauthnSupported = isWebAuthnSupported()

/**
 * The refusal names what the account can prove itself with, and the dialog offers exactly that.
 * A refusal without the set (an older backend during a rolling deploy) falls back to the
 * second-factor proofs, which is everything this dialog offered before.
 */
const proofs = computed<StepUpProofName[]>(
    () => current.value?.proofs ?? ['TOTP', 'SECURITY_KEY', 'BACKUP_CODE'],
)
const offersPasskey = computed(() => proofs.value.includes('PASSKEY') && webauthnSupported)
const offersPassword = computed(() => proofs.value.includes('PASSWORD'))
const offersTotp = computed(() => proofs.value.includes('TOTP'))
const offersBackup = computed(() => proofs.value.includes('BACKUP_CODE'))
const offersSecurityKey = computed(() => proofs.value.includes('SECURITY_KEY') && webauthnSupported)
const offersNothing = computed(
    () => !offersPasskey.value && !offersPassword.value && !offersTotp.value && !offersBackup.value && !offersSecurityKey.value,
)

const open = computed({
  get: () => current.value !== null,
  set: (v: boolean) => { if (!v) onCancel() },
})

const categoryHint = computed(() => {
  switch (current.value?.category) {
    case 'ACCOUNT_SECURITY': return t('twoFactor.stepUp.categoryAccountSecurity')
    case 'FEDERATION': return t('twoFactor.stepUp.categoryFederation')
    case 'INSTANCE_CONFIG': return t('twoFactor.stepUp.categoryInstanceConfig')
    case 'ROLE_CHANGE': return t('twoFactor.stepUp.categoryRoleChange')
    default: return t('twoFactor.stepUp.description')
  }
})

const {running: submitting, error: submitError, run: runSubmit, clearError: clearSubmitError} = useAsyncAction(async () => {
  if (!current.value) return
  if (offersPassword.value && !useBackupCode.value) {
    if (!password.value) return
    await passwordStepUp(password.value)
  } else {
    if (!code.value) return
    const factor: string = useBackupCode.value ? 'BACKUP_CODE' : 'TOTP'
    await stepUp(factor, code.value)
  }
  complete()
}, {formatError: () => offersPassword.value && !useBackupCode.value
      ? t('twoFactor.stepUp.wrongPassword')
      : t('twoFactor.stepUp.invalidCode')})

const {running: passkeyRunning, error: passkeyError, run: runPasskey, clearError: clearPasskeyError} = useAsyncAction(async () => {
  const begin = await passkeyStepUpBegin()
  const credentialJson = await getWebAuthnCredential(begin.optionsJson)
  await passkeyStepUpFinish(begin.challengeToken, credentialJson)
  complete()
}, {formatError: (e) => {
  const message = (e as Error | undefined)?.message
  if (message === 'webauthn-cancelled') return t('twoFactor.webauthn.cancelled')
  if (message === 'webauthn-unsupported') return t('twoFactor.webauthn.unsupported')
  return t('twoFactor.stepUp.passkeyFailed')
}})

const {running: webauthnRunning, error: webauthnError, run: runWebAuthn, clearError: clearWebauthnError} = useAsyncAction(async () => {
  const begin = await webauthnStepUpBegin()
  const credentialJson = await getWebAuthnCredential(begin.optionsJson)
  await webauthnStepUpFinish(begin.challengeToken, credentialJson)
  complete()
}, {formatError: (e) => {
  const message = (e as Error | undefined)?.message
  if (message === 'webauthn-cancelled') return t('twoFactor.webauthn.cancelled')
  if (message === 'webauthn-unsupported') return t('twoFactor.webauthn.unsupported')
  return t('twoFactor.stepUp.invalidCode')
}})

const loading = computed(() => submitting.value || passkeyRunning.value || webauthnRunning.value)
const error = computed(() => submitError.value || passkeyError.value || webauthnError.value)
const submitReady = computed(() => offersPassword.value && !useBackupCode.value ? !!password.value : !!code.value)

watch(current, (v) => {
  if (v) {
    code.value = ''
    password.value = ''
    useBackupCode.value = false
    clearSubmitError()
    clearPasskeyError()
    clearWebauthnError()
  }
})

function clearErrors() {
  clearSubmitError()
  clearPasskeyError()
  clearWebauthnError()
}

function onSubmit() {
  clearErrors()
  void runSubmit()
}

function onPasskey() {
  clearErrors()
  void runPasskey()
}

function onWebAuthn() {
  clearErrors()
  void runWebAuthn()
}

function onCancel() {
  cancel()
}
</script>

<template>
  <Modal v-model="open" size="sm" topmost>
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('twoFactor.stepUp.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ categoryHint }}</MutedText>

      <PrimaryButton
          v-if="offersPasskey"
          type="button"
          :disabled="loading"
          class="w-full"
          :icon="['fas', 'fingerprint']"
          @click="onPasskey"
      >
        {{ t('twoFactor.stepUp.usePasskey') }}
      </PrimaryButton>

      <form v-if="offersPassword || offersTotp || offersBackup" class="space-y-3" @submit.prevent="onSubmit">
        <PasswordInput
            v-if="offersPassword && !useBackupCode"
            v-model="password"
            :placeholder="t('twoFactor.stepUp.passwordPlaceholder')"
            :disabled="loading"
            autocomplete="current-password"
        />
        <TextInput
            v-else
            v-model="code"
            :placeholder="useBackupCode ? 'XXXX-XXXX-XXXX' : '000000'"
            :disabled="loading"
            autocomplete="one-time-code"
            inputmode="numeric"
        />
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <div class="flex justify-between gap-2">
          <SecondaryButton type="button" :disabled="loading" @click="onCancel">
            {{ t('twoFactor.stepUp.cancel') }}
          </SecondaryButton>
          <PrimaryButton :disabled="loading || !submitReady" type="submit">
            {{ loading ? t('common.loading') : t('twoFactor.stepUp.submit') }}
          </PrimaryButton>
        </div>
      </form>
      <template v-else>
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <Alert v-if="offersNothing" variant="info">{{ t('twoFactor.stepUp.noProofs') }}</Alert>
        <div class="flex justify-between gap-2">
          <SecondaryButton type="button" :disabled="loading" @click="onCancel">
            {{ t('twoFactor.stepUp.cancel') }}
          </SecondaryButton>
        </div>
      </template>

      <SecondaryButton
          v-if="offersSecurityKey"
          type="button"
          :disabled="loading"
          class="w-full"
          @click="onWebAuthn"
      >
        <font-awesome-icon :icon="['fas', 'key']" class="mr-1"/>
        {{ t('twoFactor.webauthn.useKey') }}
      </SecondaryButton>
      <div v-if="offersBackup && (offersTotp || offersPassword)" class="text-center">
        <LinkButton type="button" @click="useBackupCode = !useBackupCode">
          {{ useBackupCode
            ? (offersPassword ? t('twoFactor.stepUp.usePassword') : t('twoFactor.verify.useAuthenticator'))
            : t('twoFactor.verify.useBackupCode') }}
        </LinkButton>
      </div>
    </div>
  </Modal>
</template>
