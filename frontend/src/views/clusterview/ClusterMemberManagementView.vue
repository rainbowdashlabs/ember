/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ManagedMemberRow from './clustermembermanagementview/ManagedMemberRow.vue'
import {clusterMembers} from '@/api'
import type {ManagedMember, ManagedStation} from '@/api/clusterMembers'

const {t} = useI18n()

const PAGE_SIZE = 50

const query = ref('')
const stationUid = ref('')
const includeFormer = ref(false)
const page = ref(0)

const loading = ref(true)
const busy = ref(false)
const error = ref('')
const members = ref<ManagedMember[]>([])
const total = ref(0)
const stations = ref<ManagedStation[]>([])

async function search() {
  loading.value = true
  error.value = ''
  try {
    const result = await clusterMembers.searchManagedMembers({
      q: query.value || undefined,
      stationUid: stationUid.value || undefined,
      includeFormer: includeFormer.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    members.value = result.members
    total.value = result.total
  } catch {
    error.value = t('clusterMemberManagement.loadFailed')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  stations.value = await clusterMembers.listManagedStations().catch(() => [])
  await search()
})

// A changed filter starts again at the first page, or the reader lands on an empty one
watch([query, stationUid, includeFormer], () => {
  page.value = 0
  void search()
})
watch(page, () => void search())

async function act(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await search()
  } catch {
    error.value = t('clusterMemberManagement.actionFailed')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-member-management.subtitle')"
               :title="t('pages.cluster-member-management.title')">
    <div class="space-y-4">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer class="flex flex-wrap items-center gap-3">
        <SearchInput v-model="query" :placeholder="t('clusterMemberManagement.searchPlaceholder')" class="flex-1"/>
        <SelectInput v-model="stationUid" class="w-56">
          <option value="">{{ t('clusterMemberManagement.allStations') }}</option>
          <option v-for="station in stations" :key="station.uid" :value="station.uid">{{ station.name }}</option>
        </SelectInput>
        <label class="flex items-center gap-2 text-sm">
          <CheckboxInput v-model="includeFormer"/>
          <span>{{ t('clusterMemberManagement.includeFormer') }}</span>
        </label>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="members.length === 0">{{ t('clusterMemberManagement.empty') }}</EmptyState>
        <template v-else>
          <p class="text-sm text-(--text-muted)">{{ t('clusterMemberManagement.count', {count: total}) }}</p>
          <div class="space-y-2">
            <ManagedMemberRow
                v-for="member in members"
                :key="member.id"
                :busy="busy"
                :member="member"
                @archive="id => act(() => clusterMembers.archiveManagedMember(id))"
                @user-type="(id, type) => act(() => clusterMembers.setManagedUserType(id, type))"
            />
          </div>

          <div v-if="total > PAGE_SIZE" class="flex items-center justify-between gap-3">
            <SecondaryButton :disabled="page === 0" @click="page = page - 1">{{ t('common.previous') }}</SecondaryButton>
            <SecondaryButton :disabled="(page + 1) * PAGE_SIZE >= total" @click="page = page + 1">
              {{ t('common.next') }}
            </SecondaryButton>
          </div>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
