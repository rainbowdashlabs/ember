/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import type {AccountOverview} from '@/api/generated/schema'
import {useDataTable} from '@/composables/useDataTable'
import {accountColumns} from './adminaccountsview/accountColumns'
import {useAccountList} from './adminaccountsview/useAccountList'
import AccountPager from './adminaccountsview/AccountPager.vue'
import AccountDetailModal from './adminaccountsview/AccountDetailModal.vue'

/**
 * Every account of the instance, searched and paged on the server. A row opens the account with
 * what can be done to it: a one-time password, a reset of its second factor, retiring its password.
 */
const {t} = useI18n()
const list = useAccountList()
const {query, page, pages, total, accounts, loading, failure} = list

const table = useDataTable<AccountOverview>({
  id: 'admin-accounts',
  perStation: false,
  rows: accounts,
  columns: computed(() => accountColumns(t)),
  rowKey: account => account.id,
})

const openedId = ref<number | null>(null)
const opened = computed(() => accounts.value.find(account => account.id === openedId.value) ?? null)
const showing = computed({
  get: () => opened.value !== null,
  set: value => { if (!value) openedId.value = null },
})

onMounted(() => list.load())
</script>

<template>
  <ViewContent :title="t('pages.admin-accounts.title')" :subtitle="t('pages.admin-accounts.subtitle')">
    <NeutralContainer class="space-y-3">
      <div class="flex items-end gap-2">
        <TextInput
            v-model="query"
            class="flex-1"
            :placeholder="t('adminAccounts.searchPlaceholder')"
            data-testid="account-search"
            @update:model-value="list.search()"
        />
        <TableColumnPicker :table="table"/>
      </div>
      <FailureAlert :failure="failure"/>
      <RecordTable :table="table" plain clickable test-id="accounts-table" row-test-id="account-row" @row-click="openedId = $event.id">
        <template #empty>
          <EmptyState v-if="!loading" compact>{{ t('adminAccounts.empty') }}</EmptyState>
        </template>
      </RecordTable>
      <AccountPager :page="page" :pages="pages" :total="total" :busy="loading" @go="list.goTo"/>
    </NeutralContainer>
    <AccountDetailModal v-if="opened" v-model="showing" :account="opened" :on-changed="list.load"/>
  </ViewContent>
</template>
