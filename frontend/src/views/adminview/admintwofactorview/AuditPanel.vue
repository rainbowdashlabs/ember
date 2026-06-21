/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TableHeaderCell from '@/components/typography/TableHeaderCell.vue'
import AccountSearchPicker from '@/components/input/search/AccountSearchPicker.vue'
import {twoFactorAdmin} from '@/api'
import type {AccountSearchResult, AuditEntry} from '@/api/twoFactorAdmin'

const {t} = useI18n()

const audit = ref<AuditEntry[]>([])
const auditLoading = ref(false)
const auditOffset = ref(0)
const auditPageSize = 50
const auditHasMore = ref(false)
const auditAccountFilter = ref<number | null>(null)
const auditFilterUid = ref<string | null>(null)
const error = ref('')

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
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  auditLoading.value = false
}

function onAuditPick(item: AccountSearchResult) {
  auditAccountFilter.value = item.id
  auditFilterUid.value = item.uid
  loadAudit(true)
}

function onAuditUidUpdate(uid: string | null) {
  auditFilterUid.value = uid
  if (uid == null) {
    auditAccountFilter.value = null
    loadAudit(true)
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('de-DE')
}

onMounted(() => loadAudit(true))
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('twoFactor.admin.audit.title') }}</SubHeader>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
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
      <SecondaryButton :disabled="auditLoading" @click="loadAudit(true)">
        {{ t('common.refresh') }}
      </SecondaryButton>
    </div>

    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-(--text-muted)">
            <TableHeaderCell>{{ t('twoFactor.admin.audit.col.when') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.audit.col.account') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.audit.col.actor') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.audit.col.event') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.audit.col.factor') }}</TableHeaderCell>
            <TableHeaderCell>{{ t('twoFactor.admin.audit.col.country') }}</TableHeaderCell>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in audit" :key="e.id" class="border-t border-(--border)">
            <td class="py-2 pr-3 text-(--text-muted) whitespace-nowrap">{{ formatDate(e.createdAt) }}</td>
            <td class="py-2 pr-3 font-mono">{{ e.accountId }}</td>
            <td class="py-2 pr-3 font-mono">{{ e.actorId ?? '—' }}</td>
            <td class="py-2 pr-3">{{ e.event }}</td>
            <td class="py-2 pr-3 text-(--text-muted)">{{ e.factorKind ?? '—' }}</td>
            <td class="py-2 pr-3 text-(--text-muted)">{{ e.country ?? '—' }}</td>
          </tr>
          <tr v-if="!auditLoading && audit.length === 0">
            <td colspan="6" class="py-4 text-(--text-muted) text-center">{{ t('twoFactor.admin.audit.empty') }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="auditHasMore" class="text-center">
      <SecondaryButton :disabled="auditLoading" @click="loadAudit(false)">
        {{ t('twoFactor.admin.audit.loadMore') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
