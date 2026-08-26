/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StepProgressBar from '@/components/display/StepProgressBar.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFormPanel from './eventshared/EventFormPanel.vue'
import {type RestrictionSelection, emptyRestriction} from '@/components/input/restriction'
import BatchScheduleStep from './batchcreateview/BatchScheduleStep.vue'
import BatchEditTable from './batchcreateview/BatchEditTable.vue'
import type {AttendanceTemplateField} from '@/api/attendance'
import type {BatchFieldEntry, BatchRow, EventFieldEntry, EventTemplate} from '@/api/events'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useEventEditDeps} from '@/composables/useEventEditDeps'

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()
const {loaded} = useSession()

const step = ref(1)
const success = ref('')

const {categories, templates, groups, tags, reload: reloadDeps} = useEventEditDeps({autoLoad: false})
const attendanceFields = ref<AttendanceTemplateField[]>([])
const eventTemplates = ref<EventTemplate[]>([])

const selectedSourceTemplateId = ref<number | null>(null)
const fieldDefs = ref<EventFieldEntry[]>([])
const eventName = ref('')
const eventDescription = ref('')
const selectedCategoryId = ref('')
const selectedTemplateId = ref('')
const requiresRegistration = ref(false)
const requiresConfirmation = ref(false)
const hasDeadline = ref(false)
const registrationDeadline = ref('')
const restriction = ref<RestrictionSelection>(emptyRestriction())

const rows = ref<BatchRow[]>([])

const {loading, error, reload} = useAsyncLoader(async () => {
  await reloadDeps()
  const [allFields, evTpls] = await Promise.all([
    Promise.all(templates.value.map(tmpl => attendance.listTemplateFields(tmpl.id))),
    events.listTemplates().catch(() => [] as EventTemplate[]),
  ])
  attendanceFields.value = allFields.flat()
  eventTemplates.value = evTpls
}, {autoLoad: loaded.value})

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})

async function applyTemplate() {
  if (selectedSourceTemplateId.value == null) return
  try {
    const detail = await events.getTemplate(selectedSourceTemplateId.value)
    const tpl = detail.template
    if (tpl.title) eventName.value = tpl.title
    if (tpl.description) eventDescription.value = tpl.description
    if (tpl.categoryId) selectedCategoryId.value = String(tpl.categoryId)
    if (tpl.requiresRegistration != null) requiresRegistration.value = tpl.requiresRegistration
    if (tpl.requiresConfirmation != null) requiresConfirmation.value = tpl.requiresConfirmation
    fieldDefs.value = detail.fields.map(f => ({
      name: f.name,
      fieldType: f.fieldType ?? 'STRING',
      config: typeof f.config === 'string' ? (f.config ? JSON.parse(f.config) : {}) : (f.config ?? {}),
      value: '',
      overview: f.overview ?? false,
      attendanceFieldId: f.attendanceFieldId ?? null,
      isPublic: f.isPublic ?? false,
    }))
  } catch {
    error.value = t('common.error')
  }
}

function onScheduleDone(newRows: BatchRow[]) {
  rows.value = newRows
  step.value = 3
}

const {running: saving, error: createError, run: createBatch} = useAsyncAction(async () => {
  error.value = ''
  const inlineFields: BatchFieldEntry[] = fieldDefs.value.filter(f => f.name.trim()).map(f => ({
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
    inlineFields,
    rows: rows.value,
    requiresRegistration: requiresRegistration.value,
    requiresConfirmation: requiresConfirmation.value,
    registrationDeadline: registrationDeadline.value || undefined,
    restriction: restriction.value,
  })
  success.value = t('batchCreate.success', {count: created.length})
  setTimeout(() => router.push({name: eventRoutes.index}), 1500)
}, {formatError: () => t('common.error')})
</script>

<template>
  <ViewContent
      :title="t('pages.event-batch.title')"
      :subtitle="t('pages.event-batch.subtitle')"
  >
    <Spinner v-if="loading"/>
    <template v-else>
      <Alert v-if="error || createError" variant="error">{{ error || createError }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <StepProgressBar class="mb-4"
          :steps="[
            { label: t('batchCreate.stepFields') },
            { label: t('batchCreate.stepSchedule'), disabled: fieldDefs.length === 0 },
            { label: t('batchCreate.stepTable'), disabled: rows.length === 0 },
          ]"
          :current-step="step - 1"
          @update:current-step="step = $event + 1"
      />

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
            v-model:restriction="restriction"
            v-model:fields="fieldDefs"
            :categories="categories"
            :templates="templates"
            :attendance-fields="attendanceFields"
            :groups="groups"
            :tags="tags"
        />

        <div v-if="eventTemplates.length > 0" class="space-y-2">
          <FieldLabel>{{ t('batchCreate.applyTemplate') }}</FieldLabel>
          <SelectInput :model-value="String(selectedSourceTemplateId ?? '')"
                       @update:model-value="selectedSourceTemplateId = $event ? Number($event) : null; applyTemplate()">
            <option value="">-</option>
            <option v-for="tpl in eventTemplates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
          </SelectInput>
        </div>

        <div class="flex justify-end">
          <PrimaryButton @click="step = 2">
            {{ t('common.next') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>

      <BatchScheduleStep
          v-if="step === 2"
          :field-defs="fieldDefs"
          @done="onScheduleDone"
          @error="error = $event"
      />

      <template v-if="step === 3">
        <BatchEditTable v-model:rows="rows" :field-defs="fieldDefs"/>

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
