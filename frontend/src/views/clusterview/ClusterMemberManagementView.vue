/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MemberListPanel from '@/views/stationview/members/listview/MemberListPanel.vue'
import {useMemberListConfig, type MemberListPort} from '@/views/stationview/members/listview/useMemberListConfig'
import {provideMemberRowExtras} from '@/views/stationview/members/listview/memberRowExtras'
import {useClusterMemberSource, MANAGED_MEMBER_CAP} from './clustermembersview/clusterMemberSource'
import {clusterMembers} from '@/api'
import type {ManagedStation} from '@/api/clusterMembers'
import {ClusterPermission} from '@/api/clusters'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {hasClusterPermission, sessionInfo} = useSession()

const stationUid = ref('')
const includeFormer = ref(false)
const stations = ref<ManagedStation[]>([])

const {source, managed, overflowed} = useClusterMemberSource(() => includeFormer.value)

/**
 * An association reads across its stations and reaches its own copies of the member screens. It
 * offers no column for a station's own groups or tags, because those belong to one station and most
 * of the list would have nothing under them.
 */
const port: MemberListPort = {
  source,
  // One screen, not two: the association's copy shows what is asked of somebody and lets it be
  // answered on the same page, so there is nothing a separate edit screen would add.
  routes: {detail: 'cluster-member-detail'},
  canExport: computed(() => hasClusterPermission(ClusterPermission.CLUSTER_MEMBER_EXPORT)),
  canEdit: computed(() => hasClusterPermission(ClusterPermission.CLUSTER_MEMBER_MANAGER)),
  exportFileName: 'verbandsmitglieder',
}

const config = useMemberListConfig(port)

/**
 * Whose row offers nothing, and why.
 *
 * <p>An association's member manager may not touch a station's owner, and may not touch their own
 * membership of any station in the association. Both are refused by the server; saying so here means
 * nobody has to find out by pressing something.
 */
function blockedReason(memberId: number): string {
  const person = managed.value.get(memberId)
  if (!person) return ''
  if (person.stationOwner) return t('clusterMemberManagement.blockedOwner')
  if (person.id === sessionInfo.value?.member?.id) return t('clusterMemberManagement.blockedSelf')
  return ''
}

/** Which station somebody belongs to, which a station's own list never has to say. */
function stationNameOf(memberId: number): string {
  return managed.value.get(memberId)?.stationName ?? ''
}

provideMemberRowExtras({note: stationNameOf, blockedReason, stationLocalColumns: false})

const shownMembers = computed(() => {
  if (!stationUid.value) return config.sortedMembers.value
  return config.sortedMembers.value.filter(m => managed.value.get(m.id)?.stationUid === stationUid.value)
})

onMounted(async () => {
  stations.value = await clusterMembers.listManagedStations().catch(() => [])
})

watch(includeFormer, () => { void config.reload() })
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-member-management.subtitle')"
               :title="t('pages.cluster-member-management.title')">
    <div class="space-y-4">
      <Alert v-if="overflowed" variant="info">
        {{ t('clusterMemberManagement.tooMany', {count: MANAGED_MEMBER_CAP}) }}
      </Alert>

      <NeutralContainer class="flex flex-wrap items-center gap-3">
        <SelectInput v-model="stationUid" class="w-56">
          <option value="">{{ t('clusterMemberManagement.allStations') }}</option>
          <option v-for="station in stations" :key="station.uid" :value="station.uid">{{ station.name }}</option>
        </SelectInput>
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="includeFormer"/>
          <span>{{ t('clusterMemberManagement.includeFormer') }}</span>
        </label>
      </NeutralContainer>

      <MemberListPanel :config="config" :members="shownMembers"/>
    </div>
  </ViewContent>
</template>
