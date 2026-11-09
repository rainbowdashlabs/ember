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
import Modal from '@/components/feedback/Modal.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ProcedureListRow from './procedurelistview/ProcedureListRow.vue'
import { matchesSearch } from './procedurelistview/procedureSearch'
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
/** The public procedures about the reader or somebody in their care, which they read among their own tasks. */
const aboutMe = ref<Procedure[]>([])

const searchQuery = ref('')
const statusFilter = ref<ProcedureStatus | ''>(ProcedureStatus.OPEN)
const assigneeFilter = ref<string>(canEdit.value ? 'all' : 'me')

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  const params: ProcedureListParams = {}
  if (statusFilter.value) params.status = statusFilter.value
  if (assigneeFilter.value === 'me') params.assignee = 'me'
  const [found, concerning] = await Promise.all([
    procedures.getProcedures(params),
    procedures.listAboutMe(statusFilter.value || undefined),
  ])
  if (!isCurrent()) return
  items.value = found
  aboutMe.value = concerning
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

/** The procedures about the reader that the main list does not show already, narrowed by the search. */
const filteredAboutMe = computed(() => {
  const shown = new Set(items.value.map(p => p.id))
  return aboutMe.value.filter(p => !shown.has(p.id) && matchesSearch(p, searchQuery.value))
})

const filteredItems = computed(() => items.value.filter(p =>
  (!statusFilter.value || p.status === statusFilter.value) && matchesSearch(p, searchQuery.value)))

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

    <section v-if="filteredAboutMe.length > 0" class="mb-6" data-testid="procedures-about-me">
      <SubHeader class="mb-1">{{ t('procedures.aboutMe') }}</SubHeader>
      <p class="text-sm text-[var(--text-muted)] mb-2">{{ t('procedures.aboutMeHint') }}</p>
      <div class="space-y-2">
        <ProcedureListRow v-for="p in filteredAboutMe" :key="p.id" :procedure="p"/>
      </div>
    </section>

    <AsyncSection
      :empty="filteredItems.length === 0"
      :empty-message="t('procedures.empty')"
      :failure="failure"
      :loading="loading"
    >
      <div class="space-y-2">
        <ProcedureListRow v-for="p in filteredItems" :key="p.id" :procedure="p" data-testid="procedure-entry">
          <div class="flex items-center gap-3 text-sm text-[var(--text-muted)] shrink-0">
            <span v-if="p.dueAt" class="flex items-center gap-1">
              <font-awesome-icon :icon="['fas', 'calendar']" class="w-3 h-3" />
              {{ formatDate(p.dueAt) }}
            </span>
          </div>
          <div v-if="canEdit" class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <DeleteButton :label="t('common.delete')" @click="requestDelete(p)" />
          </div>
        </ProcedureListRow>
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
