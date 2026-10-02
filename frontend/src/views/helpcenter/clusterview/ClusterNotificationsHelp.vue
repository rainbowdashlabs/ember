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
import NotificationInbox from '@/components/notifications/NotificationInbox.vue'
import ClusterMailSwitch from '@/views/clusterview/clusternotificationsview/ClusterMailSwitch.vue'
import type {NotificationInboxApi} from '@/api/notifications'

const {t} = useI18n()

/** An inbox holding one application, which reads and clears like the real one and tells nobody. */
const demoInbox: NotificationInboxApi = {
  listUnread: async () => [{
    id: 1,
    type: 'CLUSTER_APPLICATION_SUBMITTED',
    localeKey: 'notification.clusterApplicationSubmitted',
    params: {stationName: 'Wache Süd'},
    link: {route: 'cluster-applications', routeParams: {}, query: null},
    createdAt: '2026-10-01T08:00:00Z',
    acknowledgedAt: null,
  }],
  count: async () => 1,
  acknowledge: async () => {},
  acknowledgeAll: async () => {},
}
</script>

<template>
  <HelpArticle :subtitle="t('helpCenter.clusterNotifications.subtitle')" :title="t('helpCenter.clusterNotifications.title')">
    <HelpSection :title="t('helpCenter.clusterNotifications.whatIs')">
      <p>{{ t('helpCenter.clusterNotifications.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterNotifications.readTitle')">
      <p>{{ t('helpCenter.clusterNotifications.readText') }}</p>
      <NotificationInbox :api="demoInbox"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterNotifications.howTo')">
      <p>{{ t('helpCenter.clusterNotifications.howToText') }}</p>
      <ClusterMailSwitch :settings="{emailEnabled: true, mailAvailable: true}"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterNotifications.whenTitle')">
      <p>{{ t('helpCenter.clusterNotifications.whenText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterNotifications.tip') }}</HelpTip>
  </HelpArticle>
</template>
