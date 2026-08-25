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

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'square-poll-vertical']" :label="t('sidebar.forms')" to="/station/forms" name="forms-list" @navigate="close">
    <SidebarLink v-if="hasPermission(StationPermission.POLL_CREATE)" :icon="['fas', 'plus']" name="forms-create" to="/station/forms/create"
                 @navigate="close">
      {{ t('sidebar.formsCreate') }}
    </SidebarLink>
  </SidebarGroup>
</template>
