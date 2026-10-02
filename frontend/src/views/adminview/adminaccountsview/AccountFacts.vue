/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import type {AccountOverview} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'
import {associationLabel, passwordStateOf, PasswordState} from './accountColumns'

/** What the account list knows about one account, written out in full. */
const props = defineProps<{account: AccountOverview}>()

const {t} = useI18n()

const facts = computed(() => {
  const account = props.account
  const none = t('adminAccounts.none')
  const state = passwordStateOf(account)
  const password = state === PasswordState.ONE_TIME
    ? t('adminAccounts.oneTimeUntil', {date: formatDateTime(account.oneTimePasswordExpiresAt)})
    : t(`adminAccounts.password.${state}`)
  return [
    {key: 'loginName', value: account.loginName},
    {key: 'stations', value: account.stations.join(', ') || none},
    {key: 'associations', value: account.associations.map(role => associationLabel(role, t)).join(', ') || none},
    {key: 'role', value: t(`adminAccounts.roles.${account.instanceUserType}`)},
    {key: 'lastSignIn', value: account.lastSignInAt ? formatDateTime(account.lastSignInAt) : t('adminAccounts.neverSignedIn')},
    {key: 'password', value: password},
    {key: 'passkeys', value: String(account.passkeys)},
    {key: 'twoFactor', value: account.twoFactor ? t('common.yes') : t('common.no')},
  ]
})
</script>

<template>
  <dl class="grid gap-3 sm:grid-cols-2">
    <div v-for="fact in facts" :key="fact.key">
      <dt><DetailLabel>{{ t(`adminAccounts.col.${fact.key}`) }}</DetailLabel></dt>
      <dd class="text-sm break-words">{{ fact.value }}</dd>
    </div>
  </dl>
</template>
