/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import type {AttendanceTemplate} from '@/api/types'
import {attendance} from '@/api'

const {t} = useI18n()
const router = useRouter()

const templates = ref<AttendanceTemplate[]>([])
const loading = ref(true)
const error = ref('')
const showDeleteModal = ref(false)
const deleteTarget = ref<AttendanceTemplate | null>(null)

async function loadTemplates() {
  loading.value = true
  error.value = ''
  try {
    templates.value = await attendance.listTemplates()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function navigateToCreate() {
  router.push({name: 'station-attendance-config-edit'})
}

function navigateToEdit(id: number) {
  router.push({name: 'station-attendance-config-edit', params: {id}})
}

function requestDelete(tpl: AttendanceTemplate) {
  deleteTarget.value = tpl
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await attendance.deleteTemplate(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadTemplates()
  } catch {
    error.value = t('common.error')
  }
}

async function duplicateTemplate(tpl: AttendanceTemplate) {
  error.value = ''
  try {
    const detail = await attendance.getTemplate(tpl.id)
    const created = await attendance.createTemplate({name: (detail.name ?? '') + ' (Kopie)'})

    // Copy groups
    if (detail.groups && detail.groups.length > 0) {
      await attendance.setTemplateGroups(created.id, {groups: detail.groups})
    }

    // Copy fields
    if (detail.fields) {
      for (const field of detail.fields) {
        await attendance.createTemplateField(created.id, {
          name: field.name ?? '',
          fieldType: field.fieldType ?? '',
          config: field.config ?? '{}',
          position: field.position,
        })
      }
    }

    await loadTemplates()
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadTemplates)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <PrimaryButton @click="navigateToCreate">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
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

      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <p>{{ t('attendanceConfig.deleteConfirm', {name: deleteTarget?.name}) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteModal = false">{{ t('attendanceConfig.cancel') }}</SecondaryButton>
            <ErrorButton @click="confirmDelete">{{ t('attendanceConfig.delete') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
