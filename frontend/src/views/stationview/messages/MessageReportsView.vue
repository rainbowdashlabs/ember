/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {ReportedMessageResponse} from '@/api/generated/schema'
import {listReports} from '@/api/messages'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import ReportCard from './messagereportsview/ReportCard.vue'

/** The messages members reported in the station's chats, the oldest first, each to remove or dismiss. */
const {t} = useI18n()
const reports = ref<ReportedMessageResponse[]>([])

const {loading, failure, reload} = useAsyncLoader(async () => {
  reports.value = await listReports()
})
</script>

<template>
  <ViewContent :title="t('pages.message-reports.title')" :subtitle="t('pages.message-reports.subtitle')">
    <div class="space-y-4">
      <Spinner v-if="loading" size="md"/>
      <FailureAlert :failure="failure"/>
      <EmptyState v-if="!loading && reports.length === 0" :message="t('messageReports.empty')"/>
      <ReportCard v-for="report in reports" :key="report.id" :report="report" :on-handled="reload"/>
    </div>
  </ViewContent>
</template>
