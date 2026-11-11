/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MailDashboard from '@/components/mail/MailDashboard.vue'
import FailedMailsSection from './adminmaillogview/FailedMailsSection.vue'
import {useMonitoringCounts} from '@/composables/useMonitoringCounts'
import {getInstanceMailDashboard, liftInstanceBlock, requeueInstanceStuckMails} from '@/api/mailProviders'

const {t} = useI18n()
const {refresh: refreshCounts} = useMonitoringCounts()
</script>

<template>
  <ViewContent :title="t('pages.admin-mail-log.title')" :subtitle="t('pages.admin-mail-log.subtitle')">
    <div class="space-y-4">
      <FailedMailsSection :on-changed="refreshCounts"/>
      <MailDashboard
          :load="getInstanceMailDashboard"
          :lift="liftInstanceBlock"
          :requeue="requeueInstanceStuckMails"
          :per-station="false"
      />
    </div>
  </ViewContent>
</template>
