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
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import { useSession } from '@/composables/useSession'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { procedures } from '@/api'
import { StationPermission } from '@/api/types'
import type { Procedure, ProcedureTemplate } from '@/api/procedures'
import { ProcedureStatus } from '@/api/procedures'

const { t } = useI18n()
const router = useRouter()
const { hasPermission, loaded } = useSession()

const canEdit = computed(() => hasPermission(StationPermission.PROCEDURE_EDIT))
const canManage = computed(() => hasPermission(StationPermission.PROCEDURE_MANAGER))

const items = ref<Procedure[]>([])
const templates = ref<ProcedureTemplate[]>([])

const searchQuery = ref('')
const statusFilter = ref<string>(ProcedureStatus.OPEN)
const assigneeFilter = ref<string>(canEdit.value ? 'all' : 'me')

// Create modal
const showCreateModal = ref(false)
const createMode = ref<'manual' | 'template'>('manual')
const newName = ref('')
const newDescription = ref('')
const newDueAt = ref('')
const newTemplateId = ref<number | null>(null)

const {loading, error, reload} = useAsyncLoader(async () => {
  const params: { status?: string; assignee?: string } = {}
  if (statusFilter.value) params.status = statusFilter.value
  if (assigneeFilter.value === 'me') params.assignee = 'me'
  items.value = await procedures.getProcedures(params)
}, {autoLoad: false})

const {
  show: showDeleteModal,
  target: deleteTarget,
  request: requestDelete,
  confirm: handleDelete,
} = useConfirmAction<Procedure>({
  onConfirm: p => procedures.deleteProcedure(p.id),
  onSuccess: () => reload(),
  error,
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

async function loadTemplates() {
  try {
    templates.value = (await procedures.getTemplates()).filter(tpl => !tpl.archived)
  } catch { /* ignore */ }
}

function openCreateModal() {
  newName.value = ''
  newDescription.value = ''
  newDueAt.value = ''
  newTemplateId.value = null
  createMode.value = 'manual'
  showCreateModal.value = true
  loadTemplates()
}

async function handleCreate() {
  if (createMode.value === 'manual' && !newName.value.trim()) return
  if (createMode.value === 'template' && newTemplateId.value == null) return
  try {
    const data: Record<string, unknown> = {}
    if (createMode.value === 'template') {
      data.templateId = newTemplateId.value
    } else {
      data.name = newName.value.trim()
      data.description = newDescription.value || undefined
    }
    if (newDueAt.value) data.dueAt = newDueAt.value
    const created = await procedures.createProcedure(data as Parameters<typeof procedures.createProcedure>[0])
    showCreateModal.value = false
    router.push({ name: 'procedure-detail', params: { id: created.id } })
  } catch {
    error.value = t('common.error')
  }
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE')
}

watch([statusFilter, assigneeFilter], () => reload())
watch(loaded, (v) => { if (v) reload() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="t('pages.procedure-list.title')"
      :subtitle="t('pages.procedure-list.subtitle')"
  >
    <div class="flex items-center justify-between mb-4">
      <div />
      <PrimaryButton v-if="canEdit" @click="router.push({ name: 'procedure-create' })">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('procedures.createProcedure') }}
      </PrimaryButton>
    </div>

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

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-if="!loading && filteredItems.length === 0" class="text-center text-[var(--text-muted)] py-8">
      {{ t('procedures.empty') }}
    </div>

    <div class="space-y-2">
      <NeutralContainer
        v-for="p in filteredItems"
        :key="p.id"
        class="flex items-center gap-3 cursor-pointer hover:border-[var(--primary)] transition-colors group"
        @click="router.push({ name: 'procedure-detail', params: { id: p.id } })"
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
          <DeleteButton :label="t('common.delete')" @click.stop="requestDelete(p)" />
        </div>
      </NeutralContainer>
    </div>

    <!-- Create Modal -->
    <Modal v-model="showCreateModal">
      <SubHeader class="mb-3">{{ t('procedures.createProcedure') }}</SubHeader>
      <div class="flex gap-2 mb-4">
        <SelectionToggleButton :selected="createMode === 'manual'" @toggle="createMode = 'manual'">
          {{ t('procedures.createManual') }}
        </SelectionToggleButton>
        <SelectionToggleButton :selected="createMode === 'template'" @toggle="createMode = 'template'">
          {{ t('procedures.createFromTemplate') }}
        </SelectionToggleButton>
      </div>
      <form @submit.prevent="handleCreate" class="space-y-3">
        <template v-if="createMode === 'template'">
          <FieldLabel class="mb-1">{{ t('procedures.selectTemplate') }}</FieldLabel>
          <div v-if="templates.length === 0" class="text-sm text-[var(--text-muted)]">{{ t('procedures.noTemplates') }}</div>
          <SelectInput v-else :model-value="newTemplateId != null ? String(newTemplateId) : ''" @update:model-value="(v: string | undefined) => { newTemplateId = v ? Number(v) : null }">
            <option value="">—</option>
            <option v-for="tpl in templates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
          </SelectInput>
        </template>
        <template v-else>
          <TextInput v-model="newName" :placeholder="t('procedures.name')" required />
          <TextAreaInput v-model="newDescription" :placeholder="t('procedures.description')" />
        </template>
        <div>
          <FieldLabel class="mb-1">{{ t('procedures.dueDate') }}</FieldLabel>
          <DateInput v-model="newDueAt" />
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('procedures.createProcedure') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Delete Modal -->
    <Modal v-model="showDeleteModal">
      <SubHeader class="mb-3">{{ t('procedures.deleteConfirm') }}</SubHeader>
      <p class="mb-4">{{ deleteTarget?.name }}</p>
      <div class="flex gap-2 justify-end">
        <PrimaryButton @click="handleDelete">{{ t('common.delete') }}</PrimaryButton>
      </div>
    </Modal>
  </ViewContent>
</template>
