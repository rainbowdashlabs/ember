/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type { WaitingListWithCount } from '@/api/types'
import { StationPermission } from '@/api/types'
import { waitingList } from '@/api'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const { hasPermission } = useSession()
const canEdit = computed(() => hasPermission(StationPermission.WAITLIST_EDIT))

const lists = ref<WaitingListWithCount[]>([])
const loading = ref(true)
const error = ref('')

const showCreateModal = ref(false)
const newName = ref('')
const newDescription = ref('')
const creating = ref(false)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    lists.value = await waitingList.listAll()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString()
}

onMounted(loadData)
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

        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <NeutralContainer
            v-for="item in lists"
            :key="item.list.id"
            class="cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all"
            @click="navigateToDetail(item.list.id)"
          >
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <SubHeader>{{ item.list.name }}</SubHeader>
                <span class="text-sm font-medium bg-primary/15 text-primary rounded-full px-3 py-0.5">
                  {{ item.entryCount }}
                </span>
              </div>
              <p v-if="item.list.description" class="text-sm text-(--text-muted) line-clamp-2">
                {{ item.list.description }}
              </p>
              <p class="text-xs text-(--text-muted)">
                {{ t('waitingList.createdAt') }}: {{ formatDate(item.list.createdAt) }}
              </p>
            </div>
          </NeutralContainer>
        </div>
      </template>

      <Modal v-model="showCreateModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.createTitle') }}</SectionHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.name') }}</FieldLabel>
            <TextInput v-model="newName" :placeholder="t('waitingList.namePlaceholder')" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.description') }}</FieldLabel>
            <TextAreaInput v-model="newDescription" :placeholder="t('waitingList.descriptionPlaceholder')" />
          </div>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="creating || !newName.trim()" @click="createList">
              {{ creating ? t('common.loading') : t('waitingList.create') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
