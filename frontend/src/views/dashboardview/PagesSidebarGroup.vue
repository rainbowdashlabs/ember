/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'

defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'file-lines']" :label="t('sidebar.pages')" to="/station/pages" name="pages-list" @navigate="close">
    <SidebarLink :icon="['fas', 'clipboard-list']" name="pages-forms" to="/station/pages/forms" @navigate="close">
      {{ t('sidebar.pagesForms') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'square-poll-vertical']" name="pages-polls" to="/station/pages/polls" @navigate="close">
      {{ t('sidebar.pagesPolls') }}
    </SidebarLink>
  </SidebarGroup>
</template>
