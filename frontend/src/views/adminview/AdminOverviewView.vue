/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import client from '@/api/client'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {formatDateTime} from '@/util/format'
import AttentionCard from './adminoverviewview/AttentionCard.vue'

interface RecentApplication {
  id: number
  name: string
  station_name: string
  created_at: string
}

interface RecentProblemReport {
  id: number
  reporter_name: string
  page_url?: string | null
  created_at: string
}

interface AdminOverview {
  emailFailed: number
  emailPending: number
  emailStuckSending: number
  stationApplicationsPending: number
  stationsSetupPending: number
  accountsUnverified: number
  federationPartnersPending: number
  discoveryPeersUnreachable: number
  problemReportsOpen: number
  recentApplications: RecentApplication[]
  recentProblemReports: RecentProblemReport[]
}

const {t} = useI18n()

const {config: overview, loading, error} = useConfigPanel<AdminOverview | null>({
  initial: null,
  fetch: async () => (await client.get<AdminOverview>('/admin/overview')).data,
})

const totalAttention = computed(() => {
  if (!overview.value) return 0
  return overview.value.emailFailed
      + overview.value.emailStuckSending
      + overview.value.stationApplicationsPending
      + overview.value.federationPartnersPending
      + overview.value.discoveryPeersUnreachable
      + overview.value.problemReportsOpen
})
</script>

<template>
  <ViewContent :title="t('pages.admin-overview.title')" :subtitle="t('pages.admin-overview.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && overview">
        <SuccessContainer v-if="totalAttention === 0" class="text-center">
          <font-awesome-icon :icon="['fas', 'check']" class="text-2xl mb-1"/>
          <p class="font-medium">{{ t('adminOverview.allClear') }}</p>
          <p class="text-sm text-(--text-muted)">{{ t('adminOverview.allClearHint') }}</p>
        </SuccessContainer>

        <div>
          <SubHeader>{{ t('adminOverview.attentionSection') }}</SubHeader>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-3">
            <AttentionCard
                :icon="['fas', 'envelope']"
                :label="t('adminOverview.cards.emailFailed')"
                :count="overview.emailFailed"
                :crit-at="1"
                route-name="admin-mailing"
            />
            <AttentionCard
                :icon="['fas', 'hourglass-half']"
                :label="t('adminOverview.cards.emailStuck')"
                :count="overview.emailStuckSending"
                :crit-at="1"
                route-name="admin-mailing"
            />
            <AttentionCard
                :icon="['fas', 'inbox']"
                :label="t('adminOverview.cards.emailPending')"
                :count="overview.emailPending"
                :warn-at="50"
                :crit-at="500"
                route-name="admin-mailing"
            />
            <AttentionCard
                :icon="['fas', 'clipboard-check']"
                :label="t('adminOverview.cards.applicationsPending')"
                :count="overview.stationApplicationsPending"
                :warn-at="1"
                route-name="admin-station-applications"
            />
            <AttentionCard
                :icon="['fas', 'building']"
                :label="t('adminOverview.cards.stationsSetupPending')"
                :count="overview.stationsSetupPending"
                :warn-at="1"
                route-name="admin-stations"
            />
            <AttentionCard
                :icon="['fas', 'user-clock']"
                :label="t('adminOverview.cards.accountsUnverified')"
                :count="overview.accountsUnverified"
                :warn-at="5"
                :crit-at="50"
            />
            <AttentionCard
                :icon="['fas', 'handshake']"
                :label="t('adminOverview.cards.federationPending')"
                :count="overview.federationPartnersPending"
                :warn-at="1"
            />
            <AttentionCard
                :icon="['fas', 'satellite-dish']"
                :label="t('adminOverview.cards.discoveryUnreachable')"
                :count="overview.discoveryPeersUnreachable"
                :warn-at="1"
                route-name="admin-discovery"
            />
            <AttentionCard
                :icon="['fas', 'triangle-exclamation']"
                :label="t('adminOverview.cards.problemReports')"
                :count="overview.problemReportsOpen"
                :warn-at="1"
                :crit-at="10"
                route-name="admin-problem-reports"
            />
          </div>
        </div>

        <div>
          <SubHeader>{{ t('adminOverview.recentApplicationsSection') }}</SubHeader>
          <EmptyState v-if="overview.recentApplications.length === 0" compact class="mt-3">
            {{ t('adminOverview.noApplications') }}
          </EmptyState>
          <ul v-else class="space-y-2 mt-3">
            <li v-for="app in overview.recentApplications" :key="app.id">
              <NeutralContainer>
                <div class="flex items-center justify-between gap-2 flex-wrap">
                  <div>
                    <p class="font-medium">{{ app.station_name }}</p>
                    <p class="text-xs text-(--text-muted)">{{ app.name }}</p>
                  </div>
                  <span class="text-xs text-(--text-muted)">{{ formatDateTime(app.created_at) }}</span>
                </div>
              </NeutralContainer>
            </li>
          </ul>
        </div>

        <div>
          <SubHeader>{{ t('adminOverview.recentReportsSection') }}</SubHeader>
          <EmptyState v-if="overview.recentProblemReports.length === 0" compact class="mt-3">
            {{ t('adminOverview.noReports') }}
          </EmptyState>
          <ul v-else class="space-y-2 mt-3">
            <li v-for="report in overview.recentProblemReports" :key="report.id">
              <NeutralContainer>
                <div class="flex items-center justify-between gap-2 flex-wrap">
                  <div class="min-w-0">
                    <p class="font-medium">{{ report.reporter_name }}</p>
                    <p v-if="report.page_url" class="text-xs text-(--text-muted) truncate">{{ report.page_url }}</p>
                  </div>
                  <span class="text-xs text-(--text-muted)">{{ formatDateTime(report.created_at) }}</span>
                </div>
              </NeutralContainer>
            </li>
          </ul>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
