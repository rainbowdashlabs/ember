/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {AttendanceTemplate} from '@/api/attendance'
import {attendance} from '@/api'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()
const router = useRouter()

const {config: templates, loading, error, reload: loadTemplates} = useConfigPanel<AttendanceTemplate[]>({
  initial: [],
  fetch: () => attendance.listTemplates(),
})
const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<AttendanceTemplate>({
  onDelete: tpl => attendance.deleteTemplate(tpl.id),
  onSuccess: () => loadTemplates(),
  error,
})

function navigateToCreate() {
  router.push({name: 'station-attendance-config-edit'})
}

function navigateToEdit(id: number) {
  router.push({name: 'station-attendance-config-edit', params: {id}})
}

async function duplicateTemplate(tpl: AttendanceTemplate) {
  error.value = ''
  try {
    const detail = await attendance.getTemplate(tpl.id)
    const created = await attendance.createTemplate({name: (detail.name ?? '') + ' (Kopie)'})

    if (detail.groups && detail.groups.length > 0) {
      await attendance.setTemplateGroups(created.id, {groups: detail.groups})
    }

    if (detail.fields) {
      for (const field of detail.fields) {
        await attendance.createTemplateField(created.id, {
          name: field.name ?? '',
          fieldType: field.fieldType ?? '',
          config: field.config ?? {},
          position: field.position,
        })
      }
    }

    await loadTemplates()
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.station-attendance-config.title')"
      :subtitle="t('pages.station-attendance-config.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <PrimaryButton :icon="['fas', 'plus']" @click="navigateToCreate">
          {{ t('attendanceConfig.create') }}
        </PrimaryButton>
      </div>

      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading && templates.length === 0 && !error" class="text-center text-(--text-muted) py-12">
        {{ t('attendanceConfig.empty') }}
      </div>

      <div v-if="!loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <NeutralContainer v-for="tpl in templates" :key="tpl.id" class="flex items-center justify-between">
          <span class="font-medium">{{ tpl.name }}</span>
          <div class="flex items-center gap-2">
            <IconButton :icon="['fas', 'copy']" :label="t('attendanceConfig.duplicate')"
                        class="text-secondary hover:bg-secondary/15" @click="duplicateTemplate(tpl)"/>
            <EditButton @click="navigateToEdit(tpl.id)"/>
            <DeleteButton @click="requestDelete(tpl)"/>
          </div>
        </NeutralContainer>
      </div>

      <ConfirmDeleteModal
          v-model="showDeleteModal"
          :message="t('attendanceConfig.deleteConfirm', {name: deleteTarget?.name})"
          @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
