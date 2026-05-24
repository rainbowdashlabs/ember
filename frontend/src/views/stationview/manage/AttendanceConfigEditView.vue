/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import AttendanceGroupsEditor from './attendanceconfigedit/GroupsEditor.vue'
import AttendanceFieldsList from './attendanceconfigedit/FieldsList.vue'
import AttendanceFieldModal from './attendanceconfigedit/FieldModal.vue'
import type {AttendanceTemplateField, MemberGroup, TemplateGroupEntry} from '@/api/types'
import {attendance, memberGroups} from '@/api'

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
const loading = ref(false)
const saving = ref(false)
const error = ref('')

// Field modal state
const showFieldModal = ref(false)
const editingField = ref<AttendanceTemplateField | null>(null)
const fieldSaving = ref(false)

// Delete modal state
const showDeleteFieldModal = ref(false)
const deleteFieldTarget = ref<AttendanceTemplateField | null>(null)

async function loadTemplate() {
  if (!templateId.value) return
  loading.value = true
  error.value = ''
  try {
    const [detail, groups] = await Promise.all([
      attendance.getTemplate(templateId.value),
      memberGroups.listGroups(),
    ])
    name.value = detail.name ?? ''
    fields.value = detail.fields ?? []
    templateGroups.value = detail.groups ?? []
    availableGroups.value = groups
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveTemplate() {
  saving.value = true
  error.value = ''
  try {
    if (isEdit.value) {
      await attendance.updateTemplate(templateId.value!, {name: name.value})
    } else {
      const created = await attendance.createTemplate({name: name.value})
      await router.replace({name: 'station-attendance-config-edit', params: {id: created.id}})
      availableGroups.value = await memberGroups.listGroups()
    }
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
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

async function saveField(data: { name: string; fieldType: string; config: string; position: number }) {
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
        config: f.config ?? '{}',
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

onMounted(loadTemplate)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton @click="goBack">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2"/>
        {{ t('attendanceConfig.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="space-y-6">
        <!-- Template name -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{
              isEdit ? t('attendanceConfig.editTitle') : t('attendanceConfig.createTitle')
            }}
          </SectionHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('attendanceConfig.name') }}</FieldLabel>
            <TextInput v-model="name" :placeholder="t('attendanceConfig.namePlaceholder')"/>
          </div>
          <PrimaryButton :disabled="saving || !name" @click="saveTemplate">
            {{
              saving ? t('common.loading') : (isEdit ? t('attendanceConfig.save') : t('attendanceConfig.createSubmit'))
            }}
          </PrimaryButton>
        </NeutralContainer>

        <!-- Groups -->
        <AttendanceGroupsEditor
            v-if="isEdit"
            :available-groups="availableGroups"
            :groups="templateGroups"
            @add="addGroup"
            @remove="removeGroup"
            @move-up="moveGroupUp"
            @move-down="moveGroupDown"
        />

        <!-- Fields -->
        <AttendanceFieldsList
            v-if="isEdit"
            :available-groups="availableGroups"
            :fields="fields"
            @add="openAddField"
            @delete="requestDeleteField"
            @edit="openEditField"
            @reorder="reorderFields"
        />
      </div>

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
