/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import AccountSearchPicker from '@/components/input/search/AccountSearchPicker.vue'
import {twoFactorAdmin} from '@/api'
import type {AccountSearchResult} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'

const emit = defineEmits<{
  (e: 'reset-performed'): void
}>()

const {t} = useI18n()

const resetAccountId = ref<number | null>(null)
const resetAccountUid = ref<string | null>(null)
const resetAccountName = ref<string | null>(null)
const resetConfirmOpen = ref(false)
const resetSuccess = ref('')

function onResetPick(item: AccountSearchResult) {
  resetAccountId.value = item.id
  resetAccountUid.value = item.uid
  resetAccountName.value = twoFactorAdmin.accountLabel(item)
}

function onResetUidUpdate(uid: string | null | undefined) {
  resetAccountUid.value = uid ?? null
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

const {running: resetLoading, failure, run: confirmReset} = useAsyncAction(async () => {
  if (!resetAccountId.value) return
  await twoFactorAdmin.resetAccount2FAByInstanceAdmin(resetAccountId.value)
  resetSuccess.value = t('twoFactor.admin.resetSuccess', {name: resetAccountName.value ?? resetAccountId.value})
  resetConfirmOpen.value = false
  resetAccountId.value = null
  resetAccountUid.value = null
  resetAccountName.value = null
  emit('reset-performed')
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('twoFactor.admin.resetTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('twoFactor.admin.resetHint') }}</MutedText>
    <FailureAlert :failure="failure"/>
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

    <ConfirmDeleteModal
        v-model="resetConfirmOpen"
        :title="t('twoFactor.admin.resetConfirmTitle')"
        :message="t('twoFactor.admin.resetConfirmText', {name: resetAccountName ?? resetAccountId})"
        :confirm-label="t('twoFactor.admin.reset')"
        :busy="resetLoading"
        @confirm="confirmReset"
    >
      <Alert variant="error">{{ t('twoFactor.admin.resetWarning') }}</Alert>
    </ConfirmDeleteModal>
  </NeutralContainer>
</template>
