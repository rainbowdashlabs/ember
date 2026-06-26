/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import WaitingListsGrid from './listview/WaitingListsGrid.vue'
import CreateListModal from './listview/CreateListModal.vue'
import type { WaitingListWithCount } from '@/api/types'
import { StationPermission } from '@/api/types'
import { waitingList } from '@/api'
import { useSession } from '@/composables/useSession'
import { useConfigPanel } from '@/composables/useConfigPanel'

const { t } = useI18n()
const router = useRouter()
const { hasPermission } = useSession()
const canEdit = computed(() => hasPermission(StationPermission.WAITLIST_EDIT))

const { config: lists, loading, error } = useConfigPanel<WaitingListWithCount[]>({
  initial: [],
  fetch: () => waitingList.listAll(),
})

const showCreateModal = ref(false)
const newName = ref('')
const newDescription = ref('')
const creating = ref(false)

function openCreate() {
  newName.value = ''
  newDescription.value = ''
  showCreateModal.value = true
}

async function createList() {
  if (!newName.value.trim()) return
  creating.value = true
  error.value = ''
  try {
    const created = await waitingList.create({
      name: newName.value.trim(),
      description: newDescription.value.trim(),
    })
    showCreateModal.value = false
    router.push({ name: 'waiting-list-detail', params: { id: created.id } })
  } catch {
    error.value = t('common.error')
  } finally {
    creating.value = false
  }
}

function navigateToDetail(id: number) {
  router.push({ name: 'waiting-list-detail', params: { id } })
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('waitingList.title') }}</SectionHeader>
        <PrimaryButton v-if="canEdit" :icon="['fas', 'plus']" @click="openCreate">
          {{ t('waitingList.create') }}
        </PrimaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="lists.length === 0">{{ t('waitingList.noLists') }}</EmptyState>
        <WaitingListsGrid v-else :lists="lists" @select="navigateToDetail" />
      </template>

      <CreateListModal
        v-model="showCreateModal"
        v-model:name="newName"
        v-model:description="newDescription"
        :creating="creating"
        @submit="createList"
      />
    </div>
  </ViewContent>
</template>
