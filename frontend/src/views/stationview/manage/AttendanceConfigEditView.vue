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
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import AttendanceFieldModal from './attendanceconfigedit/FieldModal.vue'
import EditContent from './attendanceconfigedit/EditContent.vue'
import type {AttendanceTemplateField, MemberGroup, TemplateGroupEntry} from '@/api/types'
import {attendance, memberGroups} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'

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

const showFieldModal = ref(false)
const editingField = ref<AttendanceTemplateField | null>(null)

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

function openAddField() {
  editingField.value = null
  showFieldModal.value = true
}

function openEditField(field: AttendanceTemplateField) {
  editingField.value = field
  showFieldModal.value = true
}

const {running: fieldSaving, error: fieldSaveError, run: saveField} = useAsyncAction(
    async (data: { name: string; fieldType: string; config: Record<string, unknown>; position: number }) => {
      if (!templateId.value) return
      if (editingField.value) {
        fields.value = await attendance.updateTemplateField(templateId.value, editingField.value.id, data)
      } else {
        fields.value = await attendance.createTemplateField(templateId.value, data)
      }
      showFieldModal.value = false
    },
    {formatError: () => t('common.error')},
)

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

const {
  show: showDeleteFieldModal,
  target: deleteFieldTarget,
  requestDelete: requestDeleteField,
  confirm: confirmDeleteField,
} = useConfirmDelete<AttendanceTemplateField>({
  onDelete: async (field) => {
    if (!templateId.value) return
    fields.value = await attendance.deleteTemplateField(templateId.value, field.id)
  },
  error,
})

const displayError = computed(() => error.value || fieldSaveError.value)

function goBack() {
  router.push({name: 'station-attendance-config'})
}

</script>

<template>
  <ViewContent
      :title="t('pages.station-attendance-config-edit.title')"
      :subtitle="t('pages.station-attendance-config-edit.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('attendanceConfig.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="displayError" variant="error">{{ displayError }}</Alert>

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

      <AttendanceFieldModal
          v-model="showFieldModal"
          :available-groups="availableGroups"
          :field="editingField"
          :field-count="fields.length"
          :saving="fieldSaving"
          @save="saveField"
      />

      <ConfirmDeleteModal
          v-model="showDeleteFieldModal"
          :message="t('attendanceConfig.deleteFieldConfirm', {name: deleteFieldTarget?.name})"
          @confirm="confirmDeleteField"
      />
    </div>
  </ViewContent>
</template>
