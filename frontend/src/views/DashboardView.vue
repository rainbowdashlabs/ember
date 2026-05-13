/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import StationSwitcher from '@/components/navigation/StationSwitcher.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {auth, notifications, events} from '@/api'
import client from '@/api/client'
import Alert from '@/components/feedback/Alert.vue'
import {getItem} from '@/api/storage'
import {Roles} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'
import {usePendingChanges} from '@/composables/usePendingChanges'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()
const {
  loaded,
  load,
  isAdmin,
  isManager,
  hasRole,
  canManageMembers,
  canManageInventory,
  canManageAttendance,
  canExportAttendance,
  canManageEvents,
  isMemberManager,
  fullName,
  clear
} = useSession()
const {activeStation, activeLogoUrl} = useStations()
const {pendingChangesCount, refresh: refreshPendingChanges} = usePendingChanges()

const isDemo = ref(false)
const notificationCount = ref(0)
const pendingRegistrationCount = ref(0)

async function refreshNotificationCount() {
  try {
    notificationCount.value = await notifications.getCount()
  } catch { /* ignore */ }
}

async function refreshPendingRegistrationCount() {
  try {
    const pending = await events.listPendingRegistrations()
    pendingRegistrationCount.value = pending.length
  } catch { /* ignore */ }
}

onMounted(async () => {
  if (!loaded.value) {
    load()
  }
  try {
    const res = await client.get<{ demo: boolean }>('/demo/status')
    isDemo.value = res.data.demo
  } catch { /* ignore */
  }
})

watch(loaded, (isLoaded) => {
  if (isLoaded && (canManageMembers() || isMemberManager())) refreshPendingChanges()
  if (isLoaded) refreshNotificationCount()
  if (isLoaded && canManageEvents()) refreshPendingRegistrationCount()
}, {immediate: true})

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
  await router.push({name: 'login'})
}
</script>

<template>
  <SidebarLayout :station-logo-url="activeLogoUrl" :station-name="activeStation?.stationName" :subtitle="pageSubtitle"
                 :title="pageTitle">
    <template #sidebar="{ close }">
      <SidebarGroup :badge="notificationCount" :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')" prefix="/station/dashboard">
        <SidebarLink :badge="notificationCount" :icon="['fas', 'house']" name="dashboard-overview" to="/station/dashboard/overview"
                     @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink v-if="hasRole(Roles.TEAM)" :icon="['fas', 'chart-line']" name="dashboard-statistics"
                     to="/station/dashboard/statistics" @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'newspaper']" :label="t('sidebar.news')" prefix="/station/news">
        <SidebarLink :icon="['fas', 'newspaper']" name="news-list" to="/station/news" @navigate="close">
          {{ t('sidebar.newsList') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'user']" :label="t('sidebar.profile')" prefix="/station/profile">
        <SidebarLink :icon="['fas', 'user']" name="profile" to="/station/profile" @navigate="close">
          {{ t('sidebar.myProfile') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'calendar-days']" name="profile-absences" to="/station/profile/absences"
                     @navigate="close">
          {{ t('sidebar.absences') }}
        </SidebarLink>
        <SidebarLink v-if="isMemberManager()" :icon="['fas', 'users']" name="profile-managed"
                     to="/station/profile/managed" @navigate="close">
          {{ t('sidebar.managedProfiles') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'gear']" name="profile-settings" to="/station/profile/settings" @navigate="close">
          {{ t('sidebar.settings') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup v-if="isManager()" :icon="['fas', 'gears']" :label="t('sidebar.station')" prefix="/station/manage">
        <SidebarLink :icon="['fas', 'gears']" name="station-manage" to="/station/manage" @navigate="close">
          {{ t('sidebar.manage') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-check']" name="station-attendance-config"
                     to="/station/manage/attendance-config" @navigate="close">
          {{ t('sidebar.attendanceConfig') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'users-gear']" name="station-members-config" to="/station/manage/members-config"
                     @navigate="close">
          {{ t('sidebar.membersConfig') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup v-if="canManageMembers()" :badge="pendingChangesCount" :icon="['fas', 'users']"
                    :label="t('sidebar.members')" prefix="/station/members">
        <SidebarLink :icon="['fas', 'user-plus']" name="members-create" to="/station/members/create" @navigate="close">
          {{ t('sidebar.create') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'list']" name="members-list" to="/station/members/list" @navigate="close">
          {{ t('sidebar.list') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'layer-group']" name="members-groups" to="/station/members/groups"
                     @navigate="close">
          {{ t('sidebar.groups') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'hashtag']" name="members-tags" to="/station/members/tags"
                     @navigate="close">
          {{ t('sidebar.tags') }}
        </SidebarLink>
        <SidebarLink :badge="pendingChangesCount" :icon="['fas', 'bell']" name="members-changes"
                     to="/station/members/changes" @navigate="close">
          {{ t('sidebar.changes') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'user-slash']" name="members-former" to="/station/members/former"
                     @navigate="close">
          {{ t('sidebar.formerMembers') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'boxes-stacked']" :label="t('sidebar.inventory')" prefix="/station/inventory">
        <SidebarLink v-if="canManageInventory()" :icon="['fas', 'house']" name="inventory-overview" to="/station/inventory/overview"
                     @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'boxes-stacked']" name="inventory-my" to="/station/inventory/my"
                     @navigate="close">
          {{ t('sidebar.myInventory') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'rotate']" name="inventory-exchanges" to="/station/inventory/exchanges"
                     @navigate="close">
          {{ t('sidebar.inventoryExchanges') }}
        </SidebarLink>
        <SidebarLink v-if="canManageInventory()" :icon="['fas', 'users']" name="inventory-members" to="/station/inventory/members"
                     @navigate="close">
          {{ t('sidebar.inventoryMembers') }}
        </SidebarLink>
        <SidebarLink v-if="canManageInventory()" :icon="['fas', 'box-open']" name="inventory-manage" to="/station/inventory/manage"
                     @navigate="close">
          {{ t('sidebar.inventoryManage') }}
        </SidebarLink>
        <SidebarLink v-if="canManageInventory()" :icon="['fas', 'clipboard-list']" name="inventory-requirements"
                     to="/station/inventory/requirements" @navigate="close">
          {{ t('sidebar.inventoryRequirements') }}
        </SidebarLink>
        <SidebarLink v-if="canManageInventory()" :icon="['fas', 'clipboard-check']" name="inventory-checks" to="/station/inventory/checks"
                     @navigate="close">
          {{ t('sidebar.inventoryCheck') }}
        </SidebarLink>
        <SidebarLink v-if="canManageInventory()" :icon="['fas', 'folder-plus']" name="inventory-procurement" to="/station/inventory/procurement"
                     @navigate="close">
          {{ t('sidebar.inventoryProcurement') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup v-if="canManageAttendance()" :icon="['fas', 'clipboard-user']" :label="t('sidebar.attendance')"
                    prefix="/station/attendance">
        <SidebarLink :icon="['fas', 'calendar-plus']" name="attendance-new" to="/station/attendance/new"
                     @navigate="close">
          {{ t('sidebar.newAttendance') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clock-rotate-left']" name="attendance-past" to="/station/attendance/past"
                     @navigate="close">
          {{ t('sidebar.pastAttendance') }}
        </SidebarLink>
        <SidebarLink v-if="canExportAttendance()" :icon="['fas', 'chart-line']" name="attendance-report"
                     to="/station/attendance/report" @navigate="close">
          {{ t('sidebar.attendanceReport') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :badge="pendingRegistrationCount" :icon="['fas', 'calendar-days']" :label="t('sidebar.events')" prefix="/station/events">
        <SidebarLink :icon="['fas', 'calendar-plus']" name="events-upcoming" to="/station/events/upcoming"
                     @navigate="close">
          {{ t('sidebar.upcomingEvents') }}
        </SidebarLink>
        <SidebarLink v-if="canManageEvents()" :badge="pendingRegistrationCount" :icon="['fas', 'clipboard-list']" name="events-registrations"
                     to="/station/events/registrations" @navigate="close">
          {{ t('sidebar.pendingRegistrations') }}
        </SidebarLink>
        <SidebarLink v-if="canManageEvents()" :icon="['fas', 'gears']" name="events" to="/station/events"
                     @navigate="close">
          {{ t('sidebar.manageEvents') }}
        </SidebarLink>
      </SidebarGroup>

    </template>

    <template #header>
      <HelpCenterLink/>

      <router-link v-if="isAdmin()" to="/admin/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('header.adminPanel') }}</span>
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

    <template #footer>
      <StationSwitcher/>
    </template>

    <Alert v-if="isDemo" class="mb-4" variant="info">
      {{ t('demo.banner') }}
    </Alert>
    <RouterView/>
  </SidebarLayout>
</template>
