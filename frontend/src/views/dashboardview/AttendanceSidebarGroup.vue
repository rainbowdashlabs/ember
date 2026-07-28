/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {hasPermission, canExportAttendance} = useSession()

const attendanceDefaultRoute = computed(() => {
  if (hasPermission(StationPermission.ATTENDANCE_EDIT)) return '/station/attendance/new'
  return undefined
})

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'clipboard-user']" :label="t('sidebar.attendance')"
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
</template>
