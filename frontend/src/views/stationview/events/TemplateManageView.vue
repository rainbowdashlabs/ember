/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldList from './eventshared/EventFieldList.vue'
import type {EventCategory, EventFieldEntry, EventTemplate, EventTemplateDetail} from '@/api/types'
import {events} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded} = useSession()

const templates = ref<EventTemplate[]>([])
const categories = ref<EventCategory[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

// Create modal
const createOpen = ref(false)
const createName = ref('')

// Edit modal
const editOpen = ref(false)
const editDetail = ref<EventTemplateDetail | null>(null)
const editName = ref('')
const editTitle = ref('')
const editDescription = ref('')
const editCategoryId = ref('')
const editEventType = ref('')
const editRequiresRegistration = ref(false)
const editRequiresConfirmation = ref(false)
const editFields = ref<EventFieldEntry[]>([])
const saving = ref(false)

onMounted(() => { if (loaded.value) loadData() })
watch(loaded, (v) => { if (v && loading.value) loadData() })

async function loadData() {
  loading.value = true
  try {
    const [t, c] = await Promise.all([events.listTemplates(), events.listCategories()])
    templates.value = t
    categories.value = c
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function createTemplate() {
  if (!createName.value.trim()) return
  try {
    await events.createTemplate({name: createName.value.trim()})
    createOpen.value = false
    createName.value = ''
    await loadData()
  } catch { error.value = t('common.error') }
}

async function openEdit(id: number) {
  try {
    const detail = await events.getTemplate(id)
    editDetail.value = detail
    editName.value = detail.template.name
    editTitle.value = detail.template.title ?? ''
    editDescription.value = detail.template.description ?? ''
    editCategoryId.value = detail.template.categoryId ? String(detail.template.categoryId) : ''
    editEventType.value = detail.template.eventType ?? ''
    editRequiresRegistration.value = detail.template.requiresRegistration ?? false
    editRequiresConfirmation.value = detail.template.requiresConfirmation ?? false
    editFields.value = detail.fields.map(f => ({
      name: f.name,
      fieldType: f.fieldType,
      config: f.config ?? '{}',
      value: '',
      overview: f.overview,
      attendanceFieldId: f.attendanceFieldId ?? null,
      isPublic: f.isPublic,
    }))
    editOpen.value = true
  } catch { error.value = t('common.error') }
}

async function saveEdit() {
  if (!editDetail.value) return
  saving.value = true
  try {
    const id = editDetail.value.template.id
    await events.updateTemplate(id, {
      name: editName.value,
      title: editTitle.value || null,
      description: editDescription.value || null,
      categoryId: editCategoryId.value ? Number(editCategoryId.value) : null,
      eventType: editEventType.value || null,
      requiresRegistration: editRequiresRegistration.value || null,
      requiresConfirmation: editRequiresConfirmation.value || null,
    })
    await events.setTemplateFields(id, {
      fields: editFields.value.map((f, i) => ({
        name: f.name,
        fieldType: f.fieldType ?? 'string',
        config: f.config ?? '{}',
        position: i,
        overview: f.overview,
        isPublic: f.isPublic,
        attendanceFieldId: f.attendanceFieldId,
      })),
    })
    editOpen.value = false
    success.value = t('eventTemplates.saved')
    setTimeout(() => { success.value = '' }, 3000)
    await loadData()
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

async function deleteTemplate(id: number) {
  try {
    await events.deleteTemplate(id)
    await loadData()
  } catch { error.value = t('common.error') }
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('eventTemplates.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']" @click="createOpen = true">{{ t('eventTemplates.create') }}</PrimaryButton>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>
      <Spinner v-if="loading" />

      <EmptyState v-if="!loading && templates.length === 0">{{ t('eventTemplates.empty') }}</EmptyState>

      <div v-if="!loading" class="space-y-2">
        <NeutralContainer v-for="tpl in templates" :key="tpl.id" class="flex items-center justify-between">
          <div>
            <span class="font-medium">{{ tpl.name }}</span>
            <MutedText v-if="tpl.title" size="sm" class="ml-2">{{ tpl.title }}</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <EditButton @click="openEdit(tpl.id)"/>
            <DeleteButton @click="deleteTemplate(tpl.id)"/>
          </div>
        </NeutralContainer>
      </div>

      <!-- Create modal -->
      <Modal v-model="createOpen">
        <div class="space-y-4">
          <SubHeader>{{ t('eventTemplates.create') }}</SubHeader>
          <FieldLabel>{{ t('eventTemplates.name') }}</FieldLabel>
          <TextInput v-model="createName" :placeholder="t('eventTemplates.namePlaceholder')"/>
          <div class="flex justify-end">
            <PrimaryButton :disabled="!createName.trim()" @click="createTemplate">{{ t('eventTemplates.create') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Edit modal -->
      <Modal v-model="editOpen">
        <div class="space-y-4 max-h-[80vh] overflow-y-auto">
          <SubHeader>{{ t('eventTemplates.edit') }}</SubHeader>

          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.name') }}</FieldLabel>
            <TextInput v-model="editName"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.eventTitle') }}</FieldLabel>
            <TextInput v-model="editTitle" :placeholder="t('eventTemplates.eventTitlePlaceholder')"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.eventDescription') }}</FieldLabel>
            <TextAreaInput v-model="editDescription" :rows="3"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.category') }}</FieldLabel>
            <SelectInput v-model="editCategoryId">
              <option value="">{{ t('eventTemplates.noCategory') }}</option>
              <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.eventType') }}</FieldLabel>
            <SelectInput v-model="editEventType">
              <option value="">{{ t('eventTemplates.noDefault') }}</option>
              <option value="ONE_TIME">{{ t('events.typeOneTime') }}</option>
              <option value="RECURRING">{{ t('events.typeRecurring') }}</option>
              <option value="MONTHLY_FIRST">{{ t('events.typeMonthlyFirst') }}</option>
              <option value="QUARTERLY">{{ t('events.typeQuarterly') }}</option>
              <option value="YEARLY">{{ t('events.typeYearly') }}</option>
            </SelectInput>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('events.requiresRegistration') }}</span>
            <ToggleInput v-model="editRequiresRegistration"/>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</span>
            <ToggleInput v-model="editRequiresConfirmation"/>
          </div>

          <EventFieldList v-model:fields="editFields"/>

          <div class="flex justify-end">
            <PrimaryButton :disabled="saving || !editName.trim()" @click="saveEdit">
              {{ saving ? t('common.loading') : t('stationManage.save') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
