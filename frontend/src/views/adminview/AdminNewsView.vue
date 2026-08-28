/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {adminNews} from '@/api'
import type {SystemNewsEntry, SystemNewsRequest} from '@/api/adminNews'
import {ContentMode, type ContentModeName} from '@/api/news'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import SystemNewsList from './adminnewsview/SystemNewsList.vue'
import SystemNewsEditor from './adminnewsview/SystemNewsEditor.vue'

const {t} = useI18n()

const {config: entries, loading, error, runWith} = useConfigPanel<SystemNewsEntry[]>({
  initial: [],
  fetch: () => adminNews.listSystemNews(),
})

const editing = ref(false)
const editingEntry = ref<SystemNewsEntry | null>(null)
const saving = ref(false)

function startCreate() {
  editingEntry.value = null
  editing.value = true
}

/**
 * Opens an entry for correction. The list rows carry no blocks, so a rich entry is read again in
 * full: the editor needs what it is about to edit, and loading it for every row of the list would
 * ask the database once per row for something the list never shows.
 */
async function startEdit(entry: SystemNewsEntry) {
  editingEntry.value = entry.contentMode === ContentMode.RICH
      ? await adminNews.getSystemNews(entry.id)
      : entry
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editingEntry.value = null
}

interface EditorPayload {
  title: string
  contentMarkdown: string
  userTypes: string[]
  notifyMembers: boolean
  contentMode: ContentModeName
  rows: RowEditData[]
}

async function save(payload: EditorPayload) {
  saving.value = true
  try {
    const data: SystemNewsRequest = {
      title: payload.title,
      contentMarkdown: payload.contentMarkdown,
      userTypes: payload.userTypes,
      publish: true,
      notifyMembers: payload.notifyMembers,
      contentMode: payload.contentMode,
    }
    const saved = editingEntry.value
        ? await adminNews.updateSystemNews(editingEntry.value.id, data)
        : await adminNews.createSystemNews(data)

    if (payload.contentMode === ContentMode.RICH) {
      await adminNews.saveSystemNewsBlocks(saved.id, payload.rows.map((row, ri) => ({
        sortOrder: ri,
        cells: row.cells.map((cell, ci) => ({
          sortOrder: ci,
          widthPercent: cell.widthPercent,
          contentType: cell.contentType,
          content: cell.content,
          config: cell.config,
        })),
      })))
    }

    cancelEdit()
    await runWith(() => adminNews.listSystemNews())
  } finally {
    saving.value = false
  }
}

const {show: showRetract, target: retractTarget, requestDelete: requestRetract, confirm: confirmRetract} =
    useConfirmDelete<SystemNewsEntry>({
      onDelete: entry => adminNews.retractSystemNews(entry.id),
      onSuccess: () => runWith(() => adminNews.listSystemNews()),
    })
</script>

<template>
  <ViewContent :title="t('sidebar.systemNews')" :subtitle="t('adminNews.intro')">
    <div class="space-y-4">
      <div class="flex items-center justify-end gap-3">
        <PrimaryButton v-if="!editing" :icon="['fas', 'plus']" @click="startCreate">
          {{ t('adminNews.create') }}
        </PrimaryButton>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Spinner v-if="loading" size="lg"/>

      <SystemNewsEditor
          v-if="editing"
          :entry="editingEntry"
          :saving="saving"
          @cancel="cancelEdit"
          @save="save"
      />

      <template v-else-if="!loading">
        <EmptyState v-if="entries.length === 0" :icon="['fas', 'bullhorn']" :message="t('adminNews.empty')"/>
        <SystemNewsList v-else :entries="entries" @edit="startEdit" @retract="requestRetract"/>
      </template>
    </div>

    <ConfirmDeleteModal
        v-model="showRetract"
        :message="t('adminNews.retractMessage', {title: retractTarget?.title ?? ''})"
        @confirm="confirmRetract"
    />
  </ViewContent>
</template>
