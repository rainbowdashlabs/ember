/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'

defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {hasPermission} = useSession()
const {counts} = useSidebarCounts()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :badge="counts.pendingRegistrations + counts.openEvents" :icon="['fas', 'calendar-days']" :label="t('sidebar.events')" to="/station/events/upcoming" name="events-upcoming" @navigate="close">
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
</template>
