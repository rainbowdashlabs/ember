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
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'
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
      <SidebarGroup :icon="['fas', 'book']" :label="t('helpCenter.basics.sidebar')" prefix="/helpcenter/station/basics"
                    to="/helpcenter/station/basics" name="help-welcome" @navigate="close">
        <SidebarLink :icon="['fas', 'circle-info']" name="help-basics-overview"
                     to="/helpcenter/station/basics/overview" @navigate="close">
          {{ t('helpCenter.basics.sidebarOverview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'shield']" name="help-basics-roles"
                     to="/helpcenter/station/basics/roles" @navigate="close">
          {{ t('helpCenter.basics.sidebarRoles') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'puzzle-piece']" name="help-basics-modules"
                     to="/helpcenter/station/basics/modules" @navigate="close">
          {{ t('helpCenter.basics.sidebarModules') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'server']" name="help-basics-hosting"
                     to="/helpcenter/station/basics/hosting" @navigate="close">
          {{ t('helpCenter.basics.sidebarHosting') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')" prefix="/helpcenter/station/dashboard"
                    to="/helpcenter/station/dashboard" name="help-dashboard-module-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'house']" name="help-dashboard-overview"
                     to="/helpcenter/station/dashboard/overview" @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-line']" name="help-dashboard-statistics"
                     to="/helpcenter/station/dashboard/statistics" @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'newspaper']" :label="t('sidebar.news')" prefix="/helpcenter/station/news"
                    to="/helpcenter/station/news" name="help-news-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'newspaper']" name="help-news-list" to="/helpcenter/station/news/list" @navigate="close">
          {{ t('sidebar.newsList') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'pen']" name="help-news-create" to="/helpcenter/station/news/create" @navigate="close">
          {{ t('sidebar.newsEdit') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'user']" :label="t('sidebar.profile')" prefix="/helpcenter/station/profile"
                    to="/helpcenter/station/profile" name="help-profile-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'user']" name="help-profile" to="/helpcenter/station/profile/index" @navigate="close">
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
        <SidebarLink :icon="['fas', 'boxes-stacked']" name="help-profile-inventory"
                     to="/helpcenter/station/profile/inventory" @navigate="close">
          {{ t('sidebar.myInventory') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'gear']" name="help-profile-settings"
                     to="/helpcenter/station/profile/settings" @navigate="close">
          {{ t('sidebar.settings') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'palette']" name="help-profile-theme"
                     to="/helpcenter/station/profile/theme" @navigate="close">
          {{ t('helpCenter.themeUser.sidebarLabel') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'gears']" :label="t('sidebar.station')" prefix="/helpcenter/station/manage"
                    to="/helpcenter/station/manage" name="help-manage-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'gears']" name="help-station-manage" to="/helpcenter/station/manage/station"
                     @navigate="close">
          {{ t('sidebar.manage') }}
        </SidebarLink>
        <SidebarExpandableLink :icon="['fas', 'clipboard-check']" name="help-station-attendance-config"
                               to="/helpcenter/station/manage/attendance-config" @navigate="close">
          <template #label>{{ t('sidebar.attendanceConfig') }}</template>
          <SidebarLink :icon="['fas', 'pen']" name="help-station-attendance-config-edit"
                       to="/helpcenter/station/manage/attendance-config/edit" @navigate="close">
            {{ t('sidebar.attendanceConfigEdit') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'users-gear']" name="help-station-members-config"
                     to="/helpcenter/station/manage/members-config" @navigate="close">
          {{ t('sidebar.membersConfig') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'palette']" name="help-station-theme-manage"
                     to="/helpcenter/station/manage/theme" @navigate="close">
          {{ t('helpCenter.themeManage.sidebarLabel') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'users']" :label="t('sidebar.members')" prefix="/helpcenter/station/members"
                    to="/helpcenter/station/members" name="help-members-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'user-plus']" name="help-members-create"
                     to="/helpcenter/station/members/create" @navigate="close">
          {{ t('sidebar.create') }}
        </SidebarLink>
        <SidebarExpandableLink :icon="['fas', 'upload']" name="help-members-import"
                               to="/helpcenter/station/members/import" @navigate="close">
          <template #label>{{ t('sidebar.membersImport') }}</template>
          <SidebarLink :icon="['fas', 'users']" name="help-members-import-team"
                       to="/helpcenter/station/members/import-team" @navigate="close">
            {{ t('sidebar.membersImportTeam') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'list']" name="help-members-list" to="/helpcenter/station/members/list"
                     @navigate="close">
          {{ t('sidebar.list') }}
        </SidebarLink>
        <SidebarExpandableLink :icon="['fas', 'eye']" name="help-members-detail"
                               to="/helpcenter/station/members/detail" @navigate="close">
          <template #label>{{ t('sidebar.detail') }}</template>
          <SidebarLink :icon="['fas', 'pen']" name="help-members-edit"
                       to="/helpcenter/station/members/edit" @navigate="close">
            {{ t('sidebar.edit') }}
          </SidebarLink>
        </SidebarExpandableLink>
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
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-waiting-lists"
                     to="/helpcenter/station/members/waiting-lists" @navigate="close">
          {{ t('sidebar.waitingLists') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'boxes-stacked']" :label="t('sidebar.inventory')"
                    prefix="/helpcenter/station/inventory"
                    to="/helpcenter/station/inventory" name="help-inventory-module-overview" @navigate="close">
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
        <SidebarExpandableLink :icon="['fas', 'users']" name="help-inventory-members"
                               to="/helpcenter/station/inventory/members" prefix="/helpcenter/station/inventory/member" @navigate="close">
          <template #label>{{ t('sidebar.inventoryMembers') }}</template>
          <SidebarLink :icon="['fas', 'user']" name="help-inventory-member"
                       to="/helpcenter/station/inventory/member" @navigate="close">
            {{ t('sidebar.inventoryMemberDetail') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarExpandableLink :icon="['fas', 'box-open']" name="help-inventory-manage"
                               to="/helpcenter/station/inventory/manage" @navigate="close">
          <template #label>{{ t('sidebar.inventoryManage') }}</template>
          <SidebarLink :icon="['fas', 'eye']" name="help-inventory-detail"
                       to="/helpcenter/station/inventory/detail" @navigate="close">
            {{ t('sidebar.inventoryDetail') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'pen']" name="help-inventory-edit"
                       to="/helpcenter/station/inventory/edit" @navigate="close">
            {{ t('sidebar.inventoryEdit') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="help-inventory-requirements"
                     to="/helpcenter/station/inventory/requirements" @navigate="close">
          {{ t('sidebar.inventoryRequirements') }}
        </SidebarLink>
        <SidebarExpandableLink :icon="['fas', 'clipboard-check']" name="help-inventory-checks"
                               to="/helpcenter/station/inventory/checks" @navigate="close">
          <template #label>{{ t('sidebar.inventoryCheck') }}</template>
          <SidebarLink :icon="['fas', 'user-check']" name="help-inventory-check-member"
                       to="/helpcenter/station/inventory/checks/0" @navigate="close">
            {{ t('sidebar.inventoryCheckMember') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'clipboard-list']" name="help-inventory-check-result"
                       to="/helpcenter/station/inventory/checks/0/result" @navigate="close">
            {{ t('sidebar.inventoryCheckResult') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'folder-plus']" name="help-inventory-procurement"
                     to="/helpcenter/station/inventory/procurement" @navigate="close">
          {{ t('sidebar.inventoryProcurement') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'clipboard-user']" :label="t('sidebar.attendance')"
                    prefix="/helpcenter/station/attendance"
                    to="/helpcenter/station/attendance" name="help-attendance-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'calendar-plus']" name="help-attendance-new"
                     to="/helpcenter/station/attendance/new" @navigate="close">
          {{ t('sidebar.newAttendance') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-user']" name="help-attendance-session"
                     to="/helpcenter/station/attendance/session" @navigate="close">
          {{ t('sidebar.attendanceSession') }}
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
                    prefix="/helpcenter/station/events"
                    to="/helpcenter/station/events/overview" name="help-events-overview" @navigate="close">
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
        <SidebarLink :icon="['fas', 'pen']" name="help-event-new"
                     to="/helpcenter/station/events/new" @navigate="close">
          {{ t('sidebar.eventEdit') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'eye']" name="help-event-detail"
                     to="/helpcenter/station/events/detail" @navigate="close">
          {{ t('sidebar.eventDetail') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'square-poll-vertical']" :label="t('sidebar.forms')"
                    prefix="/helpcenter/station/forms"
                    to="/helpcenter/station/forms" name="help-forms-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'list']" name="help-forms-list"
                     to="/helpcenter/station/forms/list" @navigate="close">
          {{ t('sidebar.formsList') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'plus']" name="help-forms-create"
                     to="/helpcenter/station/forms/create" @navigate="close">
          {{ t('sidebar.formsCreate') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'pen-to-square']" name="help-forms-fill"
                     to="/helpcenter/station/forms/fill" @navigate="close">
          {{ t('sidebar.formsFill') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-bar']" name="help-forms-analytics"
                     to="/helpcenter/station/forms/analytics" @navigate="close">
          {{ t('sidebar.formsAnalytics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'box-open']" :label="t('sidebar.lostAndFound')" prefix="/helpcenter/station/lost-and-found"
                    to="/helpcenter/station/lost-and-found" name="help-lost-and-found" @navigate="close">
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'graduation-cap']" :label="t('sidebar.quiz')" prefix="/helpcenter/station/quiz"
                    to="/helpcenter/station/quiz" name="help-quiz-overview" @navigate="close">
        <SidebarExpandableLink :icon="['fas', 'book']" name="help-quiz-catalogs"
                               to="/helpcenter/station/quiz/catalogs" prefix="/helpcenter/station/quiz/catalog" @navigate="close">
          <template #label>{{ t('sidebar.quizCatalogs') }}</template>
          <SidebarLink :icon="['fas', 'eye']" name="help-quiz-catalog-detail" to="/helpcenter/station/quiz/catalog" @navigate="close">
            {{ t('sidebar.quizCatalogDetail') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'robot']" name="help-quiz-ai" to="/helpcenter/station/quiz/ai" @navigate="close">
            {{ t('sidebar.quizAi') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarExpandableLink :icon="['fas', 'file-lines']" name="help-quiz-tests"
                               to="/helpcenter/station/quiz/tests" prefix="/helpcenter/station/quiz/tests" @navigate="close">
          <template #label>{{ t('sidebar.quizTests') }}</template>
          <SidebarLink :icon="['fas', 'eye']" name="help-quiz-test-detail" to="/helpcenter/station/quiz/tests/details" @navigate="close">
            {{ t('sidebar.quizTestDetail') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'brain']" name="help-quiz-training" to="/helpcenter/station/quiz/training" @navigate="close">
          {{ t('sidebar.quizTraining') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-check']" name="help-protocol" to="/helpcenter/station/protocols" @navigate="close">
          {{ t('sidebar.protocols') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'book-open']" :label="t('sidebar.knowledgeBase')" prefix="/helpcenter/station/knowledge"
                    to="/helpcenter/station/knowledge" name="help-knowledge-base" @navigate="close">
        <SidebarLink :icon="['fas', 'pen']" name="help-knowledge-editor" to="/helpcenter/station/knowledge/editor" @navigate="close">
          {{ t('sidebar.knowledgeEditor') }}
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
