/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useToast} from '@/composables/useToast'
import {checklists, memberGroups, stationMembers, userTags} from '@/api'
import type {
  ChecklistAddMembersResult,
  ChecklistCellDto,
  ChecklistDetail,
  ChecklistRefreshResult,
  MemberGroup,
  StationMember,
  UserTag,
} from '@/api/types'
import EditButton from '@/components/button/EditButton.vue'
import ChecklistMatrix from './checklistdetailview/ChecklistMatrix.vue'
import ChecklistFilterBar from './checklistdetailview/ChecklistFilterBar.vue'
import ChecklistRefreshButton from './checklistdetailview/ChecklistRefreshButton.vue'
import ChecklistExportMenu from './checklistdetailview/ChecklistExportMenu.vue'
import ChecklistAddMembersButton from './checklistdetailview/ChecklistAddMembersButton.vue'
import ChecklistAddMembersModal from './ChecklistAddMembersModal.vue'
import ChecklistEditModal from './ChecklistEditModal.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const detail = ref<ChecklistDetail | null>(null)
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<StationMember[]>([])

const memberSearch = ref('')
const columnFilters = ref<Record<number, 'any' | 'checked' | 'unchecked'>>({})
const showRemoved = ref(false)

const showAddMembers = ref(false)
const addingMembers = ref(false)
const showDeleteConfirm = ref(false)
const deleting = ref(false)
const showEditMeta = ref(false)
const savingMeta = ref(false)

const checklistId = computed(() => Number(route.params.id))

const {loading, error, reload} = useAsyncLoader(async () => {
  const [d, g, ts, m] = await Promise.all([
    checklists.getChecklist(checklistId.value),
    memberGroups.listGroups(),
    userTags.listTags(),
    stationMembers.listMembers(false),
  ])
  detail.value = d
  groups.value = g
  tags.value = ts
  members.value = m
})

watch(checklistId, () => reload())

const sortedEntries = computed(() =>
    [...(detail.value?.entries ?? [])].sort((a, b) =>
        a.memberName.localeCompare(b.memberName, 'de', {sensitivity: 'base'}),
    ),
)
const aliveEntries = computed(() => sortedEntries.value.filter(e => !e.deletedAt))
const allEntriesForFilter = computed(() => {
  if (!detail.value) return []
  return showRemoved.value ? sortedEntries.value : aliveEntries.value
})

function isChecked(entryId: number, columnId: number): boolean {
  const cell = detail.value?.cells.find(c => c.entryId === entryId && c.columnId === columnId)
  return cell?.checked ?? false
}

const visibleEntries = computed(() => {
  if (!detail.value) return []
  const search = memberSearch.value.trim().toLowerCase()
  const cols = detail.value.columns
  return allEntriesForFilter.value.filter(entry => {
    if (search && !entry.memberName.toLowerCase().includes(search)) return false
    for (const col of cols) {
      const want = columnFilters.value[col.id] ?? 'any'
      if (want === 'any') continue
      const checked = isChecked(entry.id, col.id)
      if (want === 'checked' && !checked) return false
      if (want === 'unchecked' && checked) return false
    }
    return true
  })
})

const aliveMemberIds = computed(() => new Set(aliveEntries.value.map(e => e.memberId)))
const removedEntries = computed(() => sortedEntries.value.filter(e => e.deletedAt != null))

function applyCell(cell: ChecklistCellDto) {
  if (!detail.value) return
  const idx = detail.value.cells.findIndex(c => c.entryId === cell.entryId && c.columnId === cell.columnId)
  if (idx >= 0) detail.value.cells.splice(idx, 1, cell)
  else detail.value.cells.push(cell)
}

async function onRefresh(): Promise<ChecklistRefreshResult> {
  if (!detail.value) throw new Error('Not loaded')
  const result = await checklists.refreshChecklist(detail.value.id)
  await reload()
  toast.show(
      t('checklist.refreshDone', {added: result.added, already: result.alreadyPresent}),
      'success',
  )
  return result
}

async function onAddMembers(memberIds: number[]) {
  if (!detail.value) return
  addingMembers.value = true
  try {
    const result: ChecklistAddMembersResult = await checklists.addMembers(detail.value.id, memberIds)
    showAddMembers.value = false
    await reload()
    toast.show(
        t('checklist.addedToast', {added: result.added, restored: result.restored, skipped: result.skipped}),
        'success',
    )
  } catch {
    error.value = t('checklist.savingError')
  }
  addingMembers.value = false
}

async function onDeleteEntry(entryId: number) {
  if (!detail.value) return
  await checklists.deleteEntry(detail.value.id, entryId)
  await reload()
}

async function onBulkSet(columnId: number, entryIds: number[], checked: boolean) {
  if (!detail.value || entryIds.length === 0) return
  const result = await checklists.bulkSetColumn(detail.value.id, columnId, {entryIds, checked})
  await reload()
  toast.show(t('checklist.bulkDone', {count: result.updated}), 'success')
}

async function onSaveMeta(payload: {name: string; description: string; orderedColumnIds: number[]}) {
  if (!detail.value) return
  savingMeta.value = true
  try {
    const updated = await checklists.updateChecklist(detail.value.id, {
      name: payload.name,
      description: payload.description,
    })
    detail.value.name = updated.name
    detail.value.description = updated.description

    const currentOrder = detail.value.columns.map(c => c.id).join(',')
    if (payload.orderedColumnIds.join(',') !== currentOrder) {
      const byId = new Map(detail.value.columns.map(c => [c.id, c]))
      for (let i = 0; i < payload.orderedColumnIds.length; i++) {
        const col = byId.get(payload.orderedColumnIds[i])
        if (!col || col.position === i) continue
        await checklists.updateColumn(detail.value.id, col.id, {
          label: col.label,
          description: col.description,
          position: i,
        })
      }
      await reload()
    }
    showEditMeta.value = false
  } catch {
    error.value = t('checklist.savingError')
  }
  savingMeta.value = false
}

async function confirmDeleteChecklist() {
  if (!detail.value) return
  deleting.value = true
  try {
    await checklists.deleteChecklist(detail.value.id)
    showDeleteConfirm.value = false
    await router.push({name: 'checklist-list'})
  } catch {
    error.value = t('checklist.savingError')
  }
  deleting.value = false
}
</script>

<template>
  <ViewContent
      :title="t('pages.checklist-detail.title')"
      :subtitle="t('pages.checklist-detail.subtitle')"
  >
    <div v-if="loading" class="flex justify-center py-6"><Spinner/></div>
    <Alert v-else-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <template v-else-if="detail">
      <div class="flex flex-wrap items-end justify-between gap-3 mb-3">
        <div class="min-w-0">
          <SecondaryButton class="mb-2" @click="router.push({name: 'checklist-list'})">
            <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>
            {{ t('checklist.backToList') }}
          </SecondaryButton>
          <PageHeader>
            <font-awesome-icon :icon="['fas', 'list-check']" class="mr-2"/>
            {{ detail.name }}
          </PageHeader>
          <p v-if="detail.description" class="text-sm text-(--text-muted)">{{ detail.description }}</p>
        </div>
        <div class="flex flex-wrap gap-2 items-center">
          <EditButton @click="showEditMeta = true">
            {{ t('checklist.editChecklist') }}
          </EditButton>
          <ChecklistRefreshButton :last-refreshed-at="detail.lastRefreshedAt" :on-refresh="onRefresh"/>
          <ChecklistAddMembersButton @click="showAddMembers = true"/>
          <ChecklistExportMenu :checklist-id="detail.id"/>
          <DeleteButton @click="showDeleteConfirm = true">
            {{ t('checklist.deleteChecklist') }}
          </DeleteButton>
        </div>
      </div>

      <ChecklistFilterBar
          v-model:search="memberSearch"
          v-model:show-removed="showRemoved"
          :removed-count="removedEntries.length"
      />

      <EmptyState v-if="aliveEntries.length === 0">
        {{ t('checklist.matrixEmpty') }}
      </EmptyState>
      <template v-else>
        <EmptyState v-if="visibleEntries.length === 0">{{ t('checklist.rowFilteredOut') }}</EmptyState>
        <ChecklistMatrix
            v-else
            :detail="detail"
            :visible-entries="visibleEntries"
            v-model:column-filters="columnFilters"
            @cell-change="applyCell"
            @delete-entry="onDeleteEntry"
            @bulk-set="onBulkSet"
            @columns-changed="reload"
        />
      </template>

      <ChecklistAddMembersModal
          v-model="showAddMembers"
          :adding="addingMembers"
          :members="members"
          :alive-member-ids="aliveMemberIds"
          :removed-entries="removedEntries"
          @submit="onAddMembers"
      />

      <ChecklistEditModal
          v-model="showEditMeta"
          :initial-name="detail.name"
          :initial-description="detail.description"
          :initial-columns="detail.columns"
          :saving="savingMeta"
          @submit="onSaveMeta"
      />

      <ConfirmDeleteModal
          v-model="showDeleteConfirm"
          :message="t('checklist.deleteChecklistMessage', {name: detail.name})"
          @confirm="confirmDeleteChecklist"
      />
    </template>
  </ViewContent>
</template>
