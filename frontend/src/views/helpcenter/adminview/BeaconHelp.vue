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
import BeaconFaultCard from '@/views/adminview/adminbeaconview/BeaconFaultCard.vue'
import type {BeaconFault} from '@/api/beacon'

const {t} = useI18n()

const FAULTS: BeaconFault[] = [
  {
    id: 1,
    fingerprint: 'java.lang.IllegalStateException|dev.chojo.ember.feature.events.service.EventService.load',
    level: 'ERROR',
    exceptionClass: 'java.lang.IllegalStateException',
    logger: 'dev.chojo.ember.feature.events.service.EventService',
    frames: 'dev.chojo.ember.feature.events.service.EventService.load\ndev.chojo.ember.feature.events.route.EventRoutes.list',
    instances: 12,
    occurrences: 340,
    versions: ['26.14.4', '26.15.0'],
    firstSeen: '2026-09-01T08:12:00Z',
    lastSeen: '2026-09-09T17:40:00Z',
    acknowledged: false,
    resolvedIn: null,
  },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminBeacon.title')"
               :subtitle="t('helpCenter.adminBeacon.subtitle')">
    <HelpSection :title="t('helpCenter.adminBeacon.whatIs')">
      <p>{{ t('helpCenter.adminBeacon.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminBeacon.sending')">
      <p>{{ t('helpCenter.adminBeacon.sendingText') }}</p>
      <p>{{ t('helpCenter.adminBeacon.sendingPreview') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminBeacon.numbers')">
      <p>{{ t('helpCenter.adminBeacon.numbersText') }}</p>
      <p>{{ t('helpCenter.adminBeacon.numbersWhen') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminBeacon.receiving')">
      <p>{{ t('helpCenter.adminBeacon.receivingText') }}</p>
      <BeaconFaultCard :expanded="false" :fault="FAULTS[0]!"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminBeacon.copies')">
      <p>{{ t('helpCenter.adminBeacon.copiesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminBeacon.tip') }}</HelpTip>
  </HelpArticle>
</template>
