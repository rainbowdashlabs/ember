/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import StationSwitcher from '@/components/navigation/StationSwitcher.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import {boards} from '@/api'
import type {Board} from '@/api/boards'
import {useFederatedBoardBookmarks} from '@/composables/useFederatedBoardBookmarks'
import client from '@/api/client'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, StationModules} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import OnboardingTour from '@/components/onboarding/OnboardingTour.vue'
import ReportProblemButton from '@/components/feedback/ReportProblemButton.vue'
import DevToolsButton from '@/components/feedback/DevToolsButton.vue'
import {useOnboardingTour} from '@/composables/useOnboardingTour'
import InventorySidebarGroup from '@/views/dashboardview/InventorySidebarGroup.vue'
import SetupSidebarGroup from '@/views/dashboardview/SetupSidebarGroup.vue'
import QuickSearchPalette from '@/components/quicksearch/QuickSearchPalette.vue'
import QuickSearchTrigger from '@/components/quicksearch/QuickSearchTrigger.vue'
import {useQuickSearch} from '@/composables/useQuickSearch'
import {useStationTransferStatus} from '@/composables/useStationTransferStatus'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()
const {
  sessionInfo,
  loaded,
  load,
  isAdmin,
  isManager,
  hasPermission,
  canManageMembers,
  hasAnyMemberPermission,
  hasAnyAttendancePermission,
  canExportAttendance,
  hasAnyWaitlistPermission,
  canManageFederation,
  canTestProtocol,
  isGuardian,
  isModuleEnabled,
} = useSession()
const {activeStation, activeLogoUrl} = useStations()
const {counts, refresh: refreshSidebarCounts} = useSidebarCounts()
const {bookmarks: bookmarkedBoards, refresh: refreshBookmarkedBoards} = useFederatedBoardBookmarks()
const {
  status: transferStatus,
  hasMoved: stationMoved,
  load: loadTransferStatus,
  reset: resetTransferStatus,
} = useStationTransferStatus()

const isDemo = ref(false)
const visibleBoards = ref<Board[]>([])
const openGroup = ref<string | null>(null)
const isDesktop = ref(window.matchMedia('(min-width: 1024px)').matches)

const {open: openQuickSearch, close: closeQuickSearch, isOpen: quickSearchOpen} = useQuickSearch()

function onGlobalKeydown(event: KeyboardEvent) {
  const isCtrlK = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k'
  if (!isCtrlK) return
  event.preventDefault()
  if (quickSearchOpen.value) {
    closeQuickSearch()
  } else {
    openQuickSearch('station')
  }
}

onMounted(() => {
  const mq = window.matchMedia('(min-width: 1024px)')
  const handler = (e: MediaQueryListEvent) => { isDesktop.value = e.matches }
  mq.addEventListener('change', handler)
  window.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

const {checkFirstLogin} = useOnboardingTour()

async function refreshBoards() {
  try {
    visibleBoards.value = await boards.listBoards(true)
  } catch {
    visibleBoards.value = []
  }
}

onMounted(async () => {
  if (!loaded.value) {
    load()
  }
  resetTransferStatus()
  loadTransferStatus()
  try {
    const res = await client.get<{ demo: boolean }>('/demo/status')
    isDemo.value = res.data.demo
  } catch {
    isDemo.value = false
  }
})

watch(
    () => activeStation.value?.stationId,
    () => {
      resetTransferStatus()
      loadTransferStatus()
    },
)

watch(
    [stationMoved, () => route.name],
    ([moved, currentName]) => {
      if (!moved) return
      const allowed = new Set(['station-moved', 'station-moved-delete'])
      if (typeof currentName === 'string' && !allowed.has(currentName)) {
        router.replace({name: 'station-moved'})
      }
    },
    {immediate: true},
)

watch(loaded, (isLoaded) => {
  if (!isLoaded) return
  if (!sessionInfo.value?.member) {
    if (isAdmin()) {
      router.replace('/admin/dashboard/overview')
    } else {
      // Preserve the current path as ?redirect so picking a station in the
      // cross-station view resumes the deep link the user was trying to reach
      // (e.g. an email/feed notification clicked while logged in but with no
      // active station context).
      router.replace({
        name: 'cross-station-dashboard',
        query: {redirect: route.fullPath},
      })
    }
    return
  }
  if (
      hasPermission(StationPermission.STATION_ADMINISTRATOR)
      && sessionInfo.value?.setupCompletedAt == null
      && !route.path.startsWith('/station/setup')
  ) {
    router.replace({name: 'station-setup'})
    return
  }
  refreshSidebarCounts()
  if (isModuleEnabled(StationModules.BOARDS) && hasPermission(StationPermission.BOARD_USE)) refreshBoards()
  if (isModuleEnabled(StationModules.BOARDS) && hasPermission(StationPermission.BOARD_USE) && canManageFederation()) refreshBookmarkedBoards()
  checkFirstLogin()
}, {immediate: true})

const pageTitle = computed(() => {
  const key = `pages.${route.name as string}.title`
  return te(key) ? t(key) : ''
})

const pageSubtitle = computed(() => {
  const key = `pages.${route.name as string}.subtitle`
  return te(key) ? t(key) : ''
})

const membersDefaultRoute = computed(() => {
  if (hasPermission(StationPermission.MEMBER_READ)) return '/station/members/list'
  return undefined
})

const attendanceDefaultRoute = computed(() => {
  if (hasPermission(StationPermission.ATTENDANCE_EDIT)) return '/station/attendance/new'
  return undefined
})

const manageDefaultRoute = computed(() => {
  if (hasPermission(StationPermission.STATION_GENERAL)) return '/station/manage'
  if (hasPermission(StationPermission.STATION_LOOK_AND_FEEL)) return '/station/manage/theme'
  if (hasPermission(StationPermission.STATION_MAIL)) return '/station/manage/mailing'
  if (hasPermission(StationPermission.STATION_MODULES)) return '/station/manage/modules'
  if (hasPermission(StationPermission.STATION_IMPORT_EXPORT)) return '/station/manage/import'
  return undefined
})

</script>

<template>
  <SidebarLayout :station-logo-url="activeLogoUrl" :station-name="activeStation?.stationName" :subtitle="pageSubtitle"
                 :title="pageTitle">
    <template #sidebar="{ close }">
      <SidebarGroup v-if="stationMoved"
                    :open-group="isDesktop ? undefined : openGroup"
                    @update:open-group="v => openGroup = v"
                    :icon="['fas', 'map-location-dot']"
                    :label="t('sidebar.stationMoved')"
                    to="/station/moved"
                    name="station-moved"
                    @navigate="close"/>

      <SidebarGroup v-if="stationMoved && hasPermission(StationPermission.STATION_ADMINISTRATOR)"
                    :open-group="isDesktop ? undefined : openGroup"
                    @update:open-group="v => openGroup = v"
                    :icon="['fas', 'trash']"
                    :label="t('pages.station-moved.tabDelete')"
                    to="/station/moved/delete"
                    name="station-moved-delete"
                    @navigate="close"/>

      <template v-else>
      <SetupSidebarGroup
          v-if="hasPermission(StationPermission.STATION_ADMINISTRATOR) && sessionInfo?.setupCompletedAt == null"
          :is-desktop="isDesktop"
          :open-group="openGroup"
          @update:open-group="v => openGroup = v"
          @navigate="close"/>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" :badge="counts.notifications" :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')" prefix="/station/dashboard" to="/station/dashboard/overview" name="dashboard-overview" @navigate="close">
        <SidebarLink v-if="hasPermission(StationPermission.STATION_STATISTICS)" :icon="['fas', 'chart-line']" name="dashboard-statistics"
                     to="/station/dashboard/statistics" @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup v-if="counts.requirements > 0" :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" :badge="counts.requirements" :icon="['fas', 'clipboard-check']" :label="t('sidebar.requirements')" prefix="/station/requirements" to="/station/requirements" name="station-requirements" @navigate="close"/>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.NEWS)" :icon="['fas', 'newspaper']" :label="t('sidebar.news')" prefix="/station/news" to="/station/news" name="news-list" @navigate="close"/>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" :icon="['fas', 'user']" :label="t('sidebar.profile')" prefix="/station/profile" to="/station/profile" name="profile" @navigate="close">
        <SidebarLink :icon="['fas', 'calendar-days']" name="profile-absences" to="/station/profile/absences"
                     @navigate="close">
          {{ t('sidebar.absences') }}
        </SidebarLink>
        <SidebarLink v-if="isGuardian()" :icon="['fas', 'users']" name="profile-managed"
                     to="/station/profile/managed" @navigate="close">
          {{ t('sidebar.managedProfiles') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'bell']" name="profile-notifications" to="/station/profile/settings/notifications" @navigate="close">
          {{ t('sidebar.notifications') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="hasAnyMemberPermission() || hasAnyWaitlistPermission()" :badge="counts.pendingChanges + counts.waitingListEntries" :icon="['fas', 'users']"
                    :label="t('sidebar.members')" prefix="/station/members" :to="membersDefaultRoute" name="members-list" @navigate="close">
        <SidebarLink v-if="hasPermission(StationPermission.MEMBER_EDIT)" :icon="['fas', 'user-plus']" name="members-create" to="/station/members/create" @navigate="close">
          {{ t('sidebar.create') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.MEMBER_MANAGE_GROUP)" :icon="['fas', 'layer-group']" name="members-groups" to="/station/members/groups"
                     @navigate="close">
          {{ t('sidebar.groups') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.MEMBER_MANAGE_TAGS)" :icon="['fas', 'hashtag']" name="members-tags" to="/station/members/tags"
                     @navigate="close">
          {{ t('sidebar.tags') }}
        </SidebarLink>
        <SidebarLink v-if="canManageMembers()" :icon="['fas', 'shield']" name="members-type-permissions" to="/station/members/type-permissions"
                     @navigate="close">
          {{ t('sidebar.typePermissions') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.MEMBER_CHANGES)" :badge="counts.pendingChanges" :icon="['fas', 'bell']" name="members-changes"
                     to="/station/members/changes" @navigate="close">
          {{ t('sidebar.changes') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.MEMBER_EDIT)" :icon="['fas', 'user-slash']" name="members-former" to="/station/members/former"
                     @navigate="close">
          {{ t('sidebar.formerMembers') }}
        </SidebarLink>
        <SidebarLink v-if="isModuleEnabled(StationModules.WAITING_LIST) && hasAnyWaitlistPermission()"
                     :badge="counts.waitingListEntries" :icon="['fas', 'clipboard-list']" name="waiting-lists" to="/station/members/waiting-lists"
                     @navigate="close">
          {{ t('sidebar.waitingLists') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.MEMBER_FIELDS)" :icon="['fas', 'users-gear']" name="station-members-config" to="/station/members/config"
                     @navigate="close">
          {{ t('sidebar.membersConfig') }}
        </SidebarLink>
      </SidebarGroup>

      <InventorySidebarGroup v-if="isModuleEnabled(StationModules.INVENTORY)"
                             :is-desktop="isDesktop" :open-group="openGroup"
                             @update:open-group="v => openGroup = v" @navigate="close"/>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="hasAnyAttendancePermission() && isModuleEnabled(StationModules.ATTENDANCE)" :icon="['fas', 'clipboard-user']" :label="t('sidebar.attendance')"
                    prefix="/station/attendance" :to="attendanceDefaultRoute" name="attendance-new" @navigate="close">
        <SidebarLink v-if="hasPermission(StationPermission.ATTENDANCE_READ)" :icon="['fas', 'clock-rotate-left']" name="attendance-past" to="/station/attendance/past"
                     @navigate="close">
          {{ t('sidebar.pastAttendance') }}
        </SidebarLink>
        <SidebarLink v-if="canExportAttendance()" :icon="['fas', 'chart-line']" name="attendance-report"
                     to="/station/attendance/report" @navigate="close">
          {{ t('sidebar.attendanceReport') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.ATTENDANCE_CONFIGURE)" :icon="['fas', 'gear']" name="station-attendance-config"
                     to="/station/attendance/config" @navigate="close">
          {{ t('sidebar.attendanceConfig') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.EVENTS)" :badge="counts.pendingRegistrations + counts.openEvents" :icon="['fas', 'calendar-days']" :label="t('sidebar.events')" prefix="/station/events" to="/station/events/upcoming" name="events-upcoming" @navigate="close">
        <SidebarLink v-if="hasPermission(StationPermission.EVENT_REGISTRATION)" :badge="counts.pendingRegistrations" :icon="['fas', 'clipboard-list']" name="events-registrations"
                     to="/station/events/registrations" @navigate="close">
          {{ t('sidebar.pendingRegistrations') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.EVENT_EDIT)" :icon="['fas', 'gears']" name="events" to="/station/events"
                     @navigate="close">
          {{ t('sidebar.manageEvents') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.EVENT_MANAGE_CATEGORY)" :icon="['fas', 'folder-plus']" name="event-categories" to="/station/events/categories"
                     @navigate="close">
          {{ t('sidebar.eventCategories') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.EVENT_MANAGE_TEMPLATE)" :icon="['fas', 'clipboard-list']" name="event-templates" to="/station/events/templates"
                     @navigate="close">
          {{ t('sidebar.eventTemplates') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.FORMS)" :icon="['fas', 'square-poll-vertical']" :label="t('sidebar.forms')" prefix="/station/forms" to="/station/forms" name="forms-list" @navigate="close">
        <SidebarLink v-if="hasPermission(StationPermission.POLL_CREATE)" :icon="['fas', 'plus']" name="forms-create" to="/station/forms/create"
                     @navigate="close">
          {{ t('sidebar.formsCreate') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.LOST_AND_FOUND)" :badge="counts.lostAndFoundPending" :icon="['fas', 'box-open']" :label="t('sidebar.lostAndFound')" prefix="/station/lost-and-found" to="/station/lost-and-found" name="lost-and-found" @navigate="close"/>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.QUIZ) || isModuleEnabled(StationModules.TEST_PROTOCOL)" :icon="['fas', 'graduation-cap']" :label="t('sidebar.quiz')" prefix="/station/quiz" group-key="quiz-protocols">
        <SidebarLink v-if="hasPermission(StationPermission.TEST_CATALOG_VIEW)" :icon="['fas', 'book']" name="quiz-catalogs" to="/station/quiz/catalogs" @navigate="close">
          {{ t('sidebar.quizCatalogs') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'file-lines']" name="quiz-tests" to="/station/quiz/tests" @navigate="close">
          {{ t('sidebar.quizTests') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'brain']" name="quiz-training" to="/station/quiz/training" @navigate="close">
          {{ t('sidebar.quizTraining') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.PROTOCOL_CREATE)" :icon="['fas', 'clipboard-list']" name="protocol-list" to="/station/protocols" @navigate="close">
          {{ t('sidebar.protocols') }}
        </SidebarLink>
        <SidebarLink v-if="canTestProtocol()" :icon="['fas', 'clipboard-check']" name="protocol-run-list" to="/station/protocols/runs" @navigate="close">
          {{ t('sidebar.protocolRuns') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.BOARDS) && hasPermission(StationPermission.BOARD_USE)" :icon="['fas', 'table-columns']" :label="t('sidebar.boards')" :prefix="['/station/boards', '/station/federation/boards']" to="/station/boards" name="board-list" @navigate="close">
        <SidebarLink v-for="board in visibleBoards" :key="board.id" :icon="['fas', 'table-columns']" :name="`board-${board.id}`" :to="`/station/boards/${board.shortKey}`" :active="route.path.startsWith(`/station/boards/${board.shortKey}`)" @navigate="close">
          {{ board.name }}
        </SidebarLink>
        <SidebarLink v-for="bm in bookmarkedBoards" :key="`fed-${bm.id}`" :icon="['fas', 'table-columns']" :name="`fed-board-${bm.id}`" :to="`/station/federation/boards/${bm.partnerStationUid}/${bm.remoteBoardShortKey}`" :active="route.path.startsWith(`/station/federation/boards/${bm.partnerStationUid}/${bm.remoteBoardShortKey}`)" @navigate="close">
          <span class="flex items-center gap-1.5">
            {{ bm.remoteBoardName }}
            <SecondaryBadge class="shrink-0">
              <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-2.5 h-2.5" />
            </SecondaryBadge>
          </span>
        </SidebarLink>
        <SidebarLink :icon="['fas', 'globe']" name="federated-boards" to="/station/federation/boards" @navigate="close">
          {{ t('boards.federatedBoards') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.BOARD_EDIT)" :icon="['fas', 'gears']" name="board-manage" to="/station/boards/manage" @navigate="close">
          {{ t('sidebar.boardManage') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.PROCEDURES) && (hasPermission(StationPermission.PROCEDURE_READ) || counts.procedureCount > 0)" :badge="counts.procedureCount" :icon="['fas', 'list-check']" :label="t('sidebar.procedures')" prefix="/station/procedures" to="/station/procedures" name="procedure-list" @navigate="close">
        <SidebarLink v-if="hasPermission(StationPermission.PROCEDURE_MANAGER)" :icon="['fas', 'clipboard-list']" name="procedure-template-list" to="/station/procedures/templates" @navigate="close">
          {{ t('sidebar.procedureTemplates') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.KNOWLEDGE_BASE)" :icon="['fas', 'book-open']" :label="t('sidebar.knowledgeBase')" prefix="/station/knowledge" to="/station/knowledge" name="kb-browse" @navigate="close"/>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="hasPermission(StationPermission.PAGE_EDIT)" :icon="['fas', 'file-lines']" :label="t('sidebar.pages')" prefix="/station/pages" to="/station/pages" name="pages-list" @navigate="close">
        <SidebarLink :icon="['fas', 'folder-open']" name="pages-files" to="/station/pages/files" @navigate="close">
          {{ t('sidebar.pagesFiles') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'clipboard-list']" name="pages-forms" to="/station/pages/forms" @navigate="close">
          {{ t('sidebar.pagesForms') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'square-poll-vertical']" name="pages-polls" to="/station/pages/polls" @navigate="close">
          {{ t('sidebar.pagesPolls') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isManager()" :icon="['fas', 'gears']" :label="t('sidebar.manage')" prefix="/station/manage" :to="manageDefaultRoute" name="station-manage" @navigate="close">
        <SidebarLink v-if="hasPermission(StationPermission.STATION_LOOK_AND_FEEL)" :icon="['fas', 'palette']" name="station-theme" to="/station/manage/theme" @navigate="close">
          {{ t('sidebar.stationTheme') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.STATION_MAIL)" :icon="['fas', 'envelope']" name="station-mailing" to="/station/manage/mailing" @navigate="close">
          {{ t('sidebar.stationMailing') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.STATION_MODULES)" :icon="['fas', 'puzzle-piece']" name="station-modules" to="/station/manage/modules" @navigate="close">
          {{ t('sidebar.stationModules') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.STATION_IMPORT_EXPORT)" :icon="['fas', 'file-import']" name="station-import" to="/station/manage/import" @navigate="close">
          {{ t('sidebar.stationImport') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.STATION_MANAGER)" :icon="['fas', 'hard-drive']" name="station-storage" to="/station/manage/storage" @navigate="close">
          {{ t('sidebar.storage') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.STATION_ADMINISTRATOR)" :icon="['fas', 'tower-broadcast']" name="station-traffic" to="/station/manage/traffic" @navigate="close">
          {{ t('sidebar.stationTraffic') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.STATION_ADMINISTRATOR)" :icon="['fas', 'chart-pie']" name="station-insights" to="/station/manage/insights" @navigate="close">
          {{ t('sidebar.stationInsights') }}
        </SidebarLink>
        <SidebarLink v-if="hasPermission(StationPermission.STATION_ADMINISTRATOR)" :icon="['fas', 'shield']" name="station-security" to="/station/manage/security" @navigate="close">
          {{ t('sidebar.stationSecurity') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="canManageFederation()" :badge="counts.federationRequests" :icon="['fas', 'arrow-right-arrow-left']" :label="t('sidebar.federation')" :prefix="['/station/federate', '/station/federate/discovery']" to="/station/federate" name="station-federation" @navigate="close">
        <SidebarLink :badge="counts.federationRequests" :icon="['fas', 'gear']" name="station-federation-settings" to="/station/federate/settings" @navigate="close">
          {{ t('sidebar.federationSettings') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'compass']" name="station-discovery" to="/station/federate/discovery" @navigate="close">
          {{ t('sidebar.discovery') }}
        </SidebarLink>
      </SidebarGroup>
      </template>

    </template>

    <template #header>
      <div class="hidden lg:flex"><StationSwitcher/></div>
      <QuickSearchTrigger scope="station" @open="openQuickSearch"/>
      <HelpCenterLink/>

      <router-link v-if="isAdmin()" to="/admin/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('header.adminPanel') }}</span>
        </SecondaryButton>
      </router-link>

      <AccountMenuButton/>
    </template>

    <template #footer>
      <StationSwitcher/>
    </template>

    <Alert v-if="isDemo" class="mb-4" variant="info">
      {{ t('demo.banner') }}
    </Alert>
    <slot v-if="loaded && sessionInfo?.member"><RouterView/></slot>
    <OnboardingTour/>
    <ReportProblemButton/>
    <DevToolsButton/>
    <QuickSearchPalette/>
  </SidebarLayout>
</template>
