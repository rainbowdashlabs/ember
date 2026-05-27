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
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'
import StationSwitcher from '@/components/navigation/StationSwitcher.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {auth, notifications, events, boards} from '@/api'
import type {Board} from '@/api/boards'
import client from '@/api/client'
import Alert from '@/components/feedback/Alert.vue'
import {getItem} from '@/api/storage'
import {Roles, StationModules} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useTheme} from '@/composables/useTheme'
import {useStations} from '@/composables/useStations'
import {usePendingChanges} from '@/composables/usePendingChanges'
import HelpCenterLink from '@/components/navigation/HelpCenterLink.vue'
import OnboardingTour from '@/components/onboarding/OnboardingTour.vue'
import ReportProblemButton from '@/components/feedback/ReportProblemButton.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import {useOnboardingTour} from '@/composables/useOnboardingTour'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()
const {
  sessionInfo,
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
  canManagePolls,
  canManageWaitlist,
  canManageQuiz,
  canManageProtocol,
  canManageBoards,
  canManageFederation,
  canTestProtocol,
  isGuardian,
  isModuleEnabled,
  fullName,
  clear
} = useSession()
const {activeStation, activeLogoUrl} = useStations()
const {pendingChangesCount, refresh: refreshPendingChanges} = usePendingChanges()

const isDemo = ref(false)
const notificationCount = ref(0)
const pendingRegistrationCount = ref(0)
const visibleBoards = ref<Board[]>([])
const openGroup = ref<string | null>(null)
const isDesktop = ref(window.matchMedia('(min-width: 1024px)').matches)

onMounted(() => {
  const mq = window.matchMedia('(min-width: 1024px)')
  const handler = (e: MediaQueryListEvent) => { isDesktop.value = e.matches }
  mq.addEventListener('change', handler)
})

async function refreshNotificationCount() {
  try {
    notificationCount.value = await notifications.getCount()
  } catch { /* ignore */ }
}

const {checkFirstLogin} = useOnboardingTour()

async function refreshBoards() {
  try {
    visibleBoards.value = await boards.listBoards(true)
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
  if (isLoaded && (canManageMembers() || isGuardian())) refreshPendingChanges()
  if (isLoaded) refreshNotificationCount()
  if (isLoaded && canManageEvents()) refreshPendingRegistrationCount()
  if (isLoaded && isModuleEnabled(StationModules.BOARDS)) refreshBoards()
  if (isLoaded) checkFirstLogin()
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
  useTheme().resetToInstanceDefaults()
  await router.push({name: 'login'})
}
</script>

<template>
  <SidebarLayout :station-logo-url="activeLogoUrl" :station-name="activeStation?.stationName" :subtitle="pageSubtitle"
                 :title="pageTitle">
    <template #sidebar="{ close }">
      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" :badge="notificationCount" :icon="['fas', 'gauge']" :label="t('sidebar.dashboard')" prefix="/station/dashboard" to="/station/dashboard/overview" name="dashboard-overview" @navigate="close">
        <SidebarLink v-if="hasRole(Roles.TEAM)" :icon="['fas', 'chart-line']" name="dashboard-statistics"
                     to="/station/dashboard/statistics" @navigate="close">
          {{ t('sidebar.statistics') }}
        </SidebarLink>
      </SidebarGroup>

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
        <SidebarExpandableLink :icon="['fas', 'gear']" name="profile-theming" to="/station/profile/settings/theming" prefix="/station/profile/settings" @navigate="close">
          <template #label>{{ t('sidebar.settings') }}</template>
          <SidebarLink :icon="['fas', 'palette']" name="profile-theming" to="/station/profile/settings/theming" @navigate="close">
            {{ t('sidebar.theming') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'desktop']" name="profile-sessions" to="/station/profile/settings/sessions" @navigate="close">
            {{ t('sidebar.sessions') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'bell']" name="profile-notifications" to="/station/profile/settings/notifications" @navigate="close">
            {{ t('sidebar.notifications') }}
          </SidebarLink>
        </SidebarExpandableLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isManager()" :icon="['fas', 'gears']" :label="t('sidebar.station')" prefix="/station/manage">
        <SidebarExpandableLink :icon="['fas', 'gears']" name="station-manage" to="/station/manage" prefix="/station/manage" @navigate="close">
          <template #label>{{ t('sidebar.manage') }}</template>
          <SidebarLink :icon="['fas', 'palette']" name="station-theme" to="/station/manage/theme" @navigate="close">
            {{ t('sidebar.stationTheme') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'envelope']" name="station-mailing" to="/station/manage/mailing" @navigate="close">
            {{ t('sidebar.stationMailing') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'puzzle-piece']" name="station-modules" to="/station/manage/modules" @navigate="close">
            {{ t('sidebar.stationModules') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'file-import']" name="station-import" to="/station/manage/import" @navigate="close">
            {{ t('sidebar.stationImport') }}
          </SidebarLink>
        </SidebarExpandableLink>
        <SidebarLink :icon="['fas', 'clipboard-check']" name="station-attendance-config"
                     to="/station/manage/attendance-config" @navigate="close">
          {{ t('sidebar.attendanceConfig') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'users-gear']" name="station-members-config" to="/station/manage/members-config"
                     @navigate="close">
          {{ t('sidebar.membersConfig') }}
        </SidebarLink>
        <SidebarExpandableLink v-if="canManageFederation()" :icon="['fas', 'arrow-right-arrow-left']" name="station-federation" to="/station/manage/federation" :prefix="['/station/manage/federation', '/station/manage/discovery']" @navigate="close">
          <template #label>{{ t('sidebar.federation') }}</template>
          <SidebarLink :icon="['fas', 'gear']" name="station-federation-settings" to="/station/manage/federation/settings" @navigate="close">
            {{ t('sidebar.federationSettings') }}
          </SidebarLink>
          <SidebarLink :icon="['fas', 'compass']" name="station-discovery" to="/station/manage/discovery" @navigate="close">
            {{ t('sidebar.discovery') }}
          </SidebarLink>
        </SidebarExpandableLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="canManageMembers()" :badge="pendingChangesCount" :icon="['fas', 'users']"
                    :label="t('sidebar.members')" prefix="/station/members" to="/station/members/list" name="members-list" @navigate="close">
        <SidebarLink :icon="['fas', 'user-plus']" name="members-create" to="/station/members/create" @navigate="close">
          {{ t('sidebar.create') }}
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
        <SidebarLink v-if="isModuleEnabled(StationModules.WAITING_LIST) && canManageWaitlist()"
                     :icon="['fas', 'clipboard-list']" name="waiting-lists" to="/station/members/waiting-lists"
                     @navigate="close">
          {{ t('sidebar.waitingLists') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.INVENTORY)" :icon="['fas', 'boxes-stacked']" :label="t('sidebar.inventory')" prefix="/station/inventory">
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
        <SidebarLink v-if="canManageInventory() && canManageFederation()" :icon="['fas', 'handshake']" name="inventory-lending" to="/station/inventory/lending"
                     @navigate="close">
          {{ t('sidebar.inventoryLending') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="canManageAttendance() && isModuleEnabled(StationModules.ATTENDANCE)" :icon="['fas', 'clipboard-user']" :label="t('sidebar.attendance')"
                    prefix="/station/attendance" to="/station/attendance/new" name="attendance-new" @navigate="close">
        <SidebarLink :icon="['fas', 'clock-rotate-left']" name="attendance-past" to="/station/attendance/past"
                     @navigate="close">
          {{ t('sidebar.pastAttendance') }}
        </SidebarLink>
        <SidebarLink v-if="canExportAttendance()" :icon="['fas', 'chart-line']" name="attendance-report"
                     to="/station/attendance/report" @navigate="close">
          {{ t('sidebar.attendanceReport') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.EVENTS)" :badge="pendingRegistrationCount" :icon="['fas', 'calendar-days']" :label="t('sidebar.events')" prefix="/station/events" to="/station/events/upcoming" name="events-upcoming" @navigate="close">
        <SidebarLink v-if="canManageEvents()" :badge="pendingRegistrationCount" :icon="['fas', 'clipboard-list']" name="events-registrations"
                     to="/station/events/registrations" @navigate="close">
          {{ t('sidebar.pendingRegistrations') }}
        </SidebarLink>
        <SidebarLink v-if="canManageEvents()" :icon="['fas', 'gears']" name="events" to="/station/events"
                     @navigate="close">
          {{ t('sidebar.manageEvents') }}
        </SidebarLink>
        <SidebarLink v-if="canManageEvents()" :icon="['fas', 'folder-plus']" name="event-categories" to="/station/events/categories"
                     @navigate="close">
          {{ t('sidebar.eventCategories') }}
        </SidebarLink>
        <SidebarLink v-if="canManageEvents()" :icon="['fas', 'clipboard-list']" name="event-templates" to="/station/events/templates"
                     @navigate="close">
          {{ t('sidebar.eventTemplates') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.FORMS)" :icon="['fas', 'square-poll-vertical']" :label="t('sidebar.forms')" prefix="/station/forms" to="/station/forms" name="forms-list" @navigate="close">
        <SidebarLink v-if="canManagePolls()" :icon="['fas', 'plus']" name="forms-create" to="/station/forms/create"
                     @navigate="close">
          {{ t('sidebar.formsCreate') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.LOST_AND_FOUND)" :icon="['fas', 'box-open']" :label="t('sidebar.lostAndFound')" prefix="/station/lost-and-found" to="/station/lost-and-found" name="lost-and-found" @navigate="close"/>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.QUIZ) || isModuleEnabled(StationModules.TEST_PROTOCOL)" :icon="['fas', 'graduation-cap']" :label="t('sidebar.quiz')" prefix="/station/quiz" group-key="quiz-protocols">
        <SidebarLink v-if="canManageQuiz()" :icon="['fas', 'book']" name="quiz-catalogs" to="/station/quiz/catalogs" @navigate="close">
          {{ t('sidebar.quizCatalogs') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'file-lines']" name="quiz-tests" to="/station/quiz/tests" @navigate="close">
          {{ t('sidebar.quizTests') }}
        </SidebarLink>
        <SidebarLink :icon="['fas', 'brain']" name="quiz-training" to="/station/quiz/training" @navigate="close">
          {{ t('sidebar.quizTraining') }}
        </SidebarLink>
        <SidebarLink v-if="canManageProtocol()" :icon="['fas', 'clipboard-list']" name="protocol-list" to="/station/protocols" @navigate="close">
          {{ t('sidebar.protocols') }}
        </SidebarLink>
        <SidebarLink v-if="canTestProtocol()" :icon="['fas', 'clipboard-check']" name="protocol-run-list" to="/station/protocols/runs" @navigate="close">
          {{ t('sidebar.protocolRuns') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.BOARDS)" :icon="['fas', 'table-columns']" :label="t('sidebar.boards')" prefix="/station/boards" to="/station/boards" name="board-list" @navigate="close">
        <SidebarLink v-for="board in visibleBoards" :key="board.id" :icon="['fas', 'table-columns']" :name="`board-${board.id}`" :to="`/station/boards/${board.id}`" :active="route.path.startsWith(`/station/boards/${board.id}`)" @navigate="close">
          {{ board.name }}
        </SidebarLink>
        <SidebarLink v-if="canManageBoards()" :icon="['fas', 'gears']" name="board-manage" to="/station/boards/manage" @navigate="close">
          {{ t('sidebar.boardManage') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => openGroup = v" v-if="isModuleEnabled(StationModules.KNOWLEDGE_BASE)" :icon="['fas', 'book-open']" :label="t('sidebar.knowledgeBase')" prefix="/station/knowledge" to="/station/knowledge" name="kb-browse" @navigate="close"/>

    </template>

    <template #header>
      <HelpCenterLink/>

      <router-link v-if="isAdmin()" to="/admin/dashboard/overview">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('header.adminPanel') }}</span>
        </SecondaryButton>
      </router-link>

      <UserAvatar :member-id="sessionInfo?.member?.id" :name="fullName()" size="sm" class="hidden sm:flex"/>
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
    <OnboardingTour/>
    <ReportProblemButton/>
  </SidebarLayout>
</template>
