/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import GroupsScreen from '@/components/groups/GroupsScreen.vue'
import {clusterStationGroups, clusterStations} from '@/api'
import type {AssignableMember, GroupsCapabilities, GroupsPort} from '@/composables/useGroupsConfig'

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

function toUids(ids: number[]): string[] {
  return ids.map(id => uidAt.value.get(id)).filter((uid): uid is string => !!uid)
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

/** The page is open only to whoever may file stations, so everybody here may change the groups. */
const capabilities: GroupsCapabilities = {
  hasColour: false,
  canConvertToTag: false,
  hasPermissions: false,
  permissionScope: 'cluster',
  holds: 'stations',
  canEdit: true,
}
</script>

<template>
  <GroupsScreen :title="t('pages.cluster-station-groups.title')" :subtitle="t('pages.cluster-station-groups.subtitle')"
                :select-hint="t('clusterStationGroups.selectHint')" :port="port" :capabilities="capabilities"
                :can-edit-roles="false"/>
</template>
