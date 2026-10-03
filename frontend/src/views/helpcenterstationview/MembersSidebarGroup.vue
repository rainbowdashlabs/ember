/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'

defineProps<{
  close: () => void
}>()

const {t} = useI18n()
</script>

<template>
  <SidebarGroup :icon="['fas', 'users']" :label="t('sidebar.members')"
                to="/helpcenter/station/members" name="help-members-module-overview" @navigate="close">
    <SidebarLink :icon="['fas', 'user-plus']" name="help-members-create"
                 to="/helpcenter/station/members/create" @navigate="close">
      {{ t('sidebar.create') }}
    </SidebarLink>
    <SidebarExpandableLink :icon="['fas', 'upload']" name="help-members-import"
                           to="/helpcenter/station/members/import" @navigate="close">
      <template #label>{{ t('sidebar.membersImport') }}</template>
      <SidebarLink :icon="['fas', 'users']" name="help-members-import-team"
                   to="/helpcenter/station/members/import-team" @navigate="close">
        {{ t('sidebar.membersImportTeam') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarLink :icon="['fas', 'list']" name="help-members-list" to="/helpcenter/station/members/list"
                 @navigate="close">
      {{ t('sidebar.list') }}
    </SidebarLink>
    <SidebarExpandableLink :icon="['fas', 'eye']" name="help-members-detail"
                           to="/helpcenter/station/members/detail" @navigate="close">
      <template #label>{{ t('sidebar.detail') }}</template>
      <SidebarLink :icon="['fas', 'pen']" name="help-members-edit"
                   to="/helpcenter/station/members/edit" @navigate="close">
        {{ t('sidebar.edit') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarLink :icon="['fas', 'layer-group']" name="help-members-groups"
                 to="/helpcenter/station/members/groups" @navigate="close">
      {{ t('sidebar.groups') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'shield']" name="help-members-type-permissions"
                 to="/helpcenter/station/members/type-permissions" @navigate="close">
      {{ t('sidebar.typePermissions') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'hashtag']" name="help-members-tags" to="/helpcenter/station/members/tags"
                 @navigate="close">
      {{ t('sidebar.tags') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'bell']" name="help-members-changes"
                 to="/helpcenter/station/members/changes" @navigate="close">
      {{ t('sidebar.changes') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'user-slash']" name="help-members-former"
                 to="/helpcenter/station/members/former" @navigate="close">
      {{ t('sidebar.formerMembers') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'clipboard-list']" name="help-waiting-lists"
                 to="/helpcenter/station/members/waiting-lists" @navigate="close">
      {{ t('sidebar.waitingLists') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'users-gear']" name="help-station-members-config"
                 to="/helpcenter/station/members/config" @navigate="close">
      {{ t('sidebar.membersConfig') }}
    </SidebarLink>
  </SidebarGroup>
</template>
