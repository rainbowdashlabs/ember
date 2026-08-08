/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'
import QuizSidebarGroup from '@/views/helpcenterstationview/QuizSidebarGroup.vue'

/**
 * Help navigation for the optional station modules — everything a station can turn on, as opposed
 * to the always-present areas in {@link HelpCenterSidebar}.
 */
defineProps<{
  close: () => void
}>()

const {t} = useI18n()
</script>

<template>
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

  <SidebarGroup :icon="['fas', 'list-check']" :label="t('sidebar.checklists')" prefix="/helpcenter/station/checklist"
                to="/helpcenter/station/checklist" name="help-checklist-list" @navigate="close">
    <SidebarLink :icon="['fas', 'table-columns']" name="help-checklist-detail" to="/helpcenter/station/checklist/1" @navigate="close">
      {{ t('helpCenter.checklist.detailSidebarLabel') }}
    </SidebarLink>
  </SidebarGroup>

  <QuizSidebarGroup :close="close"/>

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
