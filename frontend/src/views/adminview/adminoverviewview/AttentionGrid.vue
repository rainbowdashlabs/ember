/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import AttentionCard from './AttentionCard.vue'
import type {AdminOverview, AttentionCardSpec} from './types'

const props = defineProps<{
  overview: AdminOverview
}>()

const {t} = useI18n()

const cards = computed<AttentionCardSpec[]>(() => [
  {key: 'emailFailed', icon: ['fas', 'envelope'], label: t('adminOverview.cards.emailFailed'), count: props.overview.emailFailed, critAt: 1, routeName: 'admin-mailing'},
  {key: 'emailStuck', icon: ['fas', 'hourglass-half'], label: t('adminOverview.cards.emailStuck'), count: props.overview.emailStuckSending, critAt: 1, routeName: 'admin-mailing'},
  {key: 'emailPending', icon: ['fas', 'inbox'], label: t('adminOverview.cards.emailPending'), count: props.overview.emailPending, warnAt: 50, critAt: 500, routeName: 'admin-mailing'},
  {key: 'applicationsPending', icon: ['fas', 'clipboard-check'], label: t('adminOverview.cards.applicationsPending'), count: props.overview.stationApplicationsPending, warnAt: 1, routeName: 'admin-station-applications'},
  {key: 'stationsSetupPending', icon: ['fas', 'building'], label: t('adminOverview.cards.stationsSetupPending'), count: props.overview.stationsSetupPending, warnAt: 1, routeName: 'admin-stations'},
  {key: 'accountsUnverified', icon: ['fas', 'user-clock'], label: t('adminOverview.cards.accountsUnverified'), count: props.overview.accountsUnverified, warnAt: 5, critAt: 50},
  {key: 'federationPending', icon: ['fas', 'handshake'], label: t('adminOverview.cards.federationPending'), count: props.overview.federationPartnersPending, warnAt: 1},
  {key: 'discoveryUnreachable', icon: ['fas', 'satellite-dish'], label: t('adminOverview.cards.discoveryUnreachable'), count: props.overview.discoveryPeersUnreachable, warnAt: 1, routeName: 'admin-discovery'},
  {key: 'problemReports', icon: ['fas', 'triangle-exclamation'], label: t('adminOverview.cards.problemReports'), count: props.overview.problemReportsOpen, warnAt: 1, critAt: 10, routeName: 'admin-problem-reports'},
])
</script>

<template>
  <div>
    <SubHeader>{{ t('adminOverview.attentionSection') }}</SubHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-3">
      <AttentionCard
          v-for="card in cards"
          :key="card.key"
          :icon="card.icon"
          :label="card.label"
          :count="card.count"
          :warn-at="card.warnAt"
          :crit-at="card.critAt"
          :route-name="card.routeName"
      />
    </div>
  </div>
</template>
