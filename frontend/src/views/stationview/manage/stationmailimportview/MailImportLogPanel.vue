/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {mailImport} from '@/api'
import {MailImportOutcome, wasImported, type MailImportLogEntry} from '@/api/mailImport'
import {formatDateTime} from '@/util/format'

/**
 * What was looked at and what became of it.
 *
 * <p>This is the feature rather than a diagnostic. A rule that quietly imports nothing is the likeliest
 * complaint, and nothing else can answer it: every message that was looked at leaves a line here,
 * including the ones nothing was taken from.
 */
const {t} = useI18n()

const entries = ref<MailImportLogEntry[]>([])
const total = ref(0)
const page = ref(0)
const loading = ref(true)

const pageSize = 50
const pages = computed(() => Math.max(Math.ceil(total.value / pageSize), 1))

async function reload() {
  loading.value = entries.value.length === 0
  try {
    const result = await mailImport.log(page.value, pageSize)
    entries.value = result.entries
    total.value = result.total
  } catch {
    entries.value = []
  }
  loading.value = false
}

/** Only one outcome produced a document; the rest are refusals of one kind or another. */
function badgeFor(entry: MailImportLogEntry) {
  if (wasImported(entry.outcome)) return SuccessBadge
  if (entry.outcome === MailImportOutcome.FAILED || entry.outcome === MailImportOutcome.AUTHENTICATION_FAILED) {
    return ErrorBadge
  }
  return SecondaryBadge
}

watch(page, reload)
reload()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <SectionHeader class="!mb-0">{{ t('mailImport.log') }}</SectionHeader>
        <SecondaryButton :icon="['fas', 'rotate']" data-testid="log-refresh" @click="reload">
          {{ t('common.refresh') }}
        </SecondaryButton>
      </div>
      <MutedText size="sm" tag="p">{{ t('mailImport.logHint') }}</MutedText>

      <Spinner v-if="loading" size="sm"/>
      <MutedText v-else-if="entries.length === 0" size="sm">{{ t('mailImport.logEmpty') }}</MutedText>

      <ul v-else class="space-y-2" data-testid="mail-import-log">
        <li v-for="entry in entries" :key="entry.id" class="border-b border-[var(--border)] pb-2 last:border-0">
          <div class="flex flex-wrap items-center gap-2">
            <component :is="badgeFor(entry)">{{ t(`mailImport.outcome.${entry.outcome}`) }}</component>
            <span class="text-sm break-words">{{ entry.attachmentName ?? entry.subject ?? t('mailImport.logPruned') }}</span>
          </div>
          <MutedText size="sm" tag="p" class="break-words">
            {{ formatDateTime(entry.createdAt) }}
            <template v-if="entry.sender"> · {{ entry.sender }}</template>
            <template v-if="entry.ruleName"> · {{ entry.ruleName }}</template>
          </MutedText>
          <MutedText v-if="entry.reason" size="sm" tag="p" class="break-words whitespace-pre-wrap">
            {{ entry.reason }}
          </MutedText>
        </li>
      </ul>

      <div v-if="pages > 1" class="flex items-center justify-center gap-3">
        <SecondaryButton :disabled="page === 0" @click="page -= 1">{{ t('common.previous') }}</SecondaryButton>
        <MutedText size="sm">{{ t('mailImport.pageOf', {page: page + 1, pages}) }}</MutedText>
        <SecondaryButton :disabled="page + 1 >= pages" @click="page += 1">{{ t('common.next') }}</SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
