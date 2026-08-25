/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useFederatedBoardBookmarks} from '@/composables/useFederatedBoardBookmarks'
import {useSidebarBoards} from '@/views/dashboardview/useSidebarBoards'

defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const route = useRoute()
const {hasPermission} = useSession()
const {boards: visibleBoards} = useSidebarBoards()
const {bookmarks: bookmarkedBoards} = useFederatedBoardBookmarks()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :icon="['fas', 'table-columns']" :label="t('sidebar.boards')" to="/station/boards" name="board-list" @navigate="close">
    <SidebarLink v-for="board in visibleBoards" :key="board.id" :icon="['fas', 'table-columns']" :name="`board-${board.id}`" :to="`/station/boards/${board.shortKey}`" :active="route.path.startsWith(`/station/boards/${board.shortKey}`)" @navigate="close">
      {{ board.name }}
    </SidebarLink>
    <SidebarLink v-for="bm in bookmarkedBoards" :key="`fed-${bm.id}`" :icon="['fas', 'table-columns']" :name="`fed-board-${bm.id}`" :to="`/station/federation/boards/${bm.partnerStationUid}/${bm.remoteBoardShortKey}`" :active="route.path.startsWith(`/station/federation/boards/${bm.partnerStationUid}/${bm.remoteBoardShortKey}`)" @navigate="close">
      <span class="flex items-center gap-1.5">
        {{ bm.remoteBoardName }}
        <SecondaryBadge class="shrink-0">
          <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-2.5 h-2.5" />
        </SecondaryBadge>
      </span>
    </SidebarLink>
    <SidebarLink :icon="['fas', 'globe']" name="federated-boards" to="/station/federation/boards" @navigate="close">
      {{ t('boards.federatedBoards') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.BOARD_EDIT)" :icon="['fas', 'gears']" name="board-manage" to="/station/boards/manage" @navigate="close">
      {{ t('sidebar.boardManage') }}
    </SidebarLink>
  </SidebarGroup>
</template>
