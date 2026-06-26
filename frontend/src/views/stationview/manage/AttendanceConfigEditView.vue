/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import AttendanceFieldModal from './attendanceconfigedit/FieldModal.vue'
import EditContent from './attendanceconfigedit/EditContent.vue'
import type {AttendanceTemplateField, MemberGroup, TemplateGroupEntry} from '@/api/types'
import {attendance, memberGroups} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const templateId = computed(() => {
  const id = route.params.id
  return id ? Number(id) : null
})
const isEdit = computed(() => templateId.value !== null)

const name = ref('')
const fields = ref<AttendanceTemplateField[]>([])
const templateGroups = ref<TemplateGroupEntry[]>([])
const availableGroups = ref<MemberGroup[]>([])

// Field modal state
const showFieldModal = ref(false)
const editingField = ref<AttendanceTemplateField | null>(null)
const fieldSaving = ref(false)

// Delete modal state
const showDeleteFieldModal = ref(false)
const deleteFieldTarget = ref<AttendanceTemplateField | null>(null)

const {loading, error} = useAsyncLoader(async () => {
  if (!templateId.value) return
  const [detail, groups] = await Promise.all([
    attendance.getTemplate(templateId.value),
    memberGroups.listGroups(),
  ])
  name.value = detail.name ?? ''
  fields.value = detail.fields ?? []
  templateGroups.value = detail.groups ?? []
  availableGroups.value = groups
})

async function saveTemplate() {
  error.value = ''
  try {
    if (isEdit.value) {
      await attendance.updateTemplate(templateId.value!, {name: name.value})
    } else {
      const created = await attendance.createTemplate({name: name.value})
      await router.replace({name: 'station-attendance-config-edit', params: {id: created.id}})
      availableGroups.value = await memberGroups.listGroups()
    }
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

// -- Groups --

function addGroup(groupId: number) {
  templateGroups.value = [...templateGroups.value, {groupId, position: templateGroups.value.length}]
  saveGroups()
}

function removeGroup(groupId: number) {
  templateGroups.value = templateGroups.value
      .filter(g => g.groupId !== groupId)
      .map((g, i) => ({...g, position: i}))
  saveGroups()
}

function moveGroupUp(index: number) {
  if (index === 0) return
  const arr = [...templateGroups.value]
  ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
  templateGroups.value = arr.map((g, i) => ({...g, position: i}))
  saveGroups()
}

function moveGroupDown(index: number) {
  if (index >= templateGroups.value.length - 1) return
  const arr = [...templateGroups.value]
  ;[arr[index], arr[index + 1]] = [arr[index + 1], arr[index]]
  templateGroups.value = arr.map((g, i) => ({...g, position: i}))
  saveGroups()
}

async function saveGroups() {
  if (!templateId.value) return
  try {
    templateGroups.value = await attendance.setTemplateGroups(templateId.value, {groups: templateGroups.value})
  } catch {
    error.value = t('common.error')
  }
}

// -- Fields --

function openAddField() {
  editingField.value = null
  showFieldModal.value = true
}

function openEditField(field: AttendanceTemplateField) {
  editingField.value = field
  showFieldModal.value = true
}

async function saveField(data: { name: string; fieldType: string; config: Record<string, unknown>; position: number }) {
  if (!templateId.value) return
  fieldSaving.value = true
  error.value = ''
  try {
    if (editingField.value) {
      fields.value = await attendance.updateTemplateField(templateId.value, editingField.value.id, data)
    } else {
      fields.value = await attendance.createTemplateField(templateId.value, data)
    }
    showFieldModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    fieldSaving.value = false
  }
}

async function reorderFields(fromIndex: number, toIndex: number) {
  if (!templateId.value) return
  const arr = [...fields.value]
  const [moved] = arr.splice(fromIndex, 1)
  arr.splice(toIndex, 0, moved)
  fields.value = arr.map((f, i) => ({...f, position: i}))

  try {
    for (const f of fields.value) {
      await attendance.updateTemplateField(templateId.value!, f.id, {
        name: f.name ?? '',
        fieldType: f.fieldType ?? '',
        config: f.config ?? {},
        position: f.position,
      })
    }
    fields.value = await attendance.listTemplateFields(templateId.value!)
  } catch {
    error.value = t('common.error')
  }
}

function requestDeleteField(field: AttendanceTemplateField) {
  deleteFieldTarget.value = field
  showDeleteFieldModal.value = true
}

async function confirmDeleteField() {
  if (!templateId.value || !deleteFieldTarget.value) return
  try {
    fields.value = await attendance.deleteTemplateField(templateId.value, deleteFieldTarget.value.id)
    showDeleteFieldModal.value = false
    deleteFieldTarget.value = null
  } catch {
    error.value = t('common.error')
  }
}

function goBack() {
  router.push({name: 'station-attendance-config'})
}

</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('attendanceConfig.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <EditContent
          v-if="!loading"
          v-model:name="name"
          :is-edit="isEdit"
          :fields="fields"
          :template-groups="templateGroups"
          :available-groups="availableGroups"
          :save-template="saveTemplate"
          @add-group="addGroup"
          @remove-group="removeGroup"
          @move-group-up="moveGroupUp"
          @move-group-down="moveGroupDown"
          @add-field="openAddField"
          @edit-field="openEditField"
          @delete-field="requestDeleteField"
          @reorder-fields="reorderFields"
      />

      <!-- Field modal -->
      <AttendanceFieldModal
          v-model="showFieldModal"
          :available-groups="availableGroups"
          :field="editingField"
          :field-count="fields.length"
          :saving="fieldSaving"
          @save="saveField"
      />

      <!-- Delete field confirm modal -->
      <Modal v-model="showDeleteFieldModal">
        <div class="space-y-4">
          <p>{{ t('attendanceConfig.deleteFieldConfirm', {name: deleteFieldTarget?.name}) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteFieldModal = false">{{ t('attendanceConfig.cancel') }}</SecondaryButton>
            <ErrorButton @click="confirmDeleteField">{{ t('attendanceConfig.delete') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
