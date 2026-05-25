/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t, te} = useI18n()
const route = useRoute()

const pageTitle = computed(() => {
  const name = (route.name as string)?.replace('help-', '') ?? ''
  const key = `pages.${name}.title`
  return te(key) ? `${t(key)} — ${t('helpCenter.link')}` : t('helpCenter.adminHelp')
})
</script>

<template>
  <SidebarLayout :subtitle="t('helpCenter.title')" :title="pageTitle" :station-name="t('helpCenter.adminHelp')">
    <template #sidebar="{ close }">
      <SidebarGroup :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')" prefix="/helpcenter/admin/dashboard">
        <SidebarLink :icon="['fas', 'house']" name="help-admin-module-overview"
                     to="/helpcenter/admin/dashboard/overview" @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="help-admin-statistics"
                     to="/helpcenter/admin/dashboard/statistics" @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'building']" :label="t('sidebar.stations')" prefix="/helpcenter/admin/stations"
                    to="/helpcenter/admin/stations/overview" name="help-admin-stations-module-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'building']" name="help-admin-stations"
                     to="/helpcenter/admin/stations" @navigate="close">
          {{ t('sidebar.manageStations') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-admin-station-applications"
                     to="/helpcenter/admin/stations/applications" @navigate="close">
          {{ t('sidebar.applications') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'gear']" name="help-admin-settings"
                     to="/helpcenter/admin/settings" @navigate="close">
          {{ t('sidebar.settings') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="help-admin-api-status"
                     to="/helpcenter/admin/api-status" @navigate="close">
          {{ t('helpCenter.adminApiStatus.sidebarLabel') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'triangle-exclamation']" name="help-admin-problems"
                     to="/helpcenter/admin/problems" @navigate="close">
          {{ t('helpCenter.adminProblems.sidebarLabel') }}
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

    <RouterView/>
  </SidebarLayout>
</template>
