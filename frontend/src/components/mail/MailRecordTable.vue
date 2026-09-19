/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {MailDeliveryStatus, type MailRecord} from '@/api/mailProviders'
import type {DataTableApi} from '@/composables/useDataTable'
import {isStuck, isTroubled} from './mailRecordColumns'

/**
 * A list of mails and what became of each. A mail that went wrong is marked red at its edge, one
 * that arrived green, and one stuck in the queue says so outright. Its `actions` slot takes what may
 * be done with a mail.
 */
defineProps<{
  table: DataTableApi<MailRecord>
  testId: string
}>()

const {t} = useI18n()

function toneOf(mail: MailRecord): string {
  if (isTroubled(mail)) return 'border-l-4 border-l-(--error)'
  return mail.deliveryStatus === MailDeliveryStatus.DELIVERED ? 'border-l-4 border-l-(--success)' : ''
}
</script>

<template>
  <RecordTable :row-class="toneOf" :table="table" :test-id="testId" plain row-test-id="mail-record">
    <template #cell-recipient="{text}">
      <span class="font-medium break-all">{{ text }}</span>
    </template>
    <template #cell-status="{row, text}">
      {{ text }}
      <div v-if="isStuck(row)" class="text-xs text-(--error)">{{ t('mailDashboard.unreachable') }}</div>
    </template>
    <template #cell-detail="{text}">
      <span class="text-xs text-(--error) break-words">{{ text }}</span>
    </template>
    <template v-if="$slots.actions" #actions="{row}">
      <slot :row="row" name="actions"/>
    </template>
    <template #empty>
      <EmptyHint>{{ t('mailDashboard.noMails') }}</EmptyHint>
    </template>
  </RecordTable>
</template>
