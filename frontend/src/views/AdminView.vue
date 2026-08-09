/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import SmartStationButton from '@/components/layout/SmartStationButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {useSession} from '@/composables/useSession'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import QuickSearchPalette from '@/components/quicksearch/QuickSearchPalette.vue'
import QuickSearchTrigger from '@/components/quicksearch/QuickSearchTrigger.vue'
import {useQuickSearchShortcut} from '@/composables/useQuickSearchShortcut'
import {usePageHeader} from '@/composables/usePageHeader'

const {t} = useI18n()
const {loaded, load} = useSession()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()

// Dev-mode-only inspector tools are only visible when the dev server is running
// (production bundles tree-shake the branch out via Vite's import.meta.env).
const isDev = import.meta.env.DEV

const {open: openQuickSearch} = useQuickSearchShortcut('admin')

onMounted(() => {
  if (!loaded.value) {
    load()
  }
})

</script>

<template>
  <SidebarLayout :subtitle="pageSubtitle" :title="pageTitle">
    <template #sidebar="{ close }">
      <SidebarGroup :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')" prefix="/admin/dashboard">
        <SidebarLink :icon="['fas', 'house']" name="admin-overview" to="/admin/dashboard/overview" @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="admin-statistics" to="/admin/dashboard/statistics"
                     @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'building']" :label="t('sidebar.stations')" prefix="/admin/stations">
        <SidebarLink :icon="['fas', 'building']" name="admin-stations" to="/admin/stations" @navigate="close">
          {{ t('sidebar.manageStations') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="admin-station-applications"
                     to="/admin/stations/applications" @navigate="close">
          {{ t('sidebar.applications') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'gear']" :label="t('sidebar.settings')" prefix="/admin/settings">
        <SidebarLink :icon="['fas', 'sliders']" name="admin-settings" to="/admin/settings" @navigate="close">
          {{ t('sidebar.general') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'envelope']" name="admin-mailing" to="/admin/settings/mailing" @navigate="close">
          {{ t('sidebar.mailing') }}
        </SidebarLink>
        <SidebarGroup :icon="['fas', 'shield']" :label="t('sidebar.security')"
                      prefix="/admin/settings/security" to="/admin/settings/security" name="admin-security"
                      @navigate="close">
          <SidebarLink :icon="['fas', 'key']" name="admin-security-tokens"
                       to="/admin/settings/security/tokens" @navigate="close">
            {{ t('sidebar.securityTokens') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'user-shield']" name="admin-security-hibp"
                       to="/admin/settings/security/hibp" @navigate="close">
            {{ t('sidebar.securityHibp') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'mobile-screen']" name="admin-security-two-factor"
                       to="/admin/settings/security/two-factor" @navigate="close">
            {{ t('sidebar.securityTwoFactor') }}
          </SidebarLink>
        </SidebarGroup>
        <SidebarLink :icon="['fas', 'scale-balanced']" name="admin-legal" to="/admin/settings/legal" @navigate="close">
          {{ t('sidebar.legal') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'mobile-screen']" :label="t('sidebar.twoFactor')" prefix="/admin/2fa"
                    to="/admin/2fa" name="admin-two-factor"
                    @navigate="close">
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'triangle-exclamation']" :label="t('sidebar.monitoring')" prefix="/admin/monitoring" group-key="monitoring">
        <SidebarLink :icon="['fas', 'hard-drive']" name="admin-storage" to="/admin/monitoring/storage" @navigate="close">
          {{ t('sidebar.storageDashboard') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'bug']" name="admin-problems" to="/admin/monitoring/problems" @navigate="close">
          {{ t('sidebar.problemLog') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'flag']" name="admin-problem-reports" to="/admin/monitoring/problem-reports" @navigate="close">
          {{ t('sidebar.problemReports') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="admin-api-status" to="/admin/monitoring/api-status" @navigate="close">
          {{ t('sidebar.apiStatus') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'rss']" name="admin-feed-metrics" to="/admin/monitoring/feed-metrics" @navigate="close">
          {{ t('sidebar.feedMetrics') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'tower-broadcast']" name="admin-traffic" to="/admin/monitoring/traffic" @navigate="close">
          {{ t('sidebar.adminTraffic') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'compass']" name="admin-discovery" to="/admin/monitoring/discovery" @navigate="close">
          {{ t('sidebar.adminDiscovery') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'map-location-dot']" name="admin-maps" to="/admin/monitoring/maps" @navigate="close">
          {{ t('sidebar.maps') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup
          v-if="isDev"
          :icon="['fas', 'code']"
          :label="t('sidebar.devTools')"
          prefix="/admin/dev"
          group-key="dev"
      >
        <SidebarLink
            :icon="['fas', 'database']"
            name="admin-data-tracking"
            to="/admin/dev/data-tracking"
            @navigate="close"
        >
          {{ t('sidebar.dataTracking') }}
        </SidebarLink>
      </SidebarGroup>
    </template>

    <template #header>
      <QuickSearchTrigger scope="admin" @open="openQuickSearch"/>
      <HelpCenterLink/>
      <SmartStationButton/>
      <AccountMenuButton/>
    </template>

    <slot><RouterView/></slot>
    <QuickSearchPalette/>
  </SidebarLayout>
</template>
