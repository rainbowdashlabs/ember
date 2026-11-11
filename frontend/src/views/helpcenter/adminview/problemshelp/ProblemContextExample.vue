/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * The surrounding log lines of an opened problem, as the page shows them, with the problem's own line marked.
 */
import {useI18n} from 'vue-i18n'
import type {LogEntry} from '@/api/generated/schema'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PillBadge from '@/components/badge/PillBadge.vue'
import LogEntryRow from '@/components/log/LogEntryRow.vue'

const {t} = useI18n()

const lines: {entry: LogEntry, problem: boolean}[] = [
  {problem: false, entry: {id: 1, loggedAt: '2026-05-12T14:10:01Z', level: 'INFO', logger: 'dev.chojo.ember.MailQueue',
    thread: 'mail-1', message: 'Sending 3 queued mails', throwable: null}},
  {problem: true, entry: {id: 2, loggedAt: '2026-05-12T14:10:02Z', level: 'ERROR', logger: 'dev.chojo.ember.MailService',
    thread: 'mail-1', message: 'MailSendException: Connection refused to smtp.example.com:587', throwable: null}},
  {problem: false, entry: {id: 3, loggedAt: '2026-05-12T14:10:02Z', level: 'INFO', logger: 'dev.chojo.ember.MailQueue',
    thread: 'mail-1', message: 'Retrying in 60 seconds', throwable: null}},
]
</script>

<template>
  <div class="mt-3">
    <SectionHeader class="!text-xs !mb-1">{{ t('adminProblems.contextTitle') }}</SectionHeader>
    <div class="flex flex-wrap items-center gap-2 mb-2">
      <SelectionToggleButton size="sm" :selected="true">12.05.2026, 16:10</SelectionToggleButton>
      <SelectionToggleButton size="sm" :selected="false">12.05.2026, 14:32</SelectionToggleButton>
      <SelectionToggleButton size="sm" :selected="false">{{ t('adminProblems.contextAllThreads') }}</SelectionToggleButton>
    </div>
    <ul class="space-y-1">
      <li v-for="line in lines" :key="line.entry.id" :class="{'ring-2 ring-primary rounded-lg': line.problem}">
        <PillBadge v-if="line.problem" class="text-xs px-1.5 py-0.5 bg-(--bg-accent) m-1">
          {{ t('adminProblems.contextThisEntry') }}
        </PillBadge>
        <LogEntryRow :entry="line.entry"/>
      </li>
    </ul>
  </div>
</template>
