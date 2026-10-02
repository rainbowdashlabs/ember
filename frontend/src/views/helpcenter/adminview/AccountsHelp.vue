/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import OneTimePasswordSheet from '@/components/onetimepassword/OneTimePasswordSheet.vue'
import type {AccountOverview} from '@/api/generated/schema'
import {useDataTable} from '@/composables/useDataTable'
import {accountColumns} from '@/views/adminview/adminaccountsview/accountColumns'
import AccountPager from '@/views/adminview/adminaccountsview/AccountPager.vue'
import AccountFacts from '@/views/adminview/adminaccountsview/AccountFacts.vue'
import {SAMPLE_ACCOUNTS, SAMPLE_OPENED_ACCOUNT, sampleSheet} from './accountshelp/sampleAccounts'

const {t} = useI18n()

const opened = SAMPLE_OPENED_ACCOUNT
const sheet = computed(() => sampleSheet(t))

const table = useDataTable<AccountOverview>({
  id: 'help-admin-accounts',
  perStation: false,
  rows: SAMPLE_ACCOUNTS,
  columns: computed(() => accountColumns(t)),
  rowKey: account => account.id,
})
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminAccounts.title')" :subtitle="t('helpCenter.adminAccounts.subtitle')">
    <HelpSection :title="t('helpCenter.adminAccounts.whatIs')">
      <p>{{ t('helpCenter.adminAccounts.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminAccounts.columnsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminAccounts.howTo')">
      <p>{{ t('helpCenter.adminAccounts.searchText') }}</p>
      <p>{{ t('helpCenter.adminAccounts.openText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminAccounts.exampleTitle')">
      <NeutralContainer class="space-y-3">
        <TextInput :model-value="t('helpCenter.adminAccounts.sampleSearch')" :placeholder="t('adminAccounts.searchPlaceholder')"/>
        <RecordTable :table="table" plain/>
        <AccountPager :page="0" :pages="3" :total="62" :busy="false"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminAccounts.actionsTitle')">
      <p>{{ t('helpCenter.adminAccounts.oneTimePasswordText') }}</p>
      <p>{{ t('helpCenter.adminAccounts.resetText') }}</p>
      <p>{{ t('helpCenter.adminAccounts.retireText') }}</p>
      <NeutralContainer class="space-y-4">
        <SubHeader>{{ opened.name }}</SubHeader>
        <AccountFacts :account="opened"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminAccounts.sheetTitle')">
      <p>{{ t('helpCenter.adminAccounts.sheetText') }}</p>
      <NeutralContainer class="space-y-3">
        <SubHeader>{{ sheet.title }}</SubHeader>
        <OneTimePasswordSheet :sheet="sheet"/>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminAccounts.tip') }}</HelpTip>
  </HelpArticle>
</template>
