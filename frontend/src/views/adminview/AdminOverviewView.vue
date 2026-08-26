/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import OnboardingTaskCard from '@/components/onboarding/OnboardingTaskCard.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import client from '@/api/client'
import {useConfigPanel} from '@/composables/useConfigPanel'
import AttentionGrid from './adminoverviewview/AttentionGrid.vue'
import RecentActivitySection from './adminoverviewview/RecentActivitySection.vue'
import type {AdminOverview, RecentEntry} from './adminoverviewview/types'

const {t} = useI18n()

const {config: overview, loading, error} = useConfigPanel<AdminOverview | null>({
  initial: null,
  fetch: async () => (await client.get<AdminOverview>('/admin/overview')).data,
})

const recentApplications = computed<RecentEntry[]>(() => (overview.value?.recentApplications ?? []).map(app => ({
  id: app.id,
  title: app.station_name,
  detail: app.name,
  createdAt: app.created_at,
})))

const recentReports = computed<RecentEntry[]>(() => (overview.value?.recentProblemReports ?? []).map(report => ({
  id: report.id,
  title: report.reporter_name,
  detail: report.page_url,
  createdAt: report.created_at,
})))

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
      <OnboardingTaskCard level="INSTANCE"/>
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && overview">
        <SuccessContainer v-if="totalAttention === 0" class="text-center">
          <font-awesome-icon :icon="['fas', 'check']" class="text-2xl mb-1"/>
          <p class="font-medium">{{ t('adminOverview.allClear') }}</p>
          <p class="text-sm text-(--text-muted)">{{ t('adminOverview.allClearHint') }}</p>
        </SuccessContainer>

        <AttentionGrid :overview="overview"/>

        <RecentActivitySection
            :title="t('adminOverview.recentApplicationsSection')"
            :empty-text="t('adminOverview.noApplications')"
            :entries="recentApplications"
        />

        <RecentActivitySection
            :title="t('adminOverview.recentReportsSection')"
            :empty-text="t('adminOverview.noReports')"
            :entries="recentReports"
            truncate-detail
        />
      </template>
    </div>
  </ViewContent>
</template>
