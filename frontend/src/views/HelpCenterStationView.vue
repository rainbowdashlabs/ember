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
  const helpKey = `helpCenter.pages.${route.name as string}.title`
  if (te(helpKey)) return t(helpKey)
  return te(key) ? `${t(key)} — ${t('helpCenter.link')}` : t('helpCenter.link')
})

const pageSubtitle = computed(() => t('helpCenter.title'))
</script>

<template>
  <SidebarLayout :subtitle="pageSubtitle" :title="pageTitle" :station-name="t('helpCenter.title')">
    <template #sidebar="{ close }">
      <SidebarGroup :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')" prefix="/helpcenter/station/dashboard">
        <SidebarLink :icon="['fas', 'house']" name="help-dashboard-overview"
                     to="/helpcenter/station/dashboard/overview" @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="help-dashboard-statistics"
                     to="/helpcenter/station/dashboard/statistics" @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'newspaper']" :label="t('sidebar.news')" prefix="/helpcenter/station/news">
        <SidebarLink :icon="['fas', 'newspaper']" name="help-news-list" to="/helpcenter/station/news" @navigate="close">
          {{ t('sidebar.newsList') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'user']" :label="t('sidebar.profile')" prefix="/helpcenter/station/profile">
        <SidebarLink :icon="['fas', 'user']" name="help-profile" to="/helpcenter/station/profile" @navigate="close">
          {{ t('sidebar.myProfile') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'calendar-days']" name="help-profile-absences"
                     to="/helpcenter/station/profile/absences" @navigate="close">
          {{ t('sidebar.absences') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'users']" name="help-profile-managed"
                     to="/helpcenter/station/profile/managed" @navigate="close">
          {{ t('sidebar.managedProfiles') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'gear']" name="help-profile-settings"
                     to="/helpcenter/station/profile/settings" @navigate="close">
          {{ t('sidebar.settings') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'gears']" :label="t('sidebar.station')" prefix="/helpcenter/station/manage">
        <SidebarLink :icon="['fas', 'gears']" name="help-station-manage" to="/helpcenter/station/manage"
                     @navigate="close">
          {{ t('sidebar.manage') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-check']" name="help-station-attendance-config"
                     to="/helpcenter/station/manage/attendance-config" @navigate="close">
          {{ t('sidebar.attendanceConfig') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'users-gear']" name="help-station-members-config"
                     to="/helpcenter/station/manage/members-config" @navigate="close">
          {{ t('sidebar.membersConfig') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'users']" :label="t('sidebar.members')" prefix="/helpcenter/station/members">
        <SidebarLink :icon="['fas', 'user-plus']" name="help-members-create"
                     to="/helpcenter/station/members/create" @navigate="close">
          {{ t('sidebar.create') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'list']" name="help-members-list" to="/helpcenter/station/members/list"
                     @navigate="close">
          {{ t('sidebar.list') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'layer-group']" name="help-members-groups"
                     to="/helpcenter/station/members/groups" @navigate="close">
          {{ t('sidebar.groups') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'hashtag']" name="help-members-tags" to="/helpcenter/station/members/tags"
                     @navigate="close">
          {{ t('sidebar.tags') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'bell']" name="help-members-changes"
                     to="/helpcenter/station/members/changes" @navigate="close">
          {{ t('sidebar.changes') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'user-slash']" name="help-members-former"
                     to="/helpcenter/station/members/former" @navigate="close">
          {{ t('sidebar.formerMembers') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'boxes-stacked']" :label="t('sidebar.inventory')"
                    prefix="/helpcenter/station/inventory">
        <SidebarLink :icon="['fas', 'house']" name="help-inventory-overview"
                     to="/helpcenter/station/inventory/overview" @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'boxes-stacked']" name="help-inventory-my"
                     to="/helpcenter/station/inventory/my" @navigate="close">
          {{ t('sidebar.myInventory') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'rotate']" name="help-inventory-exchanges"
                     to="/helpcenter/station/inventory/exchanges" @navigate="close">
          {{ t('sidebar.inventoryExchanges') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'users']" name="help-inventory-members"
                     to="/helpcenter/station/inventory/members" @navigate="close">
          {{ t('sidebar.inventoryMembers') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'box-open']" name="help-inventory-manage"
                     to="/helpcenter/station/inventory/manage" @navigate="close">
          {{ t('sidebar.inventoryManage') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-inventory-requirements"
                     to="/helpcenter/station/inventory/requirements" @navigate="close">
          {{ t('sidebar.inventoryRequirements') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-check']" name="help-inventory-checks"
                     to="/helpcenter/station/inventory/checks" @navigate="close">
          {{ t('sidebar.inventoryCheck') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'folder-plus']" name="help-inventory-procurement"
                     to="/helpcenter/station/inventory/procurement" @navigate="close">
          {{ t('sidebar.inventoryProcurement') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'clipboard-user']" :label="t('sidebar.attendance')"
                    prefix="/helpcenter/station/attendance">
        <SidebarLink :icon="['fas', 'calendar-plus']" name="help-attendance-new"
                     to="/helpcenter/station/attendance/new" @navigate="close">
          {{ t('sidebar.newAttendance') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clock-rotate-left']" name="help-attendance-past"
                     to="/helpcenter/station/attendance/past" @navigate="close">
          {{ t('sidebar.pastAttendance') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="help-attendance-report"
                     to="/helpcenter/station/attendance/report" @navigate="close">
          {{ t('sidebar.attendanceReport') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'calendar-days']" :label="t('sidebar.events')"
                    prefix="/helpcenter/station/events">
        <SidebarLink :icon="['fas', 'calendar-plus']" name="help-events-upcoming"
                     to="/helpcenter/station/events/upcoming" @navigate="close">
          {{ t('sidebar.upcomingEvents') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-events-registrations"
                     to="/helpcenter/station/events/registrations" @navigate="close">
          {{ t('sidebar.pendingRegistrations') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'gears']" name="help-events"
                     to="/helpcenter/station/events" @navigate="close">
          {{ t('sidebar.manageEvents') }}
        </SidebarLink>
      </SidebarGroup>
    </template>

    <template #header>
      <router-link to="/helpcenter/admin/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('helpCenter.adminHelp') }}</span>
        </SecondaryButton>
      </router-link>
    </template>

    <RouterView/>
  </SidebarLayout>
</template>
