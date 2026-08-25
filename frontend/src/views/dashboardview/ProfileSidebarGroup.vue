/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
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
const {isGuardian} = useSession()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'user']" :label="t('sidebar.profile')" to="/station/profile" name="profile" @navigate="close">
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
</template>
