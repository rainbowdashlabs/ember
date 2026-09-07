/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import { auth } from '@/api'
import TwoFactorSection from '@/views/stationview/profile/settingsview/TwoFactorSection.vue'
import PasskeySection from '@/views/accountview/accountsecurityview/PasskeySection.vue'

const { t } = useI18n()

const error = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

async function changePassword() {
  if (newPassword.value !== confirmPassword.value) {
    error.value = t('profile.passwordMismatch')
    throw new Error('mismatch')
  }
  error.value = ''
  try {
    await auth.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    error.value = t('profile.passwordError')
    throw e
  }
}
</script>

<template>
  <ViewContent :title="t('pages.account-security.title')" :subtitle="t('pages.account-security.subtitle')">
    <div class="max-w-2xl mx-auto space-y-8 p-4">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('profile.passwordTitle') }}</SectionHeader>
        <div class="grid gap-4 sm:grid-cols-3">
          <div class="space-y-1">
            <FieldLabel>{{ t('profile.currentPassword') }}</FieldLabel>
            <PasswordInput v-model="currentPassword" :placeholder="t('profile.currentPassword')" autocomplete="current-password"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('profile.newPassword') }}</FieldLabel>
            <PasswordInput v-model="newPassword" :placeholder="t('profile.newPassword')" autocomplete="new-password"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('profile.confirmPassword') }}</FieldLabel>
            <PasswordInput v-model="confirmPassword" :placeholder="t('profile.confirmPassword')" autocomplete="new-password"/>
          </div>
        </div>
        <SaveButton :disabled="!currentPassword || !newPassword || !confirmPassword" :action="changePassword">
          {{ t('profile.changePassword') }}
        </SaveButton>
      </NeutralContainer>

      <PasskeySection/>

      <TwoFactorSection data-onboarding="account.two-factor"/>
    </div>
  </ViewContent>
</template>
