/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {useDataTable} from '@/composables/useDataTable'
import type {FailedMail} from '@/api/generated/schema'
import {failedMailColumns} from './failedMailColumns'

/**
 * The instance's own mails that did not arrive, in full: whom they were for, what they were about and
 * why they failed. Each can be taken note of, and one the queue gave up on can be sent again.
 */
const props = defineProps<{
  mails: readonly FailedMail[]
  total: number
  busy: boolean
}>()

const emit = defineEmits<{
  acknowledge: [id: number]
  acknowledgeAll: []
  requeue: [id: number]
  more: []
}>()

const {t} = useI18n()

const table = useDataTable<FailedMail>({
  id: 'admin-failed-mails',
  perStation: false,
  rows: () => props.mails,
  columns: computed(() => failedMailColumns(t)),
  rowKey: mail => mail.id,
})
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between gap-2 flex-wrap">
      <SubHeader>{{ t('failedMails.instanceTitle', {count: total}) }}</SubHeader>
      <SecondaryButton :disabled="busy" :icon="['fas', 'check-double']" data-testid="failed-acknowledge-all" @click="emit('acknowledgeAll')">
        {{ t('failedMails.acknowledgeAll') }}
      </SecondaryButton>
    </div>
    <RecordTable :table="table" plain test-id="failed-instance-mails" row-test-id="failed-mail">
      <template #cell-recipient="{text}">
        <span class="font-medium break-all">{{ text }}</span>
      </template>
      <template #cell-detail="{text}">
        <span class="text-xs text-(--color-error-badge) break-words">{{ text }}</span>
      </template>
      <template #actions="{row}">
        <ButtonRow>
          <SecondaryButton :disabled="busy" :icon="['fas', 'check']" data-testid="failed-acknowledge" @click="emit('acknowledge', row.id)">
            {{ t('failedMails.acknowledge') }}
          </SecondaryButton>
          <SecondaryButton v-if="row.requeueable" :disabled="busy" :icon="['fas', 'paper-plane']" data-testid="failed-requeue" @click="emit('requeue', row.id)">
            {{ t('failedMails.requeue') }}
          </SecondaryButton>
        </ButtonRow>
      </template>
    </RecordTable>
    <SecondaryButton v-if="mails.length < total" :disabled="busy" :icon="['fas', 'chevron-down']" data-testid="failed-more" @click="emit('more')">
      {{ t('failedMails.more', {shown: mails.length, total}) }}
    </SecondaryButton>
  </div>
</template>
