/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import AccountSearchPicker from '@/components/input/search/AccountSearchPicker.vue'
import {twoFactorAdmin} from '@/api'
import type {AccountSearchResult} from '@/api/twoFactorAdmin'

const emit = defineEmits<{
  (e: 'reset-performed'): void
}>()

const {t} = useI18n()

const resetAccountId = ref<number | null>(null)
const resetAccountUid = ref<string | null>(null)
const resetAccountName = ref<string | null>(null)
const resetLoading = ref(false)
const resetConfirmOpen = ref(false)
const resetSuccess = ref('')
const error = ref('')

function onResetPick(item: AccountSearchResult) {
  resetAccountId.value = item.id
  resetAccountUid.value = item.uid
  resetAccountName.value = `${item.displayName} (${item.email})`
}

function onResetUidUpdate(uid: string | null) {
  resetAccountUid.value = uid
  if (uid == null) {
    resetAccountId.value = null
    resetAccountName.value = null
  }
}

function openResetModal() {
  if (!resetAccountId.value) return
  resetSuccess.value = ''
  resetConfirmOpen.value = true
}

async function confirmReset() {
  if (!resetAccountId.value) return
  resetLoading.value = true
  try {
    await twoFactorAdmin.resetAccount2FAByInstanceAdmin(resetAccountId.value)
    resetSuccess.value = t('twoFactor.admin.resetSuccess', {name: resetAccountName.value ?? resetAccountId.value})
    resetConfirmOpen.value = false
    resetAccountId.value = null
    resetAccountUid.value = null
    resetAccountName.value = null
    emit('reset-performed')
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  resetLoading.value = false
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('twoFactor.admin.resetTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('twoFactor.admin.resetHint') }}</MutedText>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="resetSuccess" variant="success">{{ resetSuccess }}</Alert>
    <div class="flex items-end gap-2">
      <div class="flex-1">
        <MutedText tag="label" size="sm">{{ t('twoFactor.admin.resetAccountLabel') }}</MutedText>
        <AccountSearchPicker
            :model-value="resetAccountUid"
            :placeholder="t('twoFactor.admin.accountSearchPlaceholder')"
            @pick="onResetPick"
            @update:model-value="onResetUidUpdate"
        />
      </div>
      <ErrorButton :disabled="!resetAccountId" @click="openResetModal">
        {{ t('twoFactor.admin.reset') }}
      </ErrorButton>
    </div>

    <Modal v-model="resetConfirmOpen" size="sm">
      <div class="space-y-4 p-4">
        <SubHeader>{{ t('twoFactor.admin.resetConfirmTitle') }}</SubHeader>
        <p class="text-sm">{{ t('twoFactor.admin.resetConfirmText', {name: resetAccountName ?? resetAccountId}) }}</p>
        <Alert variant="error">{{ t('twoFactor.admin.resetWarning') }}</Alert>
        <div class="flex justify-end gap-2">
          <SecondaryButton :disabled="resetLoading" @click="resetConfirmOpen = false">
            {{ t('common.cancel') }}
          </SecondaryButton>
          <ErrorButton :disabled="resetLoading" @click="confirmReset">
            {{ resetLoading ? t('common.loading') : t('twoFactor.admin.reset') }}
          </ErrorButton>
        </div>
      </div>
    </Modal>
  </NeutralContainer>
</template>
