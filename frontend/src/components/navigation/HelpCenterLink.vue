/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'

const route = useRoute()
const {t} = useI18n()

const routeToHelp: Record<string, string> = {
  'dashboard-overview': 'help-dashboard-overview',
  'dashboard-statistics': 'help-dashboard-statistics',
  'news-list': 'help-news-list',
  'news-create': 'help-news-create',
  'news-edit': 'help-news-edit',
  'profile': 'help-profile',
  'profile-absences': 'help-profile-absences',
  'profile-managed': 'help-profile-managed',
  'profile-inventory': 'help-profile-inventory',
  'profile-settings': 'help-profile-settings',
  'station-manage': 'help-station-manage',
  'station-attendance-config': 'help-station-attendance-config',
  'station-attendance-config-edit': 'help-station-attendance-config-edit',
  'station-members-config': 'help-station-members-config',
  'station-mail-config': 'help-station-mail-config',
  'members-create': 'help-members-create',
  'members-list': 'help-members-list',
  'members-detail': 'help-members-detail',
  'members-edit': 'help-members-edit',
  'members-import': 'help-members-import',
  'members-import-team': 'help-members-import-team',
  'members-groups': 'help-members-groups',
  'members-tags': 'help-members-tags',
  'members-changes': 'help-members-changes',
  'members-former': 'help-members-former',
  'inventory-overview': 'help-inventory-overview',
  'inventory-my': 'help-inventory-my',
  'inventory-manage': 'help-inventory-manage',
  'inventory-edit': 'help-inventory-edit',
  'inventory-detail': 'help-inventory-detail',
  'inventory-exchanges': 'help-inventory-exchanges',
  'inventory-members': 'help-inventory-members',
  'inventory-member': 'help-inventory-member',
  'inventory-requirements': 'help-inventory-requirements',
  'inventory-checks': 'help-inventory-checks',
  'inventory-check-member': 'help-inventory-check-member',
  'inventory-check-result': 'help-inventory-check-result',
  'inventory-procurement': 'help-inventory-procurement',
  'attendance-new': 'help-attendance-new',
  'attendance-session': 'help-attendance-session',
  'attendance-past': 'help-attendance-past',
  'attendance-report': 'help-attendance-report',
  'events': 'help-events',
  'events-upcoming': 'help-events-upcoming',
  'events-registrations': 'help-events-registrations',
  'event-new': 'help-event-new',
  'event-edit': 'help-event-edit',
  'event-detail': 'help-event-detail',
  'forms-list': 'help-forms-list',
  'forms-create': 'help-forms-create',
  'forms-fill': 'help-forms-fill',
  'forms-analytics': 'help-forms-analytics',
  'lost-and-found': 'help-lost-and-found',
  'admin-overview': 'help-admin-overview',
  'admin-statistics': 'help-admin-statistics',
  'admin-stations': 'help-admin-stations',
  'admin-station-edit': 'help-admin-station-edit',
  'admin-station-applications': 'help-admin-station-applications',
}

const helpRoute = computed(() => {
  const name = route.name as string
  return {name: routeToHelp[name] ?? 'help-dashboard-overview'}
})
</script>

<template>
  <router-link
      :to="helpRoute"
      class="inline-flex items-center gap-1.5 text-sm text-[var(--text-muted)] hover:text-primary transition-colors"
      target="_blank"
  >
    <font-awesome-icon :icon="['fas', 'circle-question']" class="w-4 h-4"/>
    <span class="hidden sm:inline">{{ t('helpCenter.link') }}</span>
  </router-link>
</template>
