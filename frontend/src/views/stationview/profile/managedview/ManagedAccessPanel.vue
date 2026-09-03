/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PasskeyCodeDisplay from '@/components/passkey/PasskeyCodeDisplay.vue'
import {managedMembers, passkeys} from '@/api'
import type {ManagedAccess, PasskeyCode} from '@/api/managedMembers'
import type {PasskeyModeName} from '@/api/adminSettings'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The access a guardian manages for one member in their care: the address the account is reached
 * at, and whether it may sign in at all. Signing in stays off until there is an address to send
 * the invitation to.
 */
const props = defineProps<{
  memberId: number
}>()

const {t} = useI18n()

const access = ref<ManagedAccess | null>(null)
const email = ref('')
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    access.value = await managedMembers.getAccess(props.memberId)
    email.value = access.value.email ?? ''
    username.value = access.value.username ?? ''
    password.value = ''
  } catch (e) {
    access.value = null
    error.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveEmail() {
  error.value = ''
  notice.value = ''
  try {
    access.value = await managedMembers.setEmail(props.memberId, email.value)
    email.value = access.value.email ?? ''
    notice.value = t('profileManaged.access.emailSaved')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
    throw e
  }
}

async function saveUsername() {
  error.value = ''
  notice.value = ''
  try {
    access.value = await managedMembers.setUsername(props.memberId, username.value)
    username.value = access.value.username ?? ''
    notice.value = t('profileManaged.access.usernameSaved')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
    throw e
  }
}

async function savePassword() {
  error.value = ''
  notice.value = ''
  try {
    access.value = await managedMembers.setPassword(props.memberId, password.value)
    password.value = ''
    notice.value = t('profileManaged.access.passwordSaved')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
    throw e
  }
}

const passkeyMode = ref<PasskeyModeName>('OFF')
const passkeyCode = ref<PasskeyCode | null>(null)
const passwordless = computed(() => passkeyMode.value === 'PASSWORDLESS')

onMounted(() => {
  passkeys.publicPasskeyMode().then(mode => passkeyMode.value = mode).catch(() => {})
})

async function issueCode() {
  error.value = ''
  try {
    passkeyCode.value = await managedMembers.issuePasskeyCode(props.memberId)
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  }
}

/** The abandoned attempt must not leave a photographed code alive for the rest of its window. */
async function revokeCode() {
  if (!passkeyCode.value) return
  passkeyCode.value = null
  try {
    await managedMembers.revokePasskeyCode(props.memberId)
  } catch {
    // The code still dies with its five minutes.
  }
}

async function toggleLogin(enabled: boolean) {
  error.value = ''
  notice.value = ''
  try {
    access.value = await managedMembers.setLogin(props.memberId, enabled)
    notice.value = enabled ? t('profileManaged.access.loginOn') : t('profileManaged.access.loginOff')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
    await load()
  }
}

watch(() => props.memberId, load, {immediate: true})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profileManaged.access.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('profileManaged.access.hint') }}</MutedText>

    <Spinner v-if="loading" size="md"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="notice" variant="success">{{ notice }}</Alert>

    <template v-if="access && !loading">
      <div class="space-y-1">
        <FieldLabel>{{ t('profileManaged.access.email') }}</FieldLabel>
        <TextInput v-model="email" type="email" :placeholder="t('profileManaged.access.emailPlaceholder')"/>
        <MutedText tag="p" size="sm">{{ t('profileManaged.access.emailHint') }}</MutedText>
        <SaveButton :action="saveEmail"/>
      </div>

      <div class="space-y-1 border-t border-(--border) pt-4">
        <FieldLabel>{{ t('profileManaged.access.username') }}</FieldLabel>
        <TextInput v-model="username" data-onboarding="managed.access.username"
                   :placeholder="t('profileManaged.access.usernamePlaceholder')"/>
        <MutedText tag="p" size="sm">{{ t('profileManaged.access.usernameHint') }}</MutedText>
        <SaveButton data-onboarding="managed.access.username-save" :action="saveUsername"/>
      </div>

      <div class="flex items-start justify-between gap-4 border-t border-(--border) pt-4">
        <div>
          <FieldLabel>{{ t('profileManaged.access.login') }}</FieldLabel>
          <MutedText tag="p" size="sm">
            {{ access.canSignIn ? t('profileManaged.access.loginHint') : t('profileManaged.access.loginNeedsEmail') }}
          </MutedText>
        </div>
        <ToggleInput data-onboarding="managed.access.login-toggle" :model-value="access.loginEnabled"
                     :disabled="!access.canSignIn"
                     :aria-label="t('profileManaged.access.login')"
                     @update:model-value="toggleLogin"/>
      </div>

      <div v-if="!access.email && !passwordless" class="space-y-1 border-t border-(--border) pt-4">
        <FieldLabel>{{ t('profileManaged.access.password') }}</FieldLabel>
        <TextInput v-model="password" type="password" data-onboarding="managed.access.password"
                   :placeholder="t('profileManaged.access.passwordPlaceholder')"/>
        <MutedText tag="p" size="sm">{{ t('profileManaged.access.passwordHint') }}</MutedText>
        <SaveButton data-onboarding="managed.access.password-save" :action="savePassword" :disabled="!password"/>
      </div>

      <div v-if="!access.email && passkeyMode !== 'OFF'" class="space-y-2 border-t border-(--border) pt-4">
        <FieldLabel>{{ t('passkeys.code.title') }}</FieldLabel>
        <MutedText tag="p" size="sm">{{ t('passkeys.code.guardianHint') }}</MutedText>
        <SecondaryButton v-if="!passkeyCode" type="button" :icon="['fas', 'fingerprint']" @click="issueCode">
          {{ t('passkeys.code.issue') }}
        </SecondaryButton>
        <PasskeyCodeDisplay v-else :code="passkeyCode.code" :qr-png="passkeyCode.qrPng" @gone="revokeCode"/>
      </div>
    </template>
  </NeutralContainer>
</template>
