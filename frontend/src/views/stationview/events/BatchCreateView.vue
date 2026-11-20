/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFormPanel from './eventshared/EventFormPanel.vue'
import {emptyRestriction, toRestriction} from '@/components/input/restriction'
import type {RestrictionSelection} from '@/api/types'
import BatchScheduleStep from './batchcreateview/BatchScheduleStep.vue'
import BatchEditTable from './batchcreateview/BatchEditTable.vue'
import BatchTemplateNote from './batchcreateview/BatchTemplateNote.vue'
import {useBatchTemplate} from './batchcreateview/useBatchTemplate'
import {useQuestionsRequireSignUp} from './eventshared/useQuestionsRequireSignUp'
import {nextDraftRow, type KeyedDraftRow, type ScheduleMode} from './batchcreateview/draftRows'
import type {
    AttendanceTemplateField,
    BatchFieldEntryDto,
    BatchRow,
    EventFieldEntry,
    EventTemplate,
} from '@/api/generated/schema'
import {StationPermission} from '@/api/generated/schema'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useEventEditDeps} from '@/composables/useEventEditDeps'
import {describeFailure, saying} from '@/util/failure'
import {asSaved} from './eventshared/eventQuestions'

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()
const {loaded, hasPermission, stationTimezone} = useSession()
const batchTemplate = useBatchTemplate(computed(() => hasPermission(StationPermission.EVENTS_FEDERATE)))
const templateLeftOut = computed(() => batchTemplate.leftOut(selectedTemplateId.value))

const step = ref(1)
const success = ref('')
/**
 * Whether the batch was created. The form is put away from then on, also where sharing the
 * appointments with partners failed afterwards, so creating again cannot make the batch twice.
 */
const done = ref(false)

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
const {signUpHeld} = useQuestionsRequireSignUp(
    () => (batchTemplate.applied.value?.questions ?? 0) > 0, requiresRegistration)
const requiresConfirmation = ref(false)
const restriction = ref<RestrictionSelection>(emptyRestriction())
const viewRestriction = ref<RestrictionSelection>(emptyRestriction())

const rows = ref<BatchRow[]>([])
const scheduleMode = ref<ScheduleMode>('schedule')
const drafts = ref<KeyedDraftRow[]>([nextDraftRow([])])

/**
 * The fields of the attendance sheet the batch names, and of no other.
 *
 * <p>Every sheet of the station used to be offered to take over, so a batch of drills was offered
 * the questions of the youth sheet as well, and two sheets that both carry a field of the same name
 * were indistinguishable in the list. The answer to a field taken from the wrong sheet goes to a
 * sheet nobody opens.
 */
const sheetFields = computed(() => attendanceFields.value
    .filter(field => String(field.templateId) === selectedTemplateId.value))

const {loading, failure, reload} = useAsyncLoader(async () => {
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

/**
 * Fills the batch from an appointment template: the form takes what it shows, and what it does not
 * show goes with the batch request or is named as left out, the way the single editor and the event
 * group editor take a template.
 */
async function applyTemplate() {
  if (selectedSourceTemplateId.value == null) return
  try {
    const detail = await events.getTemplate(selectedSourceTemplateId.value)
    const tpl = detail.template
    if (tpl.title) eventName.value = tpl.title
    if (tpl.description) eventDescription.value = tpl.description
    if (tpl.categoryId) selectedCategoryId.value = String(tpl.categoryId)
    if (tpl.attendanceTemplateId) selectedTemplateId.value = String(tpl.attendanceTemplateId)
    if (detail.restriction) {
      restriction.value = toRestriction(detail.restriction.register)
      viewRestriction.value = toRestriction(detail.restriction.view)
    }
    if (tpl.requiresRegistration != null) requiresRegistration.value = tpl.requiresRegistration
    if (tpl.requiresConfirmation != null) requiresConfirmation.value = tpl.requiresConfirmation
    fieldDefs.value = detail.fields.map(f => ({
      name: f.name,
      fieldType: f.fieldType,
      config: f.config,
      value: f.defaultValue ?? '',
      overview: f.overview,
      attendanceFieldId: f.attendanceFieldId,
      isPublic: f.isPublic,
    }))
    await batchTemplate.apply(detail, tpl.id)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

/**
 * Takes over the dates and starts every one of them off with the values the fields carry.
 *
 * <p>A series made from a template asks the same questions with the same answers on most of its
 * dates, so the table opens filled in rather than empty. A date that brought its own value, from an
 * imported file, keeps it.
 */
function onScheduleDone(newRows: BatchRow[]) {
  const defaults: Record<string, string> = {}
  for (const field of fieldDefs.value) {
    if (field.name?.trim() && field.value) defaults[field.name] = field.value
  }
  rows.value = newRows.map(row => ({...row, fieldValues: {...defaults, ...(row.fieldValues ?? {})}}))
  step.value = 3
}

const {running: saving, failure: createFailure, run: createBatch} = useAsyncAction(async () => {
  failure.value = null
  const inlineFields: BatchFieldEntryDto[] = fieldDefs.value.map(asSaved).flatMap(f => (f.name?.trim() ? [{
    name: f.name,
    fieldType: f.fieldType,
    config: f.config,
    overview: f.overview,
    attendanceFieldId: f.attendanceFieldId,
  }] : []))
  const created = await events.createBatchEvents({
    name: eventName.value || undefined,
    description: eventDescription.value || undefined,
    categoryId: selectedCategoryId.value ? Number(selectedCategoryId.value) : undefined,
    templateId: selectedTemplateId.value ? Number(selectedTemplateId.value) : undefined,
    inlineFields,
    rows: rows.value,
    requiresRegistration: requiresRegistration.value,
    requiresConfirmation: requiresConfirmation.value,
    restriction: restriction.value,
    viewRestriction: viewRestriction.value,
    ...batchTemplate.requestPart(requiresRegistration.value),
  })
  done.value = true
  success.value = t('batchCreate.success', {count: created.length})
  try {
    await batchTemplate.shareEach(created.map(event => event.id))
  } catch (e) {
    failure.value = saying(describeFailure(e, t), t('batchCreate.shareFailed'))
    return
  }
  setTimeout(openOverview, 1500)
})

/** Leaves the batch for the list of appointments. */
function openOverview() {
  router.push({name: eventRoutes.index})
}
</script>

<template>
  <ViewContent
      :title="t('pages.event-batch.title')"
      :subtitle="t('pages.event-batch.subtitle')"
  >
    <Spinner v-if="loading"/>
    <template v-else>
      <FailureAlert :failure="failure ?? createFailure"/>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <div v-if="done && (failure ?? createFailure)" class="flex justify-end">
        <PrimaryButton data-testid="batch-open-overview" @click="openOverview">{{ t('batchCreate.toOverview') }}</PrimaryButton>
      </div>

      <template v-if="!done">
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
          <div v-if="eventTemplates.length > 0" class="space-y-2">
            <FieldLabel>{{ t('batchCreate.applyTemplate') }}</FieldLabel>
            <SelectInput :model-value="String(selectedSourceTemplateId ?? '')"
                         @update:model-value="selectedSourceTemplateId = $event ? Number($event) : null; applyTemplate()">
              <option value="">-</option>
              <option v-for="tpl in eventTemplates" :key="tpl.id" :value="String(tpl.id)">{{ tpl.name }}</option>
            </SelectInput>
          </div>

          <EventFormPanel
              v-model:name="eventName"
              v-model:description="eventDescription"
              v-model:category-id="selectedCategoryId"
              v-model:template-id="selectedTemplateId"
              v-model:requires-registration="requiresRegistration"
              v-model:requires-confirmation="requiresConfirmation"
              v-model:registration-deadline="batchTemplate.settings.registrationDeadline"
              v-model:restriction="restriction"
              v-model:view-restriction="viewRestriction"
              v-model:fields="fieldDefs"
              v-model:registration-limit="batchTemplate.settings.registrationLimit"
              v-model:queue-order="batchTemplate.settings.queueOrder"
              v-model:min-registrations="batchTemplate.settings.minRegistrations"
              v-model:threshold-days="batchTemplate.settings.thresholdDays"
              :categories="categories"
              :templates="templates"
              :attendance-fields="sheetFields"
              :groups="groups"
              :tags="tags"
              :sign-up-held="signUpHeld"
              :time-zone="stationTimezone"
              open-fields
          />

          <BatchTemplateNote :carried="batchTemplate.carried.value" :left-out="templateLeftOut"/>

          <div class="flex justify-end">
            <PrimaryButton @click="step = 2">
              {{ t('common.next') }}
            </PrimaryButton>
          </div>
        </NeutralContainer>

        <BatchScheduleStep
            v-if="step === 2"
            v-model:mode="scheduleMode"
            v-model:drafts="drafts"
            :field-defs="fieldDefs"
            @done="onScheduleDone"
            @error="failure = $event"
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
    </template>
  </ViewContent>
</template>
