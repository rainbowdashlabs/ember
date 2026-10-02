/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import GroupsScreen from '@/components/groups/GroupsScreen.vue'
import {clusterMembers, data} from '@/api'
import {clusterMemberIdentity} from '@/api/clusterMembers'
import type {ClusterMemberResponse} from '@/api/generated/schema'
import type {PermissionGrant} from '@/composables/usePermissionTree'
import {ClusterPermission} from '@/api/clusters'
import {useSession} from '@/composables/useSession'
import type {GroupsCapabilities, GroupsPort} from '@/composables/useGroupsConfig'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

/**
 * Every write on this screen is the association administrator's, so only they are offered one. Followed
 * rather than read once, because the session may still be on its way when the screen opens.
 */
const editable = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_ADMINISTRATOR))

/**
 * The association's permissions in the shape the shared picker speaks.
 *
 * <p>The picker identifies a selection by a numeric grant id, because a station's permissions are
 * rows. An association's are not: its API speaks their names throughout. Numbering them here is what
 * lets the one picker draw both, and the numbers never leave this file.
 */
const grants = ref<PermissionGrant[]>([])
const idByName = computed(() => new Map(grants.value.map(g => [g.permission, g.id])))
const nameById = computed(() => new Map(grants.value.map(g => [g.id, g.permission])))

/**
 * A member in the shape the shared group panels draw a person in.
 *
 * <p>Those panels read one field for the whole row: the identity. An association's people are accounts
 * rather than members of a station, so the identity is built from the account here instead of arriving
 * from the server, and without it every row showed a blank name beside an empty avatar.
 */
function drawable(member: ClusterMemberResponse) {
  return {...member, identity: clusterMemberIdentity(member)}
}

/** An association's group gathers the people who run it, carries no colour and cannot become a tag. */
const port: GroupsPort = {
  listGroups: () => clusterMembers.listGroups(),
  listCandidates: async () => (await clusterMembers.listMembers()).map(drawable),
  listAllRoles: async () => {
    const hierarchy = await data.getClusterPermissionHierarchy().catch(() => [])
    grants.value = hierarchy.map((node, index) => ({id: index + 1, permission: node.name}))
    return grants.value
  },
  getDetail: async (groupId) => {
    const [detail, all] = await Promise.all([
      clusterMembers.getGroup(groupId),
      clusterMembers.listMembers(),
    ])
    const held = new Set(detail.memberIds)
    return {
      members: all.filter(m => held.has(m.id)).map(drawable),
      roles: detail.permissions
          .map(name => idByName.value.get(name))
          .filter((id): id is number => !!id)
          .map(id => ({id, permission: nameById.value.get(id) ?? ''})),
    }
  },
  createGroup: (patch) => clusterMembers.createGroup(patch.name),
  updateGroup: (groupId, patch) => clusterMembers.updateGroup(groupId, {name: patch.name}),
  deleteGroup: (groupId) => clusterMembers.deleteGroup(groupId),
  setMembers: (groupId, memberIds) => clusterMembers.updateGroup(groupId, {memberIds}),
  setRoles: async (groupId, roleIds) => {
    const names = roleIds.map(id => nameById.value.get(id)).filter((n): n is string => !!n)
    await clusterMembers.updateGroup(groupId, {permissions: names})
    return roleIds.map(id => ({id, permission: nameById.value.get(id) ?? ''}))
  },
}

const capabilities: GroupsCapabilities = reactive({
  hasColour: false,
  canConvertToTag: false,
  hasPermissions: true,
  permissionScope: 'cluster' as const,
  holds: 'members' as const,
  canEdit: editable,
})
</script>

<template>
  <GroupsScreen :title="t('pages.cluster-member-groups.title')" :subtitle="t('pages.cluster-member-groups.subtitle')"
                :select-hint="t('memberGroups.selectHint')" :port="port" :capabilities="capabilities"
                :can-edit-roles="editable"/>
</template>
