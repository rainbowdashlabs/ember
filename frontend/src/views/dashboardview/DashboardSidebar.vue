/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import {StationModules, StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useStationTransferStatus} from '@/composables/useStationTransferStatus'
import SetupSidebarGroup from '@/views/dashboardview/SetupSidebarGroup.vue'
import DashboardSidebarGroup from '@/views/dashboardview/DashboardSidebarGroup.vue'
import ProfileSidebarGroup from '@/views/dashboardview/ProfileSidebarGroup.vue'
import MembersSidebarGroup from '@/views/dashboardview/MembersSidebarGroup.vue'
import InventorySidebarGroup from '@/views/dashboardview/InventorySidebarGroup.vue'
import AttendanceSidebarGroup from '@/views/dashboardview/AttendanceSidebarGroup.vue'
import EventsSidebarGroup from '@/views/dashboardview/EventsSidebarGroup.vue'
import FormsSidebarGroup from '@/views/dashboardview/FormsSidebarGroup.vue'
import QuizSidebarGroup from '@/views/dashboardview/QuizSidebarGroup.vue'
import BoardsSidebarGroup from '@/views/dashboardview/BoardsSidebarGroup.vue'
import ProceduresSidebarGroup from '@/views/dashboardview/ProceduresSidebarGroup.vue'
import PagesSidebarGroup from '@/views/dashboardview/PagesSidebarGroup.vue'
import ManageSidebarGroup from '@/views/dashboardview/ManageSidebarGroup.vue'
import FederationSidebarGroup from '@/views/dashboardview/FederationSidebarGroup.vue'

const props = defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {counts} = useSidebarCounts()
const {hasMoved: stationMoved} = useStationTransferStatus()
const {
  sessionInfo,
  isManager,
  hasPermission,
  hasAnyMemberPermission,
  hasAnyAttendancePermission,
  hasAnyWaitlistPermission,
  canManageFederation,
  isModuleEnabled,
} = useSession()

const groupBindings = computed(() => ({
  openGroup: props.openGroup,
  isDesktop: props.isDesktop,
  'onUpdate:openGroup': (value: string | null) => emit('update:openGroup', value),
  onNavigate: () => emit('navigate'),
}))

function close() {
  emit('navigate')
}

/**
 * The media library is shared by everything that authors content, so any one of the three
 * content permissions opens it. Members without one still reach it through an editor, where
 * they see what they uploaded themselves.
 */
function canBrowseMedia() {
  return hasPermission(StationPermission.PAGE_EDIT)
      || hasPermission(StationPermission.NEWS_EDIT)
      || hasPermission(StationPermission.KNOWLEDGE_EDIT)
}
</script>

<template>
  <SidebarGroup v-if="stationMoved"
                :open-group="isDesktop ? undefined : openGroup"
                @update:open-group="v => emit('update:openGroup', v)"
                :icon="['fas', 'map-location-dot']"
                :label="t('sidebar.stationMoved')"
                prefix="/station/moved"
                to="/station/moved"
                name="station-moved"
                @navigate="close"/>

  <SidebarGroup v-if="stationMoved && hasPermission(StationPermission.STATION_ADMINISTRATOR)"
                :open-group="isDesktop ? undefined : openGroup"
                @update:open-group="v => emit('update:openGroup', v)"
                :icon="['fas', 'trash']"
                :label="t('pages.station-moved.tabDelete')"
                prefix="/station/moved/delete"
                to="/station/moved/delete"
                name="station-moved-delete"
                @navigate="close"/>

  <template v-else>
    <SetupSidebarGroup
        v-if="hasPermission(StationPermission.STATION_ADMINISTRATOR) && sessionInfo?.setupCompletedAt == null"
        v-bind="groupBindings"/>

    <DashboardSidebarGroup v-bind="groupBindings"/>

    <SidebarGroup v-if="counts.requirements > 0" :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :badge="counts.requirements" :icon="['fas', 'clipboard-check']" :label="t('sidebar.requirements')" prefix="/station/requirements" to="/station/requirements" name="station-requirements" @navigate="close"/>

    <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" v-if="isModuleEnabled(StationModules.NEWS)" :icon="['fas', 'newspaper']" :label="t('sidebar.news')" prefix="/station/news" to="/station/news" name="news-list" @navigate="close"/>

    <ProfileSidebarGroup v-bind="groupBindings"/>

    <MembersSidebarGroup v-if="hasAnyMemberPermission() || hasAnyWaitlistPermission()" v-bind="groupBindings"/>

    <InventorySidebarGroup v-if="isModuleEnabled(StationModules.INVENTORY)" v-bind="groupBindings"/>

    <AttendanceSidebarGroup v-if="hasAnyAttendancePermission() && isModuleEnabled(StationModules.ATTENDANCE)" v-bind="groupBindings"/>

    <EventsSidebarGroup v-if="isModuleEnabled(StationModules.EVENTS)" v-bind="groupBindings"/>

    <FormsSidebarGroup v-if="isModuleEnabled(StationModules.FORMS)" v-bind="groupBindings"/>

    <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" v-if="isModuleEnabled(StationModules.LOST_AND_FOUND)" :badge="counts.lostAndFoundPending" :icon="['fas', 'box-open']" :label="t('sidebar.lostAndFound')" prefix="/station/lost-and-found" to="/station/lost-and-found" name="lost-and-found" @navigate="close"/>

    <QuizSidebarGroup v-if="isModuleEnabled(StationModules.QUIZ) || isModuleEnabled(StationModules.TEST_PROTOCOL)" v-bind="groupBindings"/>

    <BoardsSidebarGroup v-if="isModuleEnabled(StationModules.BOARDS) && hasPermission(StationPermission.BOARD_USE)" v-bind="groupBindings"/>

    <ProceduresSidebarGroup v-if="isModuleEnabled(StationModules.PROCEDURES) && (hasPermission(StationPermission.PROCEDURE_READ) || counts.procedureCount > 0)" v-bind="groupBindings"/>

    <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" v-if="hasPermission(StationPermission.CHECKLIST_READ)" :icon="['fas', 'list-check']" :label="t('sidebar.checklists')" prefix="/station/checklist" to="/station/checklist" name="checklist-list" @navigate="close"/>

    <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" v-if="isModuleEnabled(StationModules.KNOWLEDGE_BASE)" :icon="['fas', 'book-open']" :label="t('sidebar.knowledgeBase')" prefix="/station/knowledge" to="/station/knowledge" name="kb-browse" @navigate="close"/>

    <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" v-if="canBrowseMedia()" :icon="['fas', 'folder-open']" :label="t('sidebar.media')" prefix="/station/media" to="/station/media" name="station-media" @navigate="close"/>

    <PagesSidebarGroup v-if="hasPermission(StationPermission.PAGE_EDIT)" v-bind="groupBindings"/>

    <ManageSidebarGroup v-if="isManager()" v-bind="groupBindings"/>

    <FederationSidebarGroup v-if="canManageFederation()" v-bind="groupBindings"/>
  </template>
</template>
