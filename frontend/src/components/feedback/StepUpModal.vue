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
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {stepUp, webauthnStepUpBegin, webauthnStepUpFinish} from '@/api/twoFactor'
import {getWebAuthnCredential, isWebAuthnSupported} from '@/util/webauthn'
import {useStepUpPrompt} from '@/composables/useStepUp'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()
const {current, complete, cancel} = useStepUpPrompt()

const code = ref('')
const useBackupCode = ref(false)
const webauthnSupported = isWebAuthnSupported()

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
  if (!code.value || !current.value) return
  const factor: string = useBackupCode.value ? 'BACKUP_CODE' : 'TOTP'
  await stepUp(factor, code.value)
  complete()
}, {formatError: () => t('twoFactor.stepUp.invalidCode')})

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

const loading = computed(() => submitting.value || webauthnRunning.value)
const error = computed(() => submitError.value || webauthnError.value)

watch(current, (v) => {
  if (v) {
    code.value = ''
    useBackupCode.value = false
    clearSubmitError()
    clearWebauthnError()
  }
})

function onSubmit() {
  clearWebauthnError()
  void runSubmit()
}

function onWebAuthn() {
  clearSubmitError()
  void runWebAuthn()
}

function onCancel() {
  cancel()
}
</script>

<template>
  <Modal v-model="open" size="sm">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('twoFactor.stepUp.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ categoryHint }}</MutedText>
      <form class="space-y-3" @submit.prevent="onSubmit">
        <TextInput
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
          <PrimaryButton :disabled="loading || !code" type="submit">
            {{ loading ? t('common.loading') : t('twoFactor.stepUp.submit') }}
          </PrimaryButton>
        </div>
      </form>
      <SecondaryButton v-if="webauthnSupported" type="button" :disabled="loading" class="w-full" @click="onWebAuthn">
        <font-awesome-icon :icon="['fas', 'key']" class="mr-1"/>
        {{ t('twoFactor.webauthn.useKey') }}
      </SecondaryButton>
      <div class="text-center">
        <LinkButton type="button" @click="useBackupCode = !useBackupCode">
          {{ useBackupCode ? t('twoFactor.verify.useAuthenticator') : t('twoFactor.verify.useBackupCode') }}
        </LinkButton>
      </div>
    </div>
  </Modal>
</template>
