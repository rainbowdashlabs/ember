/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {TwoFactorStatus, TotpBeginResponse} from '@/api/twoFactor'
import {getTwoFactorStatus, beginTotpSetup, confirmTotpSetup, removeTotp, regenerateBackupCodes} from '@/api/twoFactor'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Modal from '@/components/feedback/Modal.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useConfigPanel} from '@/composables/useConfigPanel'
import WebAuthnSection from './twofactorsection/WebAuthnSection.vue'
import TrustedDevicesSection from './twofactorsection/TrustedDevicesSection.vue'
import {apiErrorMessage, apiErrorStatus} from '@/util/apiError'

const {t} = useI18n()

const {config: status, loading, error, reload: loadStatus} = useConfigPanel<TwoFactorStatus | null>({
  initial: null,
  fetch: async () => {
    try { return await getTwoFactorStatus() } catch { return null }
  },
})

const setupStep = ref<'idle' | 'qr' | 'backup-display'>('idle')
const setupData = ref<TotpBeginResponse | null>(null)
const confirmCode = ref('')
const confirmPassword = ref('')
const confirmLoading = ref(false)
const confirmError = ref('')

const showRemoveModal = ref(false)
const removeLoading = ref(false)

const showRegenerateModal = ref(false)
const regeneratedCodes = ref<string[]>([])

const recoveryCodesAfterWebAuthn = ref<string[]>([])
const showWebAuthnRecoveryCodes = ref(false)

async function handleWebAuthnUpdated(codes: string[]) {
  if (codes && codes.length > 0) {
    recoveryCodesAfterWebAuthn.value = codes
    showWebAuthnRecoveryCodes.value = true
  }
  await loadStatus()
}

async function startSetup() {
  error.value = ''
  try {
    setupData.value = await beginTotpSetup()
    setupStep.value = 'qr'
  } catch (e) {
    error.value = apiErrorMessage(e) || t('common.error')
  }
}

const requiresPassword = () => !status.value?.enrolled

async function confirmSetup() {
  if (!setupData.value || !confirmCode.value) return
  if (requiresPassword() && !confirmPassword.value) {
    confirmError.value = t('twoFactor.setup.passwordRequired')
    return
  }
  confirmError.value = ''
  confirmLoading.value = true
  try {
    await confirmTotpSetup(
      setupData.value.secret,
      confirmCode.value,
      setupData.value.recoveryCodes,
      requiresPassword() ? confirmPassword.value : undefined,
    )
    setupStep.value = 'backup-display'
  } catch (e) {
    confirmError.value = apiErrorStatus(e) === 401
      ? t('twoFactor.setup.passwordWrong')
      : t('twoFactor.setup.invalidCode')
  } finally {
    confirmLoading.value = false
  }
}

function finishSetup() {
  setupStep.value = 'idle'
  setupData.value = null
  confirmCode.value = ''
  confirmPassword.value = ''
  loadStatus()
}

async function handleRemove() {
  removeLoading.value = true
  try {
    await removeTotp()
    showRemoveModal.value = false
    await loadStatus()
  } catch { /* ignore */ }
  removeLoading.value = false
}

async function handleRegenerate() {
  try {
    const result = await regenerateBackupCodes()
    regeneratedCodes.value = result.codes
    showRegenerateModal.value = true
    await loadStatus()
  } catch { /* ignore */ }
}
</script>

<template>
  <div class="space-y-6">
    <SubHeader>{{ t('twoFactor.title') }}</SubHeader>

    <Spinner v-if="loading" size="md"/>

    <template v-if="!loading && status">
      <div class="flex items-center gap-2 mb-4">
        <SuccessBadge v-if="status.enrolled">{{ t('twoFactor.active') }}</SuccessBadge>
        <ErrorBadge v-else>{{ t('twoFactor.inactive') }}</ErrorBadge>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <!-- Not enrolled: show setup -->
      <template v-if="!status.enrolled && setupStep === 'idle'">
        <MutedText tag="p" size="sm">{{ t('twoFactor.setup.description') }}</MutedText>
        <div class="flex flex-wrap gap-2">
          <PrimaryButton @click="startSetup">
            <font-awesome-icon :icon="['fas', 'shield']" class="mr-1"/>
            {{ t('twoFactor.setup.begin') }}
          </PrimaryButton>
        </div>
        <WebAuthnSection :factors="status.factors" :available="status.webauthnAvailable" @updated="handleWebAuthnUpdated"/>
      </template>

      <!-- QR code step -->
      <template v-if="setupStep === 'qr' && setupData">
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('twoFactor.setup.scanQr') }}</SubHeader>
          <div class="flex justify-center">
            <img :src="'data:image/png;base64,' + setupData.qrPng" alt="QR Code" class="w-48 h-48"/>
          </div>
          <MutedText tag="p" size="sm" class="text-center break-all">{{ setupData.secret }}</MutedText>

          <form class="space-y-3" @submit.prevent="confirmSetup">
            <TextInput v-model="confirmCode" :placeholder="t('twoFactor.setup.codePlaceholder')" autocomplete="one-time-code" inputmode="numeric"/>
            <template v-if="requiresPassword()">
              <MutedText tag="p" size="sm">{{ t('twoFactor.setup.passwordPrompt') }}</MutedText>
              <PasswordInput v-model="confirmPassword" :placeholder="t('twoFactor.setup.passwordPlaceholder')"/>
            </template>
            <Alert v-if="confirmError" variant="error">{{ confirmError }}</Alert>
            <PrimaryButton :disabled="confirmLoading || !confirmCode || (requiresPassword() && !confirmPassword)" class="w-full" @click="confirmSetup">
              {{ confirmLoading ? t('common.loading') : t('twoFactor.setup.verify') }}
            </PrimaryButton>
          </form>
        </NeutralContainer>
      </template>

      <!-- Backup codes display -->
      <template v-if="setupStep === 'backup-display' && setupData">
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('twoFactor.backup.title') }}</SubHeader>
          <Alert variant="info">{{ t('twoFactor.backup.saveWarning') }}</Alert>
          <div class="grid grid-cols-2 gap-2">
            <code v-for="code in setupData.recoveryCodes" :key="code" class="text-sm bg-(--bg) px-3 py-1.5 rounded text-center font-mono">{{ code }}</code>
          </div>
          <PrimaryButton class="w-full" @click="finishSetup">{{ t('twoFactor.backup.done') }}</PrimaryButton>
        </NeutralContainer>
      </template>

      <!-- Enrolled: show management -->
      <template v-if="status.enrolled && setupStep === 'idle'">
        <NeutralContainer v-if="status.factors.some(f => f.kind === 'TOTP')" class="space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', 'mobile-screen']" class="text-(--text-muted)"/>
              <span class="font-medium">{{ t('twoFactor.authenticator') }}</span>
            </div>
            <ErrorButton compact @click="showRemoveModal = true">{{ t('common.remove') }}</ErrorButton>
          </div>
        </NeutralContainer>
        <NeutralContainer v-else class="space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', 'mobile-screen']" class="text-(--text-muted)"/>
              <span class="font-medium">{{ t('twoFactor.authenticator') }}</span>
            </div>
            <PrimaryButton compact @click="startSetup">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
              {{ t('twoFactor.setup.begin') }}
            </PrimaryButton>
          </div>
        </NeutralContainer>

        <WebAuthnSection :factors="status.factors" :available="status.webauthnAvailable" @updated="handleWebAuthnUpdated"/>

        <NeutralContainer class="space-y-3">
          <div class="flex items-center justify-between">
            <div>
              <span class="font-medium">{{ t('twoFactor.backup.label') }}</span>
              <MutedText tag="span" size="sm" class="ml-2">{{ t('twoFactor.backup.remaining', {count: status.unusedBackupCodes}) }}</MutedText>
            </div>
            <SecondaryButton compact @click="handleRegenerate">{{ t('twoFactor.backup.regenerate') }}</SecondaryButton>
          </div>
        </NeutralContainer>

        <TrustedDevicesSection/>
      </template>
    </template>

    <!-- Remove TOTP modal -->
    <Modal v-model="showRemoveModal">
      <div class="space-y-4 p-4">
        <SubHeader>{{ t('twoFactor.remove.title') }}</SubHeader>
        <Alert variant="error">{{ t('twoFactor.remove.warning') }}</Alert>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="showRemoveModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton :disabled="removeLoading" @click="handleRemove">{{ t('twoFactor.remove.confirm') }}</ErrorButton>
        </div>
      </div>
    </Modal>

    <!-- Regenerated backup codes modal -->
    <Modal v-model="showRegenerateModal">
      <div class="space-y-4 p-4">
        <SubHeader>{{ t('twoFactor.backup.title') }}</SubHeader>
        <Alert variant="info">{{ t('twoFactor.backup.saveWarning') }}</Alert>
        <div class="grid grid-cols-2 gap-2">
          <code v-for="code in regeneratedCodes" :key="code" class="text-sm bg-(--bg) px-3 py-1.5 rounded text-center font-mono">{{ code }}</code>
        </div>
        <PrimaryButton class="w-full" @click="showRegenerateModal = false">{{ t('twoFactor.backup.done') }}</PrimaryButton>
      </div>
    </Modal>

    <!-- Backup codes issued automatically after first WebAuthn enrollment -->
    <Modal v-model="showWebAuthnRecoveryCodes">
      <div class="space-y-4 p-4">
        <SubHeader>{{ t('twoFactor.backup.title') }}</SubHeader>
        <Alert variant="info">{{ t('twoFactor.backup.saveWarning') }}</Alert>
        <div class="grid grid-cols-2 gap-2">
          <code v-for="code in recoveryCodesAfterWebAuthn" :key="code" class="text-sm bg-(--bg) px-3 py-1.5 rounded text-center font-mono">{{ code }}</code>
        </div>
        <PrimaryButton class="w-full" @click="showWebAuthnRecoveryCodes = false">{{ t('twoFactor.backup.done') }}</PrimaryButton>
      </div>
    </Modal>
  </div>
</template>
