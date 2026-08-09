/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {checklists, memberGroups, stationMembers, userTags} from '@/api'
import type {ChecklistCreateRequest, ChecklistSummary} from '@/api/checklists'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'
import ChecklistTile from './checklistindexview/ChecklistTile.vue'
import ChecklistCreateModal from './ChecklistCreateModal.vue'

const {t} = useI18n()
const router = useRouter()

const items = ref<ChecklistSummary[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<StationMember[]>([])
const showCreate = ref(false)

const {loading, error, reload} = useAsyncLoader(async () => {
  const [list, g, ts, m] = await Promise.all([
    checklists.listChecklists(),
    memberGroups.listGroups(),
    userTags.listTags(),
    stationMembers.listMembers(false),
  ])
  items.value = list
  groups.value = g
  tags.value = ts
  members.value = m
})

const {running: creating, error: createError, run: runCreate} = useAsyncAction(
    async (payload: ChecklistCreateRequest) => {
      const detail = await checklists.createChecklist(payload)
      showCreate.value = false
      await router.push({name: 'checklist-detail', params: {id: detail.id}})
    },
    {formatError: () => t('checklist.savingError')},
)

function onCreate(payload: ChecklistCreateRequest) {
  return runCreate(payload)
}

function open(item: ChecklistSummary) {
  router.push({name: 'checklist-detail', params: {id: item.id}})
}
</script>

<template>
  <ViewContent
      :title="t('pages.checklist-list.title')"
      :subtitle="t('pages.checklist-list.subtitle')"
  >
    <div class="flex flex-wrap items-end justify-end gap-3 mb-4">
      <PrimaryButton @click="showCreate = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('checklist.create') }}
      </PrimaryButton>
    </div>

    <Alert v-if="error || createError" variant="error" class="mb-4">{{ error || createError }}</Alert>

    <div v-if="loading" class="flex justify-center py-6"><Spinner/></div>

    <template v-else>
      <EmptyState v-if="items.length === 0">{{ t('checklist.empty') }}</EmptyState>
      <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <ChecklistTile
            v-for="item in items"
            :key="item.id"
            :item="item"
            @open="open(item)"
        />
      </div>
    </template>

    <ChecklistCreateModal
        v-model="showCreate"
        :creating="creating"
        :groups="groups"
        :tags="tags"
        :members="members"
        @submit="onCreate"
    />
  </ViewContent>
</template>
