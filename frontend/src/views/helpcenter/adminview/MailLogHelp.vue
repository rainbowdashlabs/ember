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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MailPoolOverview from '@/components/mail/MailPoolOverview.vue'
import MailProviderStanding from '@/components/mail/MailProviderStanding.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ProviderStanding} from '@/api/generated/schema'
import FailedInstanceMails from '@/views/adminview/adminmaillogview/FailedInstanceMails.vue'
import FailedStationMails from '@/views/adminview/adminmaillogview/FailedStationMails.vue'
import {SAMPLE_FAILED_MAILS, SAMPLE_STATION_FAILURES} from './mailloghelp/sampleFailedMails'

const {t} = useI18n()

/** Two providers of the instance as the log shows them: one with a daily limit, one without. */
const providers: ProviderStanding[] = [
  {
    position: 0, provider: 'BREVO', senderAddress: 'post@ember.example', attempts: 2, dailySendLimit: 300,
    sentToday: 180, waiting: 0, exhausted: false, viaInstance: false,
    pool: {limit: 150, sentToday: 64, stations: [
      {instancePosition: 0, stationUid: 'nord', name: 'Wache Nord', sentToday: 40},
      {instancePosition: 0, stationUid: 'sued', name: 'Wache Süd', sentToday: 24},
    ]},
  },
  {
    position: 1, provider: 'SMTP', senderAddress: 'post@ember.example', attempts: 2, dailySendLimit: 0,
    sentToday: 5, waiting: 0, exhausted: false, viaInstance: false,
    pool: {limit: null, sentToday: 0, stations: []},
  },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminMailLog.title')" :subtitle="t('helpCenter.adminMailLog.subtitle')">
    <HelpSection :title="t('helpCenter.adminMailLog.whatIs')">
      <p>{{ t('helpCenter.adminMailLog.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.failedTitle')">
      <p>{{ t('helpCenter.adminMailLog.failedText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.failedKindsText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.failedInstanceText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.failedReasonsText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.failedStationsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.failedExampleTitle')">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('failedMails.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('failedMails.hint') }}</MutedText>
        <div inert class="space-y-4">
          <FailedInstanceMails :mails="SAMPLE_FAILED_MAILS" :total="SAMPLE_FAILED_MAILS.length" :busy="false"/>
          <FailedStationMails :stations="SAMPLE_STATION_FAILURES" :busy="false"/>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.acknowledgeTitle')">
      <p>{{ t('helpCenter.adminMailLog.acknowledgeText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.acknowledgeUpToText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.failedRequeueText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.failedNoRequeueText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.badgeTitle')">
      <p>{{ t('helpCenter.adminMailLog.badgeText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.badgeRefreshText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.statesTitle')">
      <p>{{ t('helpCenter.adminMailLog.statesText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.deliveryText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.stuckTitle')">
      <p>{{ t('helpCenter.adminMailLog.stuckText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.requeueText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.providersTitle')">
      <p>{{ t('helpCenter.adminMailLog.providersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.poolTitle')">
      <p>{{ t('helpCenter.adminMailLog.poolText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.poolWarningText') }}</p>
      <NeutralContainer class="space-y-3">
        <MailPoolOverview :pool="{sharePercent: 50, grantedStations: 2}" :providers="providers"/>
        <MailProviderStanding v-for="standing in providers" :key="standing.position" :standing="standing"/>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminMailLog.tip') }}</HelpTip>
  </HelpArticle>
</template>
