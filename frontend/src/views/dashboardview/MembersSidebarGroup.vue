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
import {StationModules, StationPermission} from '@/api/types'
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
const {hasPermission, canManageMembers, hasAnyWaitlistPermission, isModuleEnabled} = useSession()
const {counts} = useSidebarCounts()

const membersDefaultRoute = computed(() => {
  if (hasPermission(StationPermission.MEMBER_READ)) return '/station/members/list'
  return undefined
})

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" @update:open-group="v => emit('update:openGroup', v)" :badge="counts.pendingChanges + counts.waitingListEntries" :icon="['fas', 'users']"
                :label="t('sidebar.members')" prefix="/station/members" :to="membersDefaultRoute" name="members-list" @navigate="close">
    <SidebarLink v-if="hasPermission(StationPermission.MEMBER_EDIT)" :icon="['fas', 'user-plus']" name="members-create" to="/station/members/create" @navigate="close">
      {{ t('sidebar.create') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.MEMBER_MANAGE_GROUP)" :icon="['fas', 'layer-group']" name="members-groups" to="/station/members/groups"
                 @navigate="close">
      {{ t('sidebar.groups') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.MEMBER_MANAGE_TAGS)" :icon="['fas', 'hashtag']" name="members-tags" to="/station/members/tags"
                 @navigate="close">
      {{ t('sidebar.tags') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.MEMBER_READ)" :icon="['fas', 'file']" name="member-documents" to="/station/members/documents"
                 @navigate="close">
      {{ t('sidebar.documents') }}
    </SidebarLink>
    <SidebarLink v-if="canManageMembers()" :icon="['fas', 'shield']" name="members-type-permissions" to="/station/members/type-permissions"
                 @navigate="close">
      {{ t('sidebar.typePermissions') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.MEMBER_CHANGES)" :badge="counts.pendingChanges" :icon="['fas', 'bell']" name="members-changes"
                 to="/station/members/changes" @navigate="close">
      {{ t('sidebar.changes') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.MEMBER_EDIT)" :icon="['fas', 'user-slash']" name="members-former" to="/station/members/former"
                 @navigate="close">
      {{ t('sidebar.formerMembers') }}
    </SidebarLink>
    <SidebarLink v-if="isModuleEnabled(StationModules.WAITING_LIST) && hasAnyWaitlistPermission()"
                 :badge="counts.waitingListEntries" :icon="['fas', 'clipboard-list']" name="waiting-lists" to="/station/members/waiting-lists"
                 @navigate="close">
      {{ t('sidebar.waitingLists') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.MEMBER_FIELDS)" :icon="['fas', 'users-gear']" name="station-members-config" to="/station/members/config"
                 @navigate="close">
      {{ t('sidebar.membersConfig') }}
    </SidebarLink>
  </SidebarGroup>
</template>
