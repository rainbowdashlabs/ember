/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {AccountOverview} from '@/api/generated/schema'
import AccountFacts from './AccountFacts.vue'
import AccountActions from './AccountActions.vue'

/** One account of the list, opened: everything known about it and what can be done to it. */
defineProps<{
  account: AccountOverview
  onChanged: () => void
}>()

const open = defineModel<boolean>({required: true})

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-5" data-testid="account-detail">
      <div>
        <SubHeader>{{ account.name }}</SubHeader>
        <MutedText v-if="account.email" tag="p" size="sm">{{ account.email }}</MutedText>
      </div>
      <AccountFacts :account="account"/>
      <div class="border-t border-(--border) pt-4">
        <SubHeader>{{ t('adminAccounts.actionsTitle') }}</SubHeader>
      </div>
      <AccountActions :account="account" :on-changed="onChanged"/>
    </div>
  </Modal>
</template>
