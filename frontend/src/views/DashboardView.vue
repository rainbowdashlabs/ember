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
import StationSwitcher from '@/components/navigation/StationSwitcher.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import client from '@/api/client'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, StationModules} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useFederatedBoardBookmarks} from '@/composables/useFederatedBoardBookmarks'
import OnboardingTour from '@/views/dashboardview/OnboardingTour.vue'
import ReportProblemButton from '@/components/feedback/ReportProblemButton.vue'
import DevToolsButton from '@/components/feedback/DevToolsButton.vue'
import {useOnboardingTour} from '@/composables/useOnboardingTour'
import DashboardSidebar from '@/views/dashboardview/DashboardSidebar.vue'
import DashboardHeaderActions from '@/views/dashboardview/DashboardHeaderActions.vue'
import {useSidebarBoards} from '@/views/dashboardview/useSidebarBoards'
import QuickSearchPalette from '@/components/quicksearch/QuickSearchPalette.vue'
import {useQuickSearchShortcut} from '@/composables/useQuickSearchShortcut'
import {useStationTransferStatus} from '@/composables/useStationTransferStatus'
import {usePageHeader} from '@/composables/usePageHeader'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {title: pageTitle, subtitle: pageSubtitle} = usePageHeader()
const {
  sessionInfo,
  loaded,
  loadFailed,
  sessionStationId,
  load,
  isAdmin,
  hasPermission,
  canManageFederation,
  isModuleEnabled,
} = useSession()
const {activeStation, activeLogoUrl, currentStationId} = useStations()

const sessionCurrent = computed(() => loaded.value && sessionStationId.value === currentStationId.value)
const sessionReady = computed(() => sessionCurrent.value && !loadFailed.value)
const {refresh: refreshSidebarCounts} = useSidebarCounts()
const {refresh: refreshBoards} = useSidebarBoards()
const {refresh: refreshBookmarkedBoards} = useFederatedBoardBookmarks()
const {
  hasMoved: stationMoved,
  load: loadTransferStatus,
  reset: resetTransferStatus,
} = useStationTransferStatus()

const isDemo = ref(false)
const openGroup = ref<string | null>(null)
const isDesktop = ref(window.matchMedia('(min-width: 1024px)').matches)

useQuickSearchShortcut('station')

onMounted(() => {
  const mq = window.matchMedia('(min-width: 1024px)')
  mq.addEventListener('change', (e: MediaQueryListEvent) => { isDesktop.value = e.matches })
})

const {checkFirstLogin} = useOnboardingTour()

onMounted(async () => {
  if (!sessionCurrent.value) {
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

watch(sessionReady, (ready) => {
  if (!ready) return
  if (!sessionInfo.value?.member) {
    if (isAdmin()) {
      router.replace('/admin/dashboard/overview')
    } else {
      const target = route.path.startsWith('/station') ? route.fullPath : null
      router.replace({
        name: 'cross-station-dashboard',
        query: target ? {redirect: target} : {},
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

</script>

<template>
  <SidebarLayout :station-logo-url="activeLogoUrl" :station-name="activeStation?.stationName" :subtitle="pageSubtitle"
                 :title="pageTitle">
    <template #sidebar="{ close }">
      <DashboardSidebar :is-desktop="isDesktop" :open-group="openGroup"
                        @update:open-group="v => openGroup = v" @navigate="close"/>
    </template>

    <template #header>
      <DashboardHeaderActions/>
    </template>

    <template #footer>
      <StationSwitcher/>
    </template>

    <Alert v-if="isDemo" class="mb-4" variant="info">
      {{ t('demo.banner') }}
    </Alert>
    <Alert v-if="loadFailed" class="mb-4" variant="error">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>{{ t('dashboard.sessionLoadFailed') }}</span>
        <SecondaryButton @click="load()">{{ t('dashboard.sessionRetry') }}</SecondaryButton>
      </div>
    </Alert>
    <slot v-if="sessionReady && sessionInfo?.member"><RouterView/></slot>
    <OnboardingTour/>
    <ReportProblemButton/>
    <DevToolsButton/>
    <QuickSearchPalette/>
  </SidebarLayout>
</template>
