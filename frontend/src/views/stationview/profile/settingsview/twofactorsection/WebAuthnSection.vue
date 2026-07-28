/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {FactorInfo} from '@/api/twoFactor'
import {
  removeFactor,
  renameFactor,
  webauthnRegisterBegin,
  webauthnRegisterFinish,
} from '@/api/twoFactor'
import {createWebAuthnCredential, isWebAuthnSupported} from '@/util/webauthn'
import {formatDate} from '@/util/format'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Modal from '@/components/feedback/Modal.vue'
import {apiErrorMessage, apiErrorStatus, errorMessage} from '@/util/apiError'

const props = defineProps<{
  factors: FactorInfo[]
  available: boolean
}>()

const emit = defineEmits<{
  updated: [recoveryCodes: string[]]
}>()

const {t} = useI18n()

const securityKeys = computed(() => props.factors.filter(f => f.kind === 'WEBAUTHN'))
const supported = computed(() => isWebAuthnSupported())

/**
 * A password re-entry is required only when enrolling the very first factor, mirroring the
 * backend gate that stops a hijacked session from silently planting one.
 */
const requiresPassword = computed(() => !props.factors.some(f => f.kind === 'TOTP' || f.kind === 'WEBAUTHN'))

const showLabelPrompt = ref(false)
const newLabel = ref('')
const enrollPassword = ref('')
const enrolling = ref(false)
const error = ref('')

const renameTarget = ref<FactorInfo | null>(null)
const renameLabel = ref('')

async function startEnrollment() {
  error.value = ''
  if (!supported.value) {
    error.value = t('twoFactor.webauthn.unsupported')
    return
  }
  newLabel.value = ''
  enrollPassword.value = ''
  showLabelPrompt.value = true
}

async function confirmEnrollment() {
  if (!newLabel.value.trim()) return
  if (requiresPassword.value && !enrollPassword.value) {
    error.value = t('twoFactor.setup.passwordRequired')
    return
  }
  enrolling.value = true
  error.value = ''
  try {
    const begin = await webauthnRegisterBegin()
    const credentialJson = await createWebAuthnCredential(begin.optionsJson)
    const result = await webauthnRegisterFinish(
      begin.challengeToken,
      credentialJson,
      newLabel.value.trim(),
      requiresPassword.value ? enrollPassword.value : undefined,
    )
    showLabelPrompt.value = false
    emit('updated', result.recoveryCodes ?? [])
  } catch (e) {
    if (errorMessage(e) === 'webauthn-cancelled') {
      error.value = t('twoFactor.webauthn.cancelled')
    } else if (errorMessage(e) === 'webauthn-unsupported') {
      error.value = t('twoFactor.webauthn.unsupported')
    } else if (apiErrorStatus(e) === 401) {
      error.value = t('twoFactor.setup.passwordWrong')
    } else {
      error.value = apiErrorMessage(e) || t('twoFactor.webauthn.failed')
    }
  } finally {
    enrolling.value = false
  }
}

async function handleRemove(factor: FactorInfo) {
  try {
    await removeFactor(factor.id)
    emit('updated', [])
  } catch (e) {
    error.value = apiErrorMessage(e) || t('common.error')
  }
}

function openRename(factor: FactorInfo) {
  renameTarget.value = factor
  renameLabel.value = factor.label
}

async function confirmRename() {
  if (!renameTarget.value || !renameLabel.value.trim()) return
  try {
    await renameFactor(renameTarget.value.id, renameLabel.value.trim())
    renameTarget.value = null
    emit('updated', [])
  } catch (e) {
    error.value = apiErrorMessage(e) || t('common.error')
  }
}
</script>

<template>
  <NeutralContainer v-if="available" class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'key']" class="text-(--text-muted)"/>
        <span class="font-medium">{{ t('twoFactor.webauthn.title') }}</span>
      </div>
      <PrimaryButton compact :disabled="!supported" @click="startEnrollment">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('twoFactor.webauthn.add') }}
      </PrimaryButton>
    </div>
    <MutedText v-if="!supported" tag="p" size="sm">
      {{ t('twoFactor.webauthn.unsupported') }}
    </MutedText>
    <EmptyState v-else-if="securityKeys.length === 0" :message="t('twoFactor.webauthn.empty')" compact/>
    <ul v-else class="space-y-2">
      <li v-for="key in securityKeys" :key="key.id"
          class="flex items-center justify-between gap-2 rounded border border-(--border) px-3 py-2">
        <div>
          <div class="text-sm font-medium">{{ key.label }}</div>
          <MutedText tag="div" size="sm">
            {{ t('twoFactor.webauthn.added') }}: {{ formatDate(key.createdAt) }}
          </MutedText>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton compact @click="openRename(key)">{{ t('common.rename') }}</SecondaryButton>
          <ErrorButton compact @click="handleRemove(key)">{{ t('common.remove') }}</ErrorButton>
        </div>
      </li>
    </ul>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
  </NeutralContainer>

  <Modal v-model="showLabelPrompt" size="sm">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('twoFactor.webauthn.labelPrompt') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('twoFactor.webauthn.labelHint') }}</MutedText>
      <TextInput v-model="newLabel" :placeholder="t('twoFactor.webauthn.labelPlaceholder')" :disabled="enrolling"/>
      <template v-if="requiresPassword">
        <MutedText tag="p" size="sm">{{ t('twoFactor.setup.passwordPrompt') }}</MutedText>
        <PasswordInput v-model="enrollPassword" :placeholder="t('twoFactor.setup.passwordPlaceholder')" :disabled="enrolling"/>
      </template>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <div class="flex justify-end gap-2">
        <SecondaryButton :disabled="enrolling" @click="showLabelPrompt = false">
          {{ t('common.cancel') }}
        </SecondaryButton>
        <PrimaryButton :disabled="enrolling || !newLabel.trim() || (requiresPassword && !enrollPassword)" @click="confirmEnrollment">
          {{ enrolling ? t('twoFactor.webauthn.waiting') : t('twoFactor.webauthn.continue') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>

  <Modal :model-value="renameTarget !== null" size="sm" @update:model-value="renameTarget = null">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('twoFactor.webauthn.renamePrompt') }}</SubHeader>
      <TextInput v-model="renameLabel" :placeholder="t('twoFactor.webauthn.labelPlaceholder')"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="renameTarget = null">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!renameLabel.trim()" @click="confirmRename">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
