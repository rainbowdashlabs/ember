/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {claimPageHeader} from '@/composables/usePageHeader'

const {t, te} = useI18n()
const route = useRoute()

const pageTitle = computed(() => {
  const name = (route.name as string)?.replace('help-', '') ?? ''
  const key = `pages.${name}.title`
  return te(key) ? `${t(key)} - ${t('helpCenter.link')}` : t('helpCenter.adminHelp')
})

const {set: setPageHeader} = claimPageHeader()
watch(
    () => [pageTitle.value, t('helpCenter.title')] as const,
    ([titleValue, subtitleValue]) => setPageHeader(titleValue, subtitleValue),
    {immediate: true},
)
</script>

<template>
  <SidebarLayout :subtitle="t('helpCenter.title')" :title="pageTitle" :station-name="t('helpCenter.adminHelp')" :collapsible="false">
    <template #sidebar="{ close }">
      <SidebarGroup :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')">
        <SidebarLink :icon="['fas', 'house']" name="help-admin-module-overview"
                     to="/helpcenter/admin/dashboard/overview" @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="help-admin-statistics"
                     to="/helpcenter/admin/dashboard/statistics" @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'building']" :label="t('sidebar.stations')"
                    to="/helpcenter/admin/stations/overview" name="help-admin-stations-module-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'building']" name="help-admin-stations"
                     to="/helpcenter/admin/stations" @navigate="close">
          {{ t('sidebar.manageStations') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-admin-station-applications"
                     to="/helpcenter/admin/stations/applications" @navigate="close">
          {{ t('sidebar.applications') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'sitemap']" :label="t('sidebar.clusters')">
        <SidebarLink :icon="['fas', 'sitemap']" name="help-admin-clusters"
                     to="/helpcenter/admin/clusters" @navigate="close">
          {{ t('sidebar.manageClusters') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'gear']" :label="t('sidebar.settings')">
        <SidebarLink :icon="['fas', 'sliders']" name="help-admin-settings"
                     to="/helpcenter/admin/settings" @navigate="close">
          {{ t('sidebar.general') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'envelope']" name="help-admin-mailing"
                     to="/helpcenter/admin/settings/mailing" @navigate="close">
          {{ t('sidebar.mailing') }}
        </SidebarLink>
        <SidebarGroup :icon="['fas', 'shield']" :label="t('sidebar.security')" to="/helpcenter/admin/settings/security"
                      name="help-admin-security" @navigate="close">
          <SidebarLink :icon="['fas', 'key']" name="help-admin-security-tokens"
                       to="/helpcenter/admin/settings/security/tokens" @navigate="close">
            {{ t('sidebar.securityTokens') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'user-shield']" name="help-admin-security-hibp"
                       to="/helpcenter/admin/settings/security/hibp" @navigate="close">
            {{ t('sidebar.securityHibp') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'mobile-screen']" name="help-admin-security-two-factor"
                       to="/helpcenter/admin/settings/security/two-factor" @navigate="close">
            {{ t('sidebar.securityTwoFactor') }}
          </SidebarLink>
        </SidebarGroup>
        <SidebarLink :icon="['fas', 'scale-balanced']" name="help-admin-legal"
                     to="/helpcenter/admin/settings/legal" @navigate="close">
          {{ t('sidebar.legal') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'mobile-screen']" :label="t('sidebar.twoFactor')"
                    to="/helpcenter/admin/2fa" name="help-admin-two-factor" @navigate="close">
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'triangle-exclamation']" :label="t('sidebar.monitoring')" prefix="/helpcenter/admin/monitoring" group-key="monitoring">
        <SidebarLink :icon="['fas', 'hard-drive']" name="help-admin-storage"
                     to="/helpcenter/admin/monitoring/storage" @navigate="close">
          {{ t('sidebar.storageDashboard') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'bug']" name="help-admin-problems"
                     to="/helpcenter/admin/monitoring/problems" @navigate="close">
          {{ t('sidebar.problemLog') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'flag']" name="help-admin-problem-reports"
                     to="/helpcenter/admin/monitoring/problem-reports" @navigate="close">
          {{ t('sidebar.problemReports') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="help-admin-api-status"
                     to="/helpcenter/admin/monitoring/api-status" @navigate="close">
          {{ t('sidebar.apiStatus') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'rss']" name="help-admin-feed-metrics"
                     to="/helpcenter/admin/monitoring/feed-metrics" @navigate="close">
          {{ t('sidebar.feedMetrics') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'tower-broadcast']" name="help-admin-traffic"
                     to="/helpcenter/admin/monitoring/traffic" @navigate="close">
          {{ t('sidebar.adminTraffic') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'compass']" name="help-admin-discovery"
                     to="/helpcenter/admin/monitoring/discovery" @navigate="close">
          {{ t('sidebar.adminDiscovery') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'map-location-dot']" name="help-admin-maps"
                     to="/helpcenter/admin/monitoring/maps" @navigate="close">
          {{ t('sidebar.maps') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'code']" :label="t('sidebar.devTools')"
                    group-key="dev">
        <SidebarLink :icon="['fas', 'database']" name="help-admin-data-tracking"
                     to="/helpcenter/admin/dev/data-tracking" @navigate="close">
          {{ t('sidebar.dataTracking') }}
        </SidebarLink>
      </SidebarGroup>
    </template>

    <template #header>
      <router-link to="/helpcenter/station/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'building']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('helpCenter.stationHelp') }}</span>
        </SecondaryButton>
      </router-link>
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
