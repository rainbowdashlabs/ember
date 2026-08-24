/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {managedMembers} from '@/api'
import type {ManagedAccess} from '@/api/managedMembers'
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
        <TextInput v-model="username" :placeholder="t('profileManaged.access.usernamePlaceholder')"/>
        <MutedText tag="p" size="sm">{{ t('profileManaged.access.usernameHint') }}</MutedText>
        <SaveButton :action="saveUsername"/>
      </div>

      <div class="flex items-start justify-between gap-4 border-t border-(--border) pt-4">
        <div>
          <FieldLabel>{{ t('profileManaged.access.login') }}</FieldLabel>
          <MutedText tag="p" size="sm">
            {{ access.canSignIn ? t('profileManaged.access.loginHint') : t('profileManaged.access.loginNeedsEmail') }}
          </MutedText>
        </div>
        <ToggleInput :model-value="access.loginEnabled" :disabled="!access.canSignIn"
                     :aria-label="t('profileManaged.access.login')"
                     @update:model-value="toggleLogin"/>
      </div>
    </template>
  </NeutralContainer>
</template>
