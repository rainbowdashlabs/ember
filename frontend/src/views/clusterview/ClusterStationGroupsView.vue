/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import GroupListPanel from '@/views/stationview/members/groupsview/GroupListPanel.vue'
import GroupDetailPanel from '@/views/stationview/members/groupsview/GroupDetailPanel.vue'
import GroupFormModal from '@/views/stationview/members/groupsview/GroupFormModal.vue'
import GroupDeleteModal from '@/views/stationview/members/groupsview/GroupDeleteModal.vue'
import {useMemberAssignment} from '@/views/stationview/members/useMemberAssignment'
import {clusterStationGroups, clusterStations} from '@/api'
import {useGroupsConfig, type AssignableMember, type GroupsPort} from '@/composables/useGroupsConfig'

const {t} = useI18n()

/**
 * The association's stations in the shape the shared panels speak.
 *
 * <p>The panels identify what they hold by a number, because a station's members are rows. Stations
 * are not: the association's station API speaks uids throughout. Numbering them here is what lets the
 * one panel draw both, and the numbers never leave this file.
 */
const uids = ref<string[]>([])
const indexOf = computed(() => new Map(uids.value.map((uid, index) => [uid, index + 1])))
const uidAt = computed(() => new Map(uids.value.map((uid, index) => [index + 1, uid])))

function asAssignable(station: {stationUid: string; name: string}): AssignableMember {
  return {
    id: indexOf.value.get(station.stationUid) ?? 0,
    name: station.name,
    identity: {name: station.name},
  }
}

/** A group of stations holds no people, carries no colour, cannot become a tag and grants nothing. */
const port: GroupsPort = {
  listGroups: async () => (await clusterStationGroups.listGroups()).map(g => ({id: g.id, name: g.name})),
  listCandidates: async () => {
    const stations = await clusterStations.listStations()
    uids.value = stations.map(s => s.uid)
    return stations.map(s => asAssignable({stationUid: s.uid, name: s.name}))
  },
  getDetail: async (groupId) => ({
    members: (await clusterStationGroups.listStations(groupId)).map(asAssignable),
  }),
  createGroup: (patch) => clusterStationGroups.createGroup(patch.name),
  updateGroup: (groupId, patch) => clusterStationGroups.renameGroup(groupId, patch.name),
  deleteGroup: (groupId) => clusterStationGroups.deleteGroup(groupId),
  setMembers: (groupId, ids) => clusterStationGroups.setStations(groupId, toUids(ids)),
}

function toUids(ids: number[]): string[] {
  return ids.map(id => uidAt.value.get(id)).filter((uid): uid is string => !!uid)
}

const {
  groups, allMembers, allRoles, selectedGroup, groupMembers, groupRoles, groupRoleIds,
  groupLoading, loading, error, showGroupModal, editingGroup, groupName, groupColor,
  groupSaving, groupSaveError, selectGroup, openCreateGroup, openEditGroup, saveGroup,
  showDeleteModal, deleteTarget, requestDelete, confirmDelete,
} = useGroupsConfig(port, {hasColour: false, canConvertToTag: false, hasPermissions: false, holds: 'stations'})

const sortedGroupMembers = computed(() =>
    [...groupMembers.value].sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
)

const {
  availableMembers,
  addMember: addStationToGroup,
  removeMember: removeStationFromGroup,
} = useMemberAssignment(
    allMembers,
    groupMembers,
    async ids => {
      await clusterStationGroups.setStations(selectedGroup.value!.id, toUids(ids))
      const held = new Set(ids)
      return allMembers.value.filter(m => held.has(m.id))
    },
    error,
)
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-station-groups.subtitle')" :title="t('pages.cluster-station-groups.title')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || groupSaveError" variant="error">{{ error || groupSaveError }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <GroupListPanel :groups="groups" :selected-group="selectedGroup" :can-convert-to-tag="false"
                        @create="openCreateGroup" @select="selectGroup" @edit="openEditGroup"
                        @delete="requestDelete"/>
        <GroupDetailPanel v-if="selectedGroup" :selected-group="selectedGroup" :group-loading="groupLoading"
                          :sorted-group-members="sortedGroupMembers" :available-members="availableMembers"
                          :group-roles="groupRoles" :all-roles="allRoles" v-model:group-role-ids="groupRoleIds"
                          :can-edit-roles="false" @add-member="addStationToGroup"
                          @remove-member="removeStationFromGroup"/>
        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('clusterStationGroups.selectHint') }}
        </div>
      </div>

      <GroupFormModal v-model="showGroupModal" :is-edit="!!editingGroup" v-model:name="groupName"
                      v-model:color="groupColor" :saving="groupSaving" @save="saveGroup"/>
      <GroupDeleteModal v-model="showDeleteModal" :target="deleteTarget" @confirm="confirmDelete"/>
    </div>
  </ViewContent>
</template>
