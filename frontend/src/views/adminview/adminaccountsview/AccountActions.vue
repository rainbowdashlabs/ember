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
 * What the instance's administration can do to one account: send the mail it waits for while it is
 * not yet ready, hand it a one-time password, reset its second factor and retire its password. The
 * last three ask first, and the list is read again afterwards.
 *
 * <p>The setup mail, the reset and the retirement run as one action, so the page shows the outcome
 * of the latest of them only: starting one clears the success and the failure of the one before.
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

type Action = 'setupMail' | 'reset' | 'retire'

const running = ref<Action | null>(null)

async function perform(action: Action): Promise<string> {
  const name = props.account.name
  if (action === 'setupMail') {
    const sent = await adminAccounts.sendSetupMail(props.account.id)
    return t(`adminAccounts.setupMail.sent.${sent.outcome}`, {name})
  }
  if (action === 'reset') {
    await twoFactorAdmin.resetAccount2FAByInstanceAdmin(props.account.id)
    confirmingReset.value = false
    return t('twoFactor.admin.resetSuccess', {name})
  }
  await adminSettings.retirePassword(props.account.id)
  confirmingRetire.value = false
  return t('adminAccounts.retire.success', {name})
}

const {failure, run} = useAsyncAction(async (action: Action) => {
  notice.value = ''
  running.value = action
  try {
    notice.value = await perform(action)
    props.onChanged()
  } finally {
    running.value = null
  }
})
</script>

<template>
  <div class="space-y-4">
    <Alert v-if="notice" variant="success">{{ notice }}</Alert>
    <FailureAlert :failure="failure"/>

    <div v-if="account.setupMail" class="space-y-2">
      <MutedText tag="p" size="sm">{{ t(`adminAccounts.setupMail.hint.${account.setupMail}`) }}</MutedText>
      <SecondaryButton
          :icon="['fas', 'paper-plane']"
          :disabled="running !== null"
          data-testid="account-setup-mail"
          @click="run('setupMail')"
      >
        {{ t(`adminAccounts.setupMail.action.${account.setupMail}`) }}
      </SecondaryButton>
    </div>

    <div class="space-y-2" :class="{'border-t border-(--border) pt-4': account.setupMail}">
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
        :busy="running === 'reset'"
        @confirm="run('reset')"
    >
      <Alert variant="error">{{ t('twoFactor.admin.resetWarning') }}</Alert>
    </ConfirmDeleteModal>
    <ConfirmDeleteModal
        v-model="confirmingRetire"
        :title="t('adminAccounts.retire.confirmTitle')"
        :message="t('adminAccounts.retire.confirmText', {name: account.name})"
        :confirm-label="t('adminAccounts.retire.action')"
        :busy="running === 'retire'"
        @confirm="run('retire')"
    />
  </div>
</template>
