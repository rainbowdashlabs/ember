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
const {hasPermission} = useSession()

const manageDefaultRoute = computed(() => {
  if (hasPermission(StationPermission.STATION_GENERAL)) return '/station/manage'
  if (hasPermission(StationPermission.STATION_LOOK_AND_FEEL)) return '/station/manage/theme'
  if (hasPermission(StationPermission.STATION_MAIL)) return '/station/manage/mailing'
  if (hasPermission(StationPermission.STATION_MODULES)) return '/station/manage/modules'
  if (hasPermission(StationPermission.STATION_IMPORT_EXPORT)) return '/station/manage/import'
  return undefined
})

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup data-onboarding="nav.manage" :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'gears']" :label="t('sidebar.manage')" prefix="/station/manage" :to="manageDefaultRoute" name="station-manage" @navigate="close">
    <SidebarLink v-if="hasPermission(StationPermission.STATION_LOOK_AND_FEEL)" :icon="['fas', 'palette']" name="station-theme" to="/station/manage/theme" @navigate="close">
      {{ t('sidebar.stationTheme') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.STATION_MAIL)" data-onboarding="nav.manage.mailing" :icon="['fas', 'envelope']" name="station-mailing" to="/station/manage/mailing" @navigate="close">
      {{ t('sidebar.stationMailing') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.STATION_MODULES)" :icon="['fas', 'puzzle-piece']" name="station-modules" to="/station/manage/modules" @navigate="close">
      {{ t('sidebar.stationModules') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.STATION_GENERAL)" :icon="['fas', 'sitemap']" name="station-manage-cluster" to="/station/manage/cluster" @navigate="close">
      {{ t('sidebar.stationCluster') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.STATION_IMPORT_EXPORT)" :icon="['fas', 'file-import']" name="station-import" to="/station/manage/import" @navigate="close">
      {{ t('sidebar.stationImport') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.STATION_ADMINISTRATOR)" :icon="['fas', 'shield']" name="station-security" to="/station/manage/security" @navigate="close">
      {{ t('sidebar.stationSecurity') }}
    </SidebarLink>
  </SidebarGroup>
</template>
