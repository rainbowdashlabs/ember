/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import Modal from '@/components/feedback/Modal.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import { useSession } from '@/composables/useSession'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { procedures } from '@/api'
import type {ProcedureListParams} from '@/api/procedures'
import {ProcedureStatus, StationPermission, type Procedure} from '@/api/generated/schema'
import { formatDate } from '@/util/format'

const { t } = useI18n()
const router = useRouter()
const { hasPermission, loaded } = useSession()

const canEdit = computed(() => hasPermission(StationPermission.PROCEDURE_EDIT))

const items = ref<Procedure[]>([])

const searchQuery = ref('')
const statusFilter = ref<ProcedureStatus | ''>(ProcedureStatus.OPEN)
const assigneeFilter = ref<string>(canEdit.value ? 'all' : 'me')

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  const params: ProcedureListParams = {}
  if (statusFilter.value) params.status = statusFilter.value
  if (assigneeFilter.value === 'me') params.assignee = 'me'
  const found = await procedures.getProcedures(params)
  if (!isCurrent()) return
  items.value = found
}, {autoLoad: false})

const {
  show: showDeleteModal,
  target: deleteTarget,
  request: requestDelete,
  confirm: handleDelete,
} = useConfirmAction<Procedure>({
  onConfirm: p => procedures.deleteProcedure(p.id),
  onSuccess: () => reload(),
  failure,
})

const filteredItems = computed(() => {
  let result = items.value
  if (statusFilter.value) {
    result = result.filter(p => p.status === statusFilter.value)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    result = result.filter(p => p.name.toLowerCase().includes(q) || (p.description && p.description.toLowerCase().includes(q)))
  }
  return result
})

function procedurePage(p: Procedure) {
  return { name: 'procedure-detail', params: { id: p.id } }
}

watch([statusFilter, assigneeFilter], () => reload())
watch(loaded, (v) => { if (v) reload() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="t('pages.procedure-list.title')"
      :subtitle="t('pages.procedure-list.subtitle')"
  >
    <ButtonRow align="end" class="mb-4">
      <PrimaryButton v-if="canEdit" @click="router.push({ name: 'procedure-create' })">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('procedures.createProcedure') }}
      </PrimaryButton>
    </ButtonRow>

    <div class="mb-4">
      <SearchInput v-model="searchQuery" :placeholder="t('procedures.search')" />
    </div>

    <div class="flex flex-wrap items-center gap-2 mb-4">
      <SelectionToggleButton :selected="statusFilter === ProcedureStatus.OPEN" @toggle="statusFilter = statusFilter === ProcedureStatus.OPEN ? '' : ProcedureStatus.OPEN">
        {{ t('procedures.open') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="statusFilter === ProcedureStatus.RESOLVED" @toggle="statusFilter = statusFilter === ProcedureStatus.RESOLVED ? '' : ProcedureStatus.RESOLVED">
        {{ t('procedures.resolved') }}
      </SelectionToggleButton>
      <span class="text-[var(--text-muted)] mx-1">|</span>
      <SelectionToggleButton v-if="canEdit" :selected="assigneeFilter === 'me'" @toggle="assigneeFilter = assigneeFilter === 'me' ? 'all' : 'me'">
        {{ t('procedures.filterMine') }}
      </SelectionToggleButton>
      <SelectionToggleButton v-if="canEdit" :selected="assigneeFilter === 'all'" @toggle="assigneeFilter = assigneeFilter === 'all' ? 'me' : 'all'">
        {{ t('procedures.filterAll') }}
      </SelectionToggleButton>
    </div>

    <AsyncSection
      :empty="filteredItems.length === 0"
      :empty-message="t('procedures.empty')"
      :failure="failure"
      :loading="loading"
    >
      <div class="space-y-2">
        <RowLink v-for="p in filteredItems" :key="p.id" :to="procedurePage(p)">
          <NeutralContainer
            data-testid="procedure-entry"
            class="flex items-center gap-3 cursor-pointer hover:border-[var(--color-primary)] transition-colors group"
          >
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <span class="font-medium">{{ p.name }}</span>
                <SuccessBadge v-if="p.status === ProcedureStatus.RESOLVED">{{ t('procedures.resolved') }}</SuccessBadge>
                <PrimaryBadge v-else>{{ t('procedures.open') }}</PrimaryBadge>
              </div>
              <div v-if="p.description" class="text-sm text-[var(--text-muted)] truncate">{{ p.description }}</div>
            </div>
            <div class="flex items-center gap-3 text-sm text-[var(--text-muted)] shrink-0">
              <span v-if="p.dueAt" class="flex items-center gap-1">
                <font-awesome-icon :icon="['fas', 'calendar']" class="w-3 h-3" />
                {{ formatDate(p.dueAt) }}
              </span>
            </div>
            <div v-if="canEdit" class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <DeleteButton :label="t('common.delete')" @click="requestDelete(p)" />
            </div>
          </NeutralContainer>
        </RowLink>
      </div>
    </AsyncSection>

    <Modal v-model="showDeleteModal">
      <SubHeader class="mb-3">{{ t('procedures.deleteConfirm') }}</SubHeader>
      <p class="mb-4">{{ deleteTarget?.name }}</p>
      <div class="flex gap-2 justify-end">
        <PrimaryButton @click="handleDelete">{{ t('common.delete') }}</PrimaryButton>
      </div>
    </Modal>
  </ViewContent>
</template>
