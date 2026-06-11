/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {auth} from '@/api'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'
import {useTheme} from '@/composables/useTheme'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, load, fullName, clear} = useSession()

onMounted(() => {
  if (!loaded.value) {
    load()
  }
})

const pageTitle = computed(() => {
  const key = `pages.${route.name as string}.title`
  return te(key) ? t(key) : ''
})

const pageSubtitle = computed(() => {
  const key = `pages.${route.name as string}.subtitle`
  return te(key) ? t(key) : ''
})

async function handleLogout() {
  const token = getItem('session_token')
  if (token) {
    try {
      await auth.logout({token})
    } catch {
      // ignore
    }
  }
  clear()
  useTheme().resetToInstanceDefaults()
  await router.push({name: 'login'})
}
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
        <SidebarLink :icon="['fas', 'scale-balanced']" name="admin-legal" to="/admin/settings/legal" @navigate="close">
          {{ t('sidebar.legal') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'triangle-exclamation']" :label="t('sidebar.monitoring')" prefix="/admin" group-key="monitoring">
        <SidebarLink :icon="['fas', 'hard-drive']" name="admin-storage" to="/admin/storage" @navigate="close">
          {{ t('sidebar.storageDashboard') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'bug']" name="admin-problems" to="/admin/problems" @navigate="close">
          {{ t('sidebar.problemLog') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'flag']" name="admin-problem-reports" to="/admin/problem-reports" @navigate="close">
          {{ t('sidebar.problemReports') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="admin-api-status" to="/admin/api-status" @navigate="close">
          {{ t('sidebar.apiStatus') }}
        </SidebarLink>
      </SidebarGroup>
    </template>

    <template #header>
      <HelpCenterLink/>

      <router-link to="/station/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'building']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('header.stationPanel') }}</span>
        </SecondaryButton>
      </router-link>

      <span class="text-sm text-[var(--text-muted)] hidden sm:inline">{{ fullName() }}</span>

      <IconButton
          :icon="['fas', 'right-from-bracket']"
          :label="t('header.logout')"
          class="text-[var(--text-muted)] hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
          @click="handleLogout"
      />
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
