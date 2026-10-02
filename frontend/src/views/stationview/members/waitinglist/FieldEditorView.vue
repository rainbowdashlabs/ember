/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type { WaitingList, WaitingListField, WaitingListFieldConfig } from '@/api/generated/schema'
import { waitingList } from '@/api'
import { FieldTypes, type FieldTypeName } from '@/api/fieldTypes'
import type { QuestionSettingsModel } from '@/components/input/questionsettings/questionSettings'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useConfirmAction } from '@/composables/useConfirmAction'
import {usableOptions} from '@/util/choiceOptions'
import FieldsList from './fieldeditorview/FieldsList.vue'
import FieldModal from './fieldeditorview/FieldModal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {describeFailure} from '@/util/failure'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))

const list = ref<WaitingList | null>(null)
const fields = ref<WaitingListField[]>([])

const showFieldModal = ref(false)
const editingField = ref<WaitingListField | null>(null)
const fieldName = ref('')
const fieldType = ref<FieldTypeName>(FieldTypes.TEXT)
const fieldSettings = ref<QuestionSettingsModel>({})
const fieldPublic = ref(true)

const sortedFields = computed(() =>
  [...fields.value].sort((a, b) => a.position - b.position),
)

/**
 * The list's name before the word, because every waiting list has its own questions and "Felder"
 * alone is true of all of them. The static wording stands until the list has arrived, and where it
 * could not be fetched at all.
 */
const pageTitle = computed(() => (list.value
  ? t('pages.waiting-list-fields.titleNamed', {name: list.value.name})
  : t('pages.waiting-list-fields.title')))

const {loading, failure} = useAsyncLoader(async () => {
  const [listData, fieldData] = await Promise.all([
    waitingList.getById(listId.value),
    waitingList.listFields(listId.value),
  ])
  list.value = listData
  fields.value = fieldData
})

function openAddField() {
  editingField.value = null
  fieldName.value = ''
  fieldType.value = FieldTypes.TEXT
  fieldSettings.value = {}
  fieldPublic.value = true
  showFieldModal.value = true
}

function openEditField(field: WaitingListField) {
  editingField.value = field
  fieldName.value = field.name
  fieldType.value = field.fieldType
  fieldSettings.value = {required: field.required, options: [...(field.config?.options ?? [])]}
  fieldPublic.value = field.isPublic ?? true
  showFieldModal.value = true
}

function buildConfig(): WaitingListFieldConfig {
  const options = usableOptions(fieldSettings.value.options ?? [])
  if (fieldType.value !== FieldTypes.CHOICE || options.length === 0) return {}
  return {options}
}

const { running: savingField, failure: saveFieldFailure, run: saveField } = useAsyncAction(async () => {
  if (!fieldName.value.trim()) return
  failure.value = null
  const data = {
    name: fieldName.value.trim(),
    fieldType: fieldType.value,
    config: buildConfig(),
    position: editingField.value?.position ?? fields.value.length,
    required: fieldSettings.value.required ?? false,
    isPublic: fieldPublic.value,
  }
  if (editingField.value) {
    await waitingList.updateField(listId.value, editingField.value.id, data)
  } else {
    await waitingList.createField(listId.value, data)
  }
  showFieldModal.value = false

  try {
    fields.value = await waitingList.listFields(listId.value)
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
})

const {
  show: showDeleteModal,
  target: deleteTarget,
  request: requestDelete,
  confirm: confirmDelete,
} = useConfirmAction<WaitingListField>({
  onConfirm: f => waitingList.deleteField(listId.value, f.id),
  onSuccess: async () => {
    fields.value = await waitingList.listFields(listId.value)
  },
  failure,
})

async function moveField(index: number, direction: -1 | 1) {
  const sorted = sortedFields.value
  const targetIndex = index + direction
  const fieldA = sorted[index]
  const fieldB = sorted[targetIndex]
  if (!fieldA || !fieldB) return
  failure.value = null
  try {
    await Promise.all([
      waitingList.updateField(listId.value, fieldA.id, {
        name: fieldA.name,
        fieldType: fieldA.fieldType,
        config: fieldA.config ?? {},
        position: fieldB.position,
        required: fieldA.required,
        isPublic: fieldA.isPublic,
      }),
      waitingList.updateField(listId.value, fieldB.id, {
        name: fieldB.name,
        fieldType: fieldB.fieldType,
        config: fieldB.config ?? {},
        position: fieldA.position,
        required: fieldB.required,
        isPublic: fieldB.isPublic,
      }),
    ])
    fields.value = await waitingList.listFields(listId.value)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

function goBack() {
  router.push({ name: 'waiting-list-detail', params: { id: listId.value } })
}

</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.waiting-list-fields.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('waitingList.backToList') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure ?? saveFieldFailure"/>

      <FieldsList
        v-if="!loading && list"
        :list-name="list.name"
        :fields="fields"
        @add="openAddField"
        @edit="openEditField"
        @delete="requestDelete"
        @move="moveField"
      />

      <FieldModal
        v-model="showFieldModal"
        v-model:field-name="fieldName"
        v-model:field-type="fieldType"
        v-model:field-settings="fieldSettings"
        v-model:field-public="fieldPublic"
        :is-edit="!!editingField"
        :saving="savingField"
        @save="saveField"
      />

      <ConfirmDeleteModal
        v-model="showDeleteModal"
        :title="t('waitingList.deleteFieldTitle')"
        :message="t('waitingList.deleteFieldConfirm', { name: deleteTarget?.name })"
        @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
