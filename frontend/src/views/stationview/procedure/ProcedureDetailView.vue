/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ProcedureItemRow from './proceduredetailview/ProcedureItemRow.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {procedures} from '@/api'
import {StationPermission} from '@/api/types'
import type {ProcedureDetail, ProcedureItem} from '@/api/procedures'
import {ProcedureStatus} from '@/api/procedures'
import {formatDate, formatDateTime} from '@/util/format'
import {reportCaughtError} from '@/util/devErrorReporter'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {hasPermission, loaded} = useSession()

const canEdit = computed(() => hasPermission(StationPermission.PROCEDURE_EDIT))

const detail = ref<ProcedureDetail | null>(null)

const showResolveModal = ref(false)

const procedureId = computed(() => Number(route.params.id))

const isOverdue = computed(() => {
  if (!detail.value?.procedure.dueAt) return false
  if (detail.value.procedure.status === ProcedureStatus.RESOLVED) return false
  return new Date(detail.value.procedure.dueAt) < new Date()
})

const progress = computed(() => {
  if (!detail.value) return {checked: 0, total: 0, percent: 0}
  const total = detail.value.items.length
  const checked = detail.value.items.filter(i => i.checked).length
  return {checked, total, percent: total > 0 ? Math.round((checked / total) * 100) : 0}
})

function getDependencyNames(item: ProcedureItem): string[] {
  if (!detail.value) return []
  return detail.value.dependencies
      .filter(d => d[0] === item.id)
      .map(d => detail.value!.items.find(i => i.id === d[1]))
      .filter(i => i && !i.checked)
      .map(i => i!.title)
}

function isDependencyMet(item: ProcedureItem): boolean {
  if (!detail.value) return true
  const deps = detail.value.dependencies.filter(d => d[0] === item.id)
  if (deps.length === 0) return true
  return deps.every(d => {
    const depItem = detail.value!.items.find(i => i.id === d[1])
    return depItem?.checked ?? false
  })
}

const sortedItems = computed(() => {
  if (!detail.value) return []
  const items = [...detail.value.items]
  const deps = detail.value.dependencies
  const result: ProcedureItem[] = []
  const placed = new Set<number>()
  const remaining = new Set(items.map(i => i.id))

  while (remaining.size > 0) {
    let added = false
    for (const item of items) {
      if (!remaining.has(item.id)) continue
      const itemDeps = deps.filter(d => d[0] === item.id).map(d => d[1])
      if (itemDeps.every(d => placed.has(d) || !remaining.has(d))) {
        result.push(item)
        placed.add(item.id)
        remaining.delete(item.id)
        added = true
      }
    }
    if (!added) {
      for (const item of items) {
        if (remaining.has(item.id)) result.push(item)
      }
      break
    }
  }
  return result
})

const {loading, error, reload} = useAsyncLoader(async () => {
  detail.value = await procedures.getProcedure(procedureId.value)
}, {autoLoad: false})

async function refreshSilently() {
  try {
    detail.value = await procedures.getProcedure(procedureId.value)
  } catch (e) {
    reportCaughtError(e, 'silent procedure refresh')
  }
}

function canCheckItem(item: ProcedureItem): boolean {
  if (item.checked) return canEdit.value
  if (!isDependencyMet(item)) return false
  if (!item.userAssigned && !canEdit.value) return false
  return true
}

async function toggleItem(item: ProcedureItem) {
  if (!canCheckItem(item)) return
  if (detail.value) {
    detail.value = {
      ...detail.value,
      items: detail.value.items.map(i => i.id === item.id
          ? {...i, checked: !i.checked, checkedAt: !i.checked ? new Date().toISOString() : null}
          : i),
    }
  }
  try {
    await procedures.patchItem(procedureId.value, item.id, {checked: !item.checked})
    await refreshSilently()
  } catch {
    error.value = t('common.error')
    await refreshSilently()
  }
}

async function updateNote(item: ProcedureItem, note: string) {
  try {
    await procedures.patchItem(procedureId.value, item.id, {note: note || undefined})
  } catch {
    error.value = t('common.error')
  }
}

async function handleResolve() {
  try {
    if (detail.value?.procedure.status === ProcedureStatus.OPEN) {
      await procedures.resolveProcedure(procedureId.value)
    } else {
      await procedures.reopenProcedure(procedureId.value)
    }
    showResolveModal.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

watch(loaded, (v) => {
  if (v) reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.procedure-detail.title')"
      :subtitle="t('pages.procedure-detail.subtitle')"
  >
    <Spinner v-if="loading"/>
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <template v-if="detail && !loading">
      <!-- Header -->
      <div class="flex items-start justify-between mb-4 gap-4">
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 mb-1 flex-wrap">
            <SuccessBadge v-if="detail.procedure.status === ProcedureStatus.RESOLVED">{{ t('procedures.resolved') }}</SuccessBadge>
            <PrimaryBadge v-else>{{ t('procedures.open') }}</PrimaryBadge>
            <ErrorBadge v-if="isOverdue">{{ t('procedures.overdue') }}</ErrorBadge>
          </div>
          <p v-if="detail.procedure.description" class="text-[var(--text-muted)] text-sm">{{ detail.procedure.description }}</p>
          <div class="flex flex-wrap gap-4 mt-2 text-sm text-[var(--text-muted)]">
            <span v-if="detail.procedure.dueAt" class="flex items-center gap-1" :class="{ 'text-[var(--error)]': isOverdue }">
              <font-awesome-icon :icon="['fas', 'calendar']" class="w-3 h-3"/>
              {{ formatDate(detail.procedure.dueAt) }}
            </span>
            <span v-if="detail.procedure.resolvedAt" class="flex items-center gap-1">
              <font-awesome-icon :icon="['fas', 'check-circle']" class="w-3 h-3"/>
              {{ formatDateTime(detail.procedure.resolvedAt) }}
            </span>
          </div>
        </div>
        <div v-if="canEdit" class="flex gap-2 shrink-0">
          <SecondaryButton @click="router.push({ name: 'procedure-edit', params: { id: procedureId } })">
            <font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/> {{ t('common.edit') }}
          </SecondaryButton>
          <SuccessButton v-if="detail.procedure.status === ProcedureStatus.OPEN" @click="showResolveModal = true">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/> {{ t('procedures.resolve') }}
          </SuccessButton>
          <ErrorButton v-else @click="showResolveModal = true">
            <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1"/> {{ t('procedures.reopen') }}
          </ErrorButton>
        </div>
      </div>

      <!-- Assignees -->
      <NeutralContainer v-if="detail.assignees.length > 0" class="mb-4">
        <SubHeader class="mb-2">{{ t('procedures.assignees') }}</SubHeader>
        <div class="flex flex-wrap gap-2">
          <MemberName v-for="assignee in detail.assignees" :key="assignee.memberUid" :identity="assignee" size="sm"/>
        </div>
      </NeutralContainer>

      <!-- Progress bar -->
      <NeutralContainer class="mb-4">
        <div class="flex items-center gap-3">
          <span class="text-sm font-medium">{{ t('procedures.progress') }}</span>
          <div class="flex-1 h-2 bg-[var(--bg-light-accent)] dark:bg-[var(--bg-dark-accent)] rounded-full overflow-hidden">
            <div class="h-full bg-[var(--success)] rounded-full transition-all" :style="{ width: progress.percent + '%' }"/>
          </div>
          <span class="text-sm text-[var(--text-muted)]">{{ progress.checked }}/{{ progress.total }}</span>
        </div>
      </NeutralContainer>

      <!-- Checklist items -->
      <SubHeader class="mb-3">{{ t('procedures.items') }}</SubHeader>

      <div class="space-y-2">
        <ProcedureItemRow
            v-for="item in sortedItems"
            :key="item.id"
            :item="item"
            :can-edit="canEdit"
            :can-check="canCheckItem(item)"
            :dependency-met="isDependencyMet(item)"
            :dependency-names="getDependencyNames(item)"
            @toggle="toggleItem(item)"
            @update-note="(note) => updateNote(item, note)"
        />
      </div>

      <div v-if="detail.items.length === 0" class="text-center text-[var(--text-muted)] py-8">
        {{ t('procedures.empty') }}
      </div>
    </template>

    <!-- Resolve/Reopen Modal -->
    <Modal v-model="showResolveModal">
      <SubHeader class="mb-3">
        {{ detail?.procedure.status === ProcedureStatus.OPEN ? t('procedures.resolve') : t('procedures.reopen') }}
      </SubHeader>
      <p class="mb-4">{{ detail?.procedure.name }}</p>
      <div class="flex gap-2 justify-end">
        <SecondaryButton @click="showResolveModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton @click="handleResolve">
          {{ detail?.procedure.status === ProcedureStatus.OPEN ? t('procedures.resolve') : t('procedures.reopen') }}
        </PrimaryButton>
      </div>
    </Modal>
  </ViewContent>
</template>
