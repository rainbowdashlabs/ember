/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import {useHelpSearch} from '@/composables/useHelpSearch'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()
const {query: searchQuery, results: searchResults, isSearching, clearSearch} = useHelpSearch()

function navigateToResult(path: string, closeFn: () => void) {
  router.push(path)
  clearSearch()
  closeFn()
}

function highlightSnippet(result: (typeof searchResults.value)[number]): string {
  const s = result.snippet
  const before = escapeHtml(s.substring(0, result.matchStart))
  const match = escapeHtml(s.substring(result.matchStart, result.matchEnd))
  const after = escapeHtml(s.substring(result.matchEnd))
  return `${before}<mark class="bg-amber-300 dark:bg-amber-600 text-inherit rounded-xs px-0.5">${match}</mark>${after}`
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

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
      <div class="px-2 pb-3">
        <div class="relative">
          <TextInput v-model="searchQuery" :placeholder="t('helpCenter.search')"/>
          <font-awesome-icon :icon="['fas', 'magnifying-glass']"
                             class="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)] h-3.5 w-3.5 pointer-events-none"/>
        </div>
      </div>

      <template v-if="isSearching">
        <div v-if="searchResults.length === 0" class="px-3 py-4 text-sm text-[var(--text-muted)]">
          {{ t('helpCenter.noSearchResults') }}
        </div>
        <div v-else class="flex flex-col gap-1 px-2 pb-3">
          <BaseButton v-for="result in searchResults" :key="result.entry.route"
                  class="!text-left !block !py-2 hover:bg-[var(--bg-hover)]"
                  full-width
                  @click="navigateToResult(result.entry.path, close)">
            <div class="text-sm font-medium text-[var(--text)]">{{ result.entry.title }}</div>
            <div class="text-xs text-[var(--text-muted)] mb-1">{{ result.entry.section }}</div>
            <div class="text-xs text-[var(--text-muted)] leading-relaxed break-words"
                 v-html="highlightSnippet(result)"/>
          </BaseButton>
        </div>
      </template>

      <template v-else>
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
        <SidebarLink :icon="['fas', 'arrow-right-arrow-left']" name="help-basics-federation"
                     to="/helpcenter/station/basics/federation" @navigate="close">
          {{ t('helpCenter.basics.sidebarFederation') }}
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

      <SidebarGroup :icon="['fas', 'clipboard-check']" :label="t('sidebar.requirements')" prefix="/helpcenter/station/requirements"
                    to="/helpcenter/station/requirements" name="help-station-requirements" @navigate="close"/>

      <SidebarGroup :icon="['fas', 'newspaper']" :label="t('sidebar.news')" prefix="/helpcenter/station/news"
                    to="/helpcenter/station/news" name="help-news-module-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'pen']" name="help-news-create" to="/helpcenter/station/news/create" @navigate="close">
          {{ t('sidebar.newsEdit') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'user']" :label="t('sidebar.profile')" prefix="/helpcenter/station/profile"
                    to="/helpcenter/station/profile" name="help-profile-module-overview" @navigate="close">
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
        <SidebarExpandableLink :icon="['fas', 'gear']" name="help-profile-settings"
                     to="/helpcenter/station/profile/settings" prefix="/helpcenter/station/profile/settings" @navigate="close">
          <template #label>{{ t('sidebar.settings') }}</template>
          <SidebarLink :icon="['fas', 'palette']" name="help-profile-theming"
                       to="/helpcenter/station/profile/settings/theming" @navigate="close">
            {{ t('sidebar.theming') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'desktop']" name="help-profile-sessions"
                       to="/helpcenter/station/profile/settings/sessions" @navigate="close">
            {{ t('sidebar.sessions') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'bell']" name="help-profile-notifications"
                       to="/helpcenter/station/profile/settings/notifications" @navigate="close">
            {{ t('sidebar.notifications') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'palette']" name="help-profile-theme"
                     to="/helpcenter/station/profile/theme" @navigate="close">
          {{ t('helpCenter.themeUser.sidebarLabel') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'gears']" :label="t('sidebar.station')" prefix="/helpcenter/station/manage"
                    to="/helpcenter/station/manage" name="help-manage-module-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'palette']" name="help-station-theme-manage"
                     to="/helpcenter/station/manage/theme" @navigate="close">
          {{ t('helpCenter.themeManage.sidebarLabel') }}
        </SidebarLink>
        <SidebarExpandableLink :icon="['fas', 'arrow-right-arrow-left']" name="help-station-federation"
                               to="/helpcenter/station/manage/federation" @navigate="close">
          <template #label>{{ t('helpCenter.federation.sidebarLabel') }}</template>
          <SidebarLink :icon="['fas', 'sliders']" name="help-station-federation-partner"
                       to="/helpcenter/station/manage/federation/0" @navigate="close">
            {{ t('helpCenter.federationPartner.sidebarLabel') }}
          </SidebarLink>
        </SidebarExpandableLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'users']" :label="t('sidebar.members')" prefix="/helpcenter/station/members"
                    to="/helpcenter/station/members" name="help-members-module-overview" @navigate="close">
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
        <SidebarLink :icon="['fas', 'shield']" name="help-members-type-permissions"
                     to="/helpcenter/station/members/type-permissions" @navigate="close">
          {{ t('sidebar.typePermissions') }}
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
        <SidebarLink :icon="['fas', 'users-gear']" name="help-station-members-config"
                     to="/helpcenter/station/members/config" @navigate="close">
          {{ t('sidebar.membersConfig') }}
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
        <SidebarExpandableLink :icon="['fas', 'handshake']" name="help-inventory-lending"
                               to="/helpcenter/station/inventory/lending" prefix="/helpcenter/station/inventory/lending" @navigate="close">
          <template #label>{{ t('sidebar.inventoryLending') }}</template>
          <SidebarLink :icon="['fas', 'calendar-xmark']" name="help-inventory-lending-blocks"
                       to="/helpcenter/station/inventory/lending/blocks" @navigate="close">
            {{ t('helpCenter.inventoryLendingBlocks.title') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'magnifying-glass']" name="help-inventory-lending-browse"
                       to="/helpcenter/station/inventory/lending/browse" @navigate="close">
            {{ t('helpCenter.inventoryLendingBrowse.title') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'file-lines']" name="help-inventory-lending-request"
                       to="/helpcenter/station/inventory/lending/request/0" @navigate="close">
            {{ t('helpCenter.inventoryLendingRequest.title') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'plus']" name="help-inventory-lending-create"
                       to="/helpcenter/station/inventory/lending/request/new" @navigate="close">
            {{ t('helpCenter.inventoryLendingCreate.title') }}
          </SidebarLink>
        </SidebarExpandableLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'clipboard-user']" :label="t('sidebar.attendance')"
                    prefix="/helpcenter/station/attendance"
                    to="/helpcenter/station/attendance" name="help-attendance-module-overview" @navigate="close">
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
        <SidebarExpandableLink :icon="['fas', 'gear']" name="help-station-attendance-config"
                               to="/helpcenter/station/attendance/config" @navigate="close">
          <template #label>{{ t('sidebar.attendanceConfig') }}</template>
          <SidebarLink :icon="['fas', 'pen']" name="help-station-attendance-config-edit"
                       to="/helpcenter/station/attendance/config/edit" @navigate="close">
            {{ t('sidebar.attendanceConfigEdit') }}
          </SidebarLink>
        </SidebarExpandableLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'calendar-days']" :label="t('sidebar.events')"
                    prefix="/helpcenter/station/events"
                    to="/helpcenter/station/events" name="help-events-module-overview" @navigate="close">
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
                     to="/helpcenter/station/events/0" @navigate="close">
          {{ t('sidebar.eventDetail') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'square-poll-vertical']" :label="t('sidebar.forms')"
                    prefix="/helpcenter/station/forms"
                    to="/helpcenter/station/forms" name="help-forms-module-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'plus']" name="help-forms-create"
                     to="/helpcenter/station/forms/create" @navigate="close">
          {{ t('sidebar.formsCreate') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'pen']" name="help-forms-edit"
                     to="/helpcenter/station/forms/0/edit" @navigate="close">
          {{ t('sidebar.formsEdit') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'pen-to-square']" name="help-forms-fill"
                     to="/helpcenter/station/forms/0/fill" @navigate="close">
          {{ t('sidebar.formsFill') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'chart-bar']" name="help-forms-analytics"
                     to="/helpcenter/station/forms/0/analytics" @navigate="close">
          {{ t('sidebar.formsAnalytics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'box-open']" :label="t('sidebar.lostAndFound')" prefix="/helpcenter/station/lost-and-found"
                    to="/helpcenter/station/lost-and-found" name="help-lost-and-found" @navigate="close">
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'graduation-cap']" :label="t('sidebar.quiz')" prefix="/helpcenter/station/quiz"
                    to="/helpcenter/station/quiz" name="help-quiz-module-overview" @navigate="close">
        <SidebarExpandableLink :icon="['fas', 'book']" name="help-quiz-catalogs"
                               to="/helpcenter/station/quiz/catalogs" prefix="/helpcenter/station/quiz/catalog" @navigate="close">
          <template #label>{{ t('sidebar.quizCatalogs') }}</template>
          <SidebarLink :icon="['fas', 'eye']" name="help-quiz-catalog-detail" to="/helpcenter/station/quiz/catalogs/0" @navigate="close">
            {{ t('sidebar.quizCatalogDetail') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'robot']" name="help-quiz-ai" to="/helpcenter/station/quiz/ai" @navigate="close">
            {{ t('sidebar.quizAi') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'robot']" name="help-quiz-catalog-generate" to="/helpcenter/station/quiz/catalogs/0/generate" @navigate="close">
            {{ t('sidebar.quizCatalogGenerate') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'file-import']" name="help-quiz-catalog-import" to="/helpcenter/station/quiz/catalogs/0/import" @navigate="close">
            {{ t('sidebar.quizCatalogImport') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarExpandableLink :icon="['fas', 'file-lines']" name="help-quiz-tests"
                               to="/helpcenter/station/quiz/tests" prefix="/helpcenter/station/quiz/tests" @navigate="close">
          <template #label>{{ t('sidebar.quizTests') }}</template>
          <SidebarLink :icon="['fas', 'plus']" name="help-quiz-test-create" to="/helpcenter/station/quiz/tests/create" @navigate="close">
            {{ t('sidebar.quizTestCreate') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'eye']" name="help-quiz-test-detail" to="/helpcenter/station/quiz/tests/0" @navigate="close">
            {{ t('sidebar.quizTestDetail') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'pen']" name="help-quiz-test-edit" to="/helpcenter/station/quiz/tests/0/edit" @navigate="close">
            {{ t('sidebar.quizTestEdit') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'pen']" name="help-quiz-test-take" to="/helpcenter/station/quiz/tests/0/take" @navigate="close">
            {{ t('sidebar.quizTestTake') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'star']" name="help-quiz-test-evaluate" to="/helpcenter/station/quiz/tests/0/evaluate/0" @navigate="close">
            {{ t('sidebar.quizTestEvaluate') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'brain']" name="help-quiz-training" to="/helpcenter/station/quiz/training" @navigate="close">
          {{ t('sidebar.quizTraining') }}
        </SidebarLink>
        <SidebarExpandableLink :icon="['fas', 'clipboard-check']" name="help-protocol-module-overview" to="/helpcenter/station/protocols" prefix="/helpcenter/station/protocols" @navigate="close">
          <template #label>{{ t('sidebar.protocols') }}</template>
          <SidebarLink :icon="['fas', 'eye']" name="help-protocol-detail" to="/helpcenter/station/protocols/0" @navigate="close">
            {{ t('sidebar.protocolDetail') }}
          </SidebarLink>
          <SidebarExpandableLink :icon="['fas', 'list-check']" name="help-protocol-run-list" to="/helpcenter/station/protocols/runs" prefix="/helpcenter/station/protocols/runs" @navigate="close">
            <template #label>{{ t('sidebar.protocolRuns') }}</template>
            <SidebarLink :icon="['fas', 'eye']" name="help-protocol-run-detail" to="/helpcenter/station/protocols/runs/0" @navigate="close">
              {{ t('sidebar.protocolRunDetail') }}
            </SidebarLink>
            <SidebarLink :icon="['fas', 'chart-bar']" name="help-protocol-evaluation" to="/helpcenter/station/protocols/runs/0/evaluation" @navigate="close">
              {{ t('sidebar.protocolEvaluation') }}
            </SidebarLink>
            <SidebarLink :icon="['fas', 'pen-to-square']" name="help-protocol-grade" to="/helpcenter/station/protocols/runs/0/grade/0" @navigate="close">
              {{ t('sidebar.protocolGrade') }}
            </SidebarLink>
          </SidebarExpandableLink>
        </SidebarExpandableLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'table-columns']" :label="t('sidebar.boards')" prefix="/helpcenter/station/boards"
                    :prefix2="['/helpcenter/station/federation/boards']" to="/helpcenter/station/boards" name="help-board-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'gears']" name="help-board-manage" to="/helpcenter/station/boards/manage" @navigate="close">{{ t('helpCenter.boardManage.sidebarLabel') }}</SidebarLink>
        <SidebarLink :icon="['fas', 'table-columns']" name="help-board-view" to="/helpcenter/station/boards/BOARD" @navigate="close">{{ t('helpCenter.boardView.sidebarLabel') }}</SidebarLink>
        <SidebarLink :icon="['fas', 'plus']" name="help-ticket-create" to="/helpcenter/station/boards/BOARD/tickets/new" @navigate="close">{{ t('helpCenter.ticketCreate.sidebarLabel') }}</SidebarLink>
        <SidebarLink :icon="['fas', 'eye']" name="help-ticket-detail" to="/helpcenter/station/boards/BOARD/tickets/1" @navigate="close">{{ t('helpCenter.ticketDetail.sidebarLabel') }}</SidebarLink>
        <SidebarLink :icon="['fas', 'inbox']" name="help-board-backlog" to="/helpcenter/station/boards/BOARD/backlog" @navigate="close">{{ t('helpCenter.backlog.sidebarLabel') }}</SidebarLink>
        <SidebarLink :icon="['fas', 'folder']" name="help-board-archived" to="/helpcenter/station/boards/BOARD/archived" @navigate="close">{{ t('helpCenter.archived.sidebarLabel') }}</SidebarLink>
        <SidebarLink :icon="['fas', 'gear']" name="help-board-settings" to="/helpcenter/station/boards/BOARD/settings" @navigate="close">{{ t('helpCenter.boardSettings.sidebarLabel') }}</SidebarLink>
        <SidebarLink :icon="['fas', 'arrow-right-arrow-left']" name="help-federated-boards" to="/helpcenter/station/federation/boards" @navigate="close">{{ t('helpCenter.federatedBoards.sidebarLabel') }}</SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'book-open']" :label="t('sidebar.knowledgeBase')" prefix="/helpcenter/station/knowledge"
                    to="/helpcenter/station/knowledge" name="help-knowledge-module-overview" @navigate="close">
        <SidebarLink :icon="['fas', 'house']" name="help-knowledge-base" to="/helpcenter/station/knowledge/browse" @navigate="close">
          {{ t('sidebar.overview') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'pen']" name="help-knowledge-editor" to="/helpcenter/station/knowledge/editor" @navigate="close">
          {{ t('sidebar.knowledgeEditor') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'arrow-right-arrow-left']" name="help-knowledge-federated" to="/helpcenter/station/knowledge/federated" @navigate="close">
          {{ t('helpCenter.federatedKb.sidebarLabel') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'file']" name="help-kb-file" to="/helpcenter/station/knowledge/file/0" @navigate="close">
          {{ t('helpCenter.kbFileView.sidebarLabel') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clock-rotate-left']" name="help-kb-versions" to="/helpcenter/station/knowledge/file/0/versions" @navigate="close">
          {{ t('helpCenter.kbVersions.sidebarLabel') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :icon="['fas', 'file-lines']" :label="t('sidebar.pages')" prefix="/helpcenter/station/pages"
                    to="/helpcenter/station/pages" name="help-pages" @navigate="close"/>
      </template>
    </template>

    <template #header>
      <router-link to="/helpcenter/admin/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('helpCenter.adminHelp') }}</span>
        </SecondaryButton>
      </router-link>
    </template>

    <slot><RouterView/></slot>
  </SidebarLayout>
</template>
