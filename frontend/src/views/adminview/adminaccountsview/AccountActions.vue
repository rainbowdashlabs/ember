/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import OneTimePasswordAction from '@/components/onetimepassword/OneTimePasswordAction.vue'
import {adminAccounts, adminSettings, twoFactorAdmin} from '@/api'
import type {AccountOverview} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * What the instance's administration can do to one account: hand it a one-time password, reset its
 * second factor and retire its password. Each asks first, and the list is read again afterwards.
 */
const props = defineProps<{
  account: AccountOverview
  /** Called once something about the account has changed, so the list can show it. */
  onChanged: () => void
}>()

const {t} = useI18n()
const confirmingReset = ref(false)
const confirmingRetire = ref(false)
const notice = ref('')

function issue() {
  return adminAccounts.issueOneTimePassword(props.account.id).finally(() => props.onChanged())
}

const {running: resetting, failure: resetFailure, run: confirmReset} = useAsyncAction(async () => {
  await twoFactorAdmin.resetAccount2FAByInstanceAdmin(props.account.id)
  confirmingReset.value = false
  notice.value = t('twoFactor.admin.resetSuccess', {name: props.account.name})
  props.onChanged()
})

const {running: retiring, failure: retireFailure, run: confirmRetire} = useAsyncAction(async () => {
  await adminSettings.retirePassword(props.account.id)
  confirmingRetire.value = false
  notice.value = t('adminAccounts.retire.success', {name: props.account.name})
  props.onChanged()
})
</script>

<template>
  <div class="space-y-4">
    <Alert v-if="notice" variant="success">{{ notice }}</Alert>
    <FailureAlert :failure="resetFailure ?? retireFailure"/>

    <div class="space-y-2">
      <MutedText tag="p" size="sm">{{ t('oneTimePassword.actionHint') }}</MutedText>
      <OneTimePasswordAction :name="account.name" :issue="issue"/>
    </div>

    <div class="space-y-2 border-t border-(--border) pt-4">
      <MutedText tag="p" size="sm">{{ t('twoFactor.admin.resetHint') }}</MutedText>
      <ErrorButton :icon="['fas', 'rotate-left']" data-testid="account-reset-2fa" @click="confirmingReset = true">
        {{ t('twoFactor.admin.reset') }}
      </ErrorButton>
    </div>

    <div class="space-y-2 border-t border-(--border) pt-4">
      <MutedText tag="p" size="sm">{{ t('adminAccounts.retire.hint') }}</MutedText>
      <SecondaryButton :icon="['fas', 'ban']" data-testid="account-retire-password" @click="confirmingRetire = true">
        {{ t('adminAccounts.retire.action') }}
      </SecondaryButton>
    </div>

    <ConfirmDeleteModal
        v-model="confirmingReset"
        :title="t('twoFactor.admin.resetConfirmTitle')"
        :message="t('twoFactor.admin.resetConfirmText', {name: account.name})"
        :confirm-label="t('twoFactor.admin.reset')"
        :busy="resetting"
        @confirm="confirmReset"
    >
      <Alert variant="error">{{ t('twoFactor.admin.resetWarning') }}</Alert>
    </ConfirmDeleteModal>
    <ConfirmDeleteModal
        v-model="confirmingRetire"
        :title="t('adminAccounts.retire.confirmTitle')"
        :message="t('adminAccounts.retire.confirmText', {name: account.name})"
        :confirm-label="t('adminAccounts.retire.action')"
        :busy="retiring"
        @confirm="confirmRetire"
    />
  </div>
</template>
