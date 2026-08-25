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
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :badge="counts.procedureCount" :icon="['fas', 'list-check']" :label="t('sidebar.procedures')" to="/station/procedures" name="procedure-list" @navigate="close">
    <SidebarLink v-if="hasPermission(StationPermission.PROCEDURE_MANAGER)" :icon="['fas', 'clipboard-list']" name="procedure-template-list" to="/station/procedures/templates" @navigate="close">
      {{ t('sidebar.procedureTemplates') }}
    </SidebarLink>
  </SidebarGroup>
</template>
