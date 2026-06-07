/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StepProgressBar from '@/components/display/StepProgressBar.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFormPanel from './eventshared/EventFormPanel.vue'
import BatchScheduleStep from './batchcreateview/BatchScheduleStep.vue'
import BatchEditTable from './batchcreateview/BatchEditTable.vue'
import type {AttendanceTemplate, AttendanceTemplateField, EventCategory, EventFieldEntry, EventLayout, LayoutFieldEntry, MemberGroup, UserTag} from '@/api/types'
import type {BatchRow} from '@/api/events'
import {attendance, events, memberGroups as memberGroupsApi, userTags as userTagsApi} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

// State
const step = ref(1)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

// Data
const layouts = ref<EventLayout[]>([])
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const attendanceFields = ref<AttendanceTemplateField[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])

// Step 1: Layout
const selectedLayoutId = ref<number | null>(null)
const fieldDefs = ref<EventFieldEntry[]>([])
const eventName = ref('')
const eventDescription = ref('')
const selectedCategoryId = ref('')
const selectedTemplateId = ref('')
const requiresRegistration = ref(false)
const requiresConfirmation = ref(false)
const hasDeadline = ref(false)
const registrationDeadline = ref('')
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])


// Step 3: Table
const rows = ref<BatchRow[]>([])

async function loadData() {
  loading.value = true
  try {
    const [layoutList, catList, tplList, groupList, tagList] = await Promise.all([
      events.listLayouts(),
      events.listCategories(),
      attendance.listTemplates(),
      memberGroupsApi.listGroups(),
      userTagsApi.listTags(),
    ])
    layouts.value = layoutList
    categories.value = catList
    templates.value = tplList
    groups.value = groupList
    tags.value = tagList
    const allFields: AttendanceTemplateField[] = []
    for (const tmpl of tplList) {
      const fields = await attendance.listTemplateFields(tmpl.id)
      allFields.push(...fields)
    }
    attendanceFields.value = allFields
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})

async function loadLayoutFields() {
  if (selectedLayoutId.value == null) return
  const fields = await events.getLayoutFields(selectedLayoutId.value)
  fieldDefs.value = fields.map(f => ({
    name: f.name,
    fieldType: f.fieldType,
    config: f.config ? JSON.parse(f.config) : {},
    overview: f.overview,
    attendanceFieldId: f.attendanceFieldId ?? null,
  }))
}

function onScheduleDone(newRows: BatchRow[]) {
  rows.value = newRows
  step.value = 3
}

async function createBatch() {
  saving.value = true
  error.value = ''
  try {
    const inlineFields: LayoutFieldEntry[] = fieldDefs.value.filter(f => f.name.trim()).map(f => ({
      name: f.name,
      fieldType: f.fieldType,
      config: f.config ?? undefined,
      overview: f.overview,
      attendanceFieldId: f.attendanceFieldId,
    }))
    const created = await events.createBatchEvents({
      name: eventName.value || undefined,
      description: eventDescription.value || undefined,
      categoryId: selectedCategoryId.value ? Number(selectedCategoryId.value) : undefined,
      templateId: selectedTemplateId.value ? Number(selectedTemplateId.value) : undefined,
      layoutId: selectedLayoutId.value,
      inlineFields: selectedLayoutId.value ? undefined : inlineFields,
      rows: rows.value,
      requiresRegistration: requiresRegistration.value,
      requiresConfirmation: requiresConfirmation.value,
      registrationDeadline: registrationDeadline.value || undefined,
      restrictedUserTypes: selectedUserTypes.value.length > 0 ? selectedUserTypes.value : undefined,
      restrictedGroupIds: selectedGroupIds.value.length > 0 ? selectedGroupIds.value : undefined,
      restrictedTagIds: selectedTagIds.value.length > 0 ? selectedTagIds.value : undefined,
    })
    success.value = t('batchCreate.success', {count: created.length})
    setTimeout(() => router.push({name: 'events'}), 1500)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ViewContent>
    <SectionHeader>{{ t('batchCreate.title') }}</SectionHeader>
    <Spinner v-if="loading"/>
    <template v-else>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <!-- Step indicator -->
      <StepProgressBar class="mb-4"
          :steps="[
            { label: t('batchCreate.stepLayout') },
            { label: t('batchCreate.stepSchedule'), disabled: fieldDefs.length === 0 },
            { label: t('batchCreate.stepTable'), disabled: rows.length === 0 },
          ]"
          :current-step="step - 1"
          @update:current-step="step = $event + 1"
      />

      <!-- Step 1: Layout -->
      <NeutralContainer v-if="step === 1" class="space-y-6">
        <EventFormPanel
            v-model:name="eventName"
            v-model:description="eventDescription"
            v-model:category-id="selectedCategoryId"
            v-model:template-id="selectedTemplateId"
            v-model:requires-registration="requiresRegistration"
            v-model:requires-confirmation="requiresConfirmation"
            v-model:has-deadline="hasDeadline"
            v-model:registration-deadline="registrationDeadline"
            v-model:selected-user-types="selectedUserTypes"
            v-model:selected-group-ids="selectedGroupIds"
            v-model:selected-tag-ids="selectedTagIds"
            v-model:fields="fieldDefs"
            :categories="categories"
            :templates="templates"
            :attendance-fields="attendanceFields"
            :groups="groups"
            :tags="tags"
        />

        <!-- Load from layout -->
        <div v-if="layouts.length > 0" class="space-y-2">
          <FieldLabel>{{ t('eventLayouts.loadFromLayout') }}</FieldLabel>
          <SelectInput :model-value="String(selectedLayoutId ?? '')"
                       @update:model-value="selectedLayoutId = $event ? Number($event) : null; loadLayoutFields()">
            <option value="">—</option>
            <option v-for="layout in layouts" :key="layout.id" :value="String(layout.id)">{{ layout.name }}</option>
          </SelectInput>
        </div>

        <div class="flex justify-end">
          <PrimaryButton @click="step = 2">
            {{ t('common.next') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>

      <!-- Step 2: Schedule or CSV -->
      <BatchScheduleStep
          v-if="step === 2"
          :field-defs="fieldDefs"
          @done="onScheduleDone"
          @error="error = $event"
      />

      <!-- Step 3: Table -->
      <template v-if="step === 3">
        <BatchEditTable :field-defs="fieldDefs" :rows="rows" @update:rows="rows = $event"/>

        <div class="flex justify-between mt-4">
          <MutedText tag="span" size="sm">
            {{ t('batchCreate.eventsCount', {count: rows.length}) }}
          </MutedText>
          <PrimaryButton :disabled="saving || rows.length === 0" @click="createBatch">
            {{ saving ? t('batchCreate.creating') : t('batchCreate.create') }}
          </PrimaryButton>
        </div>
      </template>
    </template>
  </ViewContent>
</template>
