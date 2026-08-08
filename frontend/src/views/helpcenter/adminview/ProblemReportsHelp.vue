/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ReportCard from '@/views/adminview/adminproblemreportsview/ReportCard.vue'
import type {ProblemReport} from '@/api/problemReports'

const {t} = useI18n()

const RECENT_REQUESTS = JSON.stringify([
  {method: 'GET', url: '/station/events', status: 200, duration: 120, timestamp: '2026-05-04T18:22:11Z'},
  {method: 'POST', url: '/station/events', status: 500, duration: 940, timestamp: '2026-05-04T18:22:14Z'},
])

const REPORTS: ProblemReport[] = [
  {
    id: 1,
    stationId: 'demo',
    reporterName: 'Lena Hoffmann',
    message: 'Beim Speichern eines Termins passiert nichts.',
    pageUrl: '/station/events/new',
    userRoles: 'MEMBER, EVENT_EDIT',
    recentRequests: RECENT_REQUESTS,
    browserInfo: 'Firefox 141 auf Windows',
    screenSize: '1920 x 1080',
    acknowledged: false,
    createdAt: '2026-05-04T18:23:00Z',
  },
  {
    id: 2,
    stationId: 'demo',
    reporterName: 'Jonas Weber',
    message: 'Das Menü lässt sich auf dem Handy nicht schließen.',
    pageUrl: '/station/dashboard/overview',
    userRoles: 'TEAM',
    browserInfo: 'Safari auf iPhone',
    screenSize: '390 x 844',
    acknowledged: true,
    createdAt: '2026-05-02T09:10:00Z',
  },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminProblemReports.title')"
               :subtitle="t('helpCenter.adminProblemReports.subtitle')">
    <HelpSection :title="t('helpCenter.adminProblemReports.whatIs')">
      <p>{{ t('helpCenter.adminProblemReports.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblemReports.howTo')">
      <p>{{ t('helpCenter.adminProblemReports.howToStep1') }}</p>
      <p>{{ t('helpCenter.adminProblemReports.howToStep2') }}</p>
      <p>{{ t('helpCenter.adminProblemReports.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblemReports.exampleTitle')">
      <p>{{ t('helpCenter.adminProblemReports.exampleText') }}</p>
      <div class="space-y-4">
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput :model-value="true"/>
            {{ t('problemReport.showAcknowledged') }}
          </label>
          <SecondaryButton :icon="['fas', 'check-double']">
            {{ t('problemReport.acknowledgeAll') }}
          </SecondaryButton>
        </div>
        <ReportCard :report="REPORTS[0]!" :expanded="true"/>
        <ReportCard :report="REPORTS[1]!" :expanded="false"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblemReports.detailsTitle')">
      <p>{{ t('helpCenter.adminProblemReports.detailsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblemReports.ackTitle')">
      <p>{{ t('helpCenter.adminProblemReports.ackText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminProblemReports.tip') }}</HelpTip>
  </HelpArticle>
</template>
