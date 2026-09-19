/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ColumnPickerButton from '@/components/table/ColumnPickerButton.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import AccountSearchPicker from '@/components/input/search/AccountSearchPicker.vue'
import {twoFactorAdmin} from '@/api'
import type {AccountSearchResult, AuditEntry} from '@/api/twoFactorAdmin'
import {useDataTable} from '@/composables/useDataTable'
import {apiErrorMessage} from '@/util/apiError'
import {auditColumns} from './auditColumns'

const {t} = useI18n()

const audit = ref<AuditEntry[]>([])
const auditLoading = ref(false)
const auditOffset = ref(0)
const auditPageSize = 50
const auditHasMore = ref(false)
const auditAccountFilter = ref<number | null>(null)
const auditFilterUid = ref<string | null>(null)
const error = ref('')

/**
 * The two-factor audit log, a page at a time, narrowed to one account where one is picked.
 *
 * <p>The table sorts and filters what has been loaded so far; loading more adds to it.
 */
const table = useDataTable<AuditEntry>({
  id: 'admin-two-factor-audit',
  rows: audit,
  columns: computed(() => auditColumns(t)),
  rowKey: entry => entry.id,
})

defineExpose({reload: () => loadAudit(true)})

async function loadAudit(reset = false) {
  if (reset) {
    audit.value = []
    auditOffset.value = 0
  }
  auditLoading.value = true
  try {
    const entries = await twoFactorAdmin.listAuditLog({
      accountId: auditAccountFilter.value ?? undefined,
      limit: auditPageSize,
      offset: auditOffset.value,
    })
    audit.value = audit.value.concat(entries)
    auditHasMore.value = entries.length === auditPageSize
    auditOffset.value += entries.length
  } catch (e) {
    error.value = apiErrorMessage(e) || t('common.error')
  }
  auditLoading.value = false
}

function onAuditPick(item: AccountSearchResult) {
  auditAccountFilter.value = item.id
  auditFilterUid.value = item.uid
  loadAudit(true)
}

function onAuditUidUpdate(uid: string | null | undefined) {
  auditFilterUid.value = uid ?? null
  if (uid == null) {
    auditAccountFilter.value = null
    loadAudit(true)
  }
}


onMounted(() => loadAudit(true))
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('twoFactor.admin.audit.title') }}</SubHeader>
    <FailureAlert :message="error"/>
    <div class="flex items-end gap-2">
      <div class="flex-1">
        <MutedText tag="label" size="sm">{{ t('twoFactor.admin.audit.filterAccount') }}</MutedText>
        <AccountSearchPicker
            :model-value="auditFilterUid"
            :placeholder="t('twoFactor.admin.accountSearchPlaceholder')"
            @pick="onAuditPick"
            @update:model-value="onAuditUidUpdate"
        />
      </div>
      <ColumnPickerButton :options="table.pickerOptions" @toggle="table.toggleColumn"/>
      <SecondaryButton :disabled="auditLoading" @click="loadAudit(true)">
        {{ t('common.refresh') }}
      </SecondaryButton>
    </div>

    <RecordTable :table="table" plain test-id="two-factor-audit-table">
      <template #cell-account="{text}"><span class="font-mono">{{ text }}</span></template>
      <template #cell-actor="{text}"><span class="font-mono">{{ text }}</span></template>
      <template #empty>
        <EmptyState v-if="!auditLoading" compact>{{ t('twoFactor.admin.audit.empty') }}</EmptyState>
      </template>
    </RecordTable>

    <div v-if="auditHasMore" class="text-center">
      <SecondaryButton :disabled="auditLoading" @click="loadAudit(false)">
        {{ t('twoFactor.admin.audit.loadMore') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
