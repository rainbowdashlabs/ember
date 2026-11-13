/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {until} from '@vueuse/core'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {reportCaughtError} from '@/util/devErrorReporter'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import {eventGroups, events} from '@/api'
import {
  EditScopeKind,
  EventChangePart,
  StationPermission,
  type ChangeNotice,
  type EventGroupPartSummary,
  type EventAttachment,
  type RegistrationFieldDefinition,
} from '@/api/generated/schema'
import {isRecurringEvent} from '@/api/events'
import EventGroupPartNotice from './eventshared/EventGroupPartNotice.vue'
import {todayIsoDate} from '@/util/format'
import EventEditBody from './eventeditview/EventEditBody.vue'
import {useEventForm} from './eventeditview/useEventForm'
import {useEventEditData} from './eventeditview/useEventEditData'
import {useEventFieldDefaults} from './eventeditview/useEventFieldDefaults'
import {useEventFederationShare} from './eventeditview/useEventFederationShare'
import {useEventAttachments} from './eventeditview/useEventAttachments'
import AttachmentsCard from './eventeditview/AttachmentsCard.vue'
import EventSaveDialog from './eventeditview/EventSaveDialog.vue'
import {useSaveChoice, type SaveChoice} from './eventeditview/useSaveChoice'
import TemplateFilesNote from './eventeditview/TemplateFilesNote.vue'
import {
  eventAttachmentPort,
  eventFederationPort,
  eventFieldDefaultsPort,
  templateFederationPort,
  templateFieldDefaultsPort,
} from './eventeditview/eventOwnerPorts'
import DocumentRequirementsEditor from './eventshared/DocumentRequirementsEditor.vue'
import {useDocumentRequirements} from './eventshared/useDocumentRequirements'
import {asSaved} from './eventshared/eventQuestions'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const eventRoutes = useEventRoutes()
const {loaded, hasPermission, sessionInfo} = useSession()

const canFederate = computed(() => hasPermission(StationPermission.EVENTS_FEDERATE))
/**
 * The appointment the editor saves into: the one it was opened on, or the appointment a change of one
 * date or of later dates was saved into, once that save went through. A part that failed after it is
 * then saved again against the new appointment, which still holds every date the change moved.
 */
const savedInto = ref<number | null>(null)
const eventId = computed(() => savedInto.value ?? (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => eventId.value !== null)
const eventGroupPart = ref<EventGroupPartSummary | null>(null)

const stationUid = computed(() => sessionInfo.value?.stationId ?? '')

const form = useEventForm()
const attachments = useEventAttachments(() => (eventId.value === null ? null : eventAttachmentPort(eventId.value)), t)
const data = useEventEditData(
    () => form.state.templateId,
    () => form.state.fields.map(f => f.attendanceFieldId).filter((id): id is number => id != null),
)
const fieldDefaults = useEventFieldDefaults()
const federationShare = useEventFederationShare(canFederate)
const documentRequirements = useDocumentRequirements()

/** The ids of the fields on the attendance sheet the form names now. */
function sheetFieldIds(): number[] {
  return data.props.value.attendanceFields.map(field => field.id)
}

watch(sheetFieldIds, ids => fieldDefaults.keepOnly(ids))

/**
 * What the editor itself turned down, as opposed to what the server did. An end before its own
 * start is the reader's own typing, so it is said plainly and never offered as a bug to report.
 */
const refused = ref('')
const registrationFields = ref<RegistrationFieldDefinition[]>([])

/** What the share card says about the applied template's share it could not take over as it was. */
const federationNote = ref('')

/**
 * The files of the applied template, which the server hangs on the appointment when it creates it.
 * The appointment does not exist before that, so they are only named here.
 */
const templateFiles = ref<EventAttachment[]>([])
const {message: templateAppliedMessage, flash: flashTemplateApplied} = useFlashMessage(3000)

/**
 * What the appointment was called when the editor opened, which is what the head of the page says
 * while it is being changed. The field itself is not read, so the tab does not rewrite itself on
 * every keystroke, and a new appointment has no name to say.
 */
const openedName = ref('')

const pageTitle = computed(() => {
  if (!isEdit.value) return t('pages.event-new.title')
  if (!openedName.value) return t('pages.event-edit.title')
  return t('pages.event-edit.titleNamed', {name: openedName.value})
})

const pageSubtitle = computed(() =>
    isEdit.value ? t('pages.event-edit.subtitle') : t('pages.event-new.subtitle'))

/**
 * Fills the editor from an appointment template, its registration questions included.
 *
 * <p>The questions join the editor's list rather than arriving only on the server: the list is what
 * the editor writes back on save, so a question it did not hold would be removed again by the same
 * save that created the appointment.
 */
async function applyEventTemplate(templateId: string | undefined) {
  if (!templateId) return
  try {
    const detail = await events.getTemplate(Number(templateId))
    form.applyTemplate(detail)
    registrationFields.value = [...registrationFields.value, ...detail.registrationFields.map(asDefinition)]
    await documentRequirements.takeFrom({kind: 'event-templates', id: Number(templateId)})
    await takeOverCarried(Number(templateId))
    flashTemplateApplied(t('eventTemplates.applied'))
  } catch (e) {
    reportCaughtError(e, 'applyEventTemplate')
    failure.value = describeFailure(e, t)
  }
}

/**
 * Takes the template's field starting values and its partner share into the form, which the save
 * writes like any other. Its files are named for a new appointment, which the server attaches them
 * to on creation, and attached straight away to an appointment that already exists.
 *
 * <p>Starting values for fields that are not on the form's attendance sheet are dropped. A share is
 * skipped for somebody who may not share appointments, with a note in the share card, so the save
 * does not fail on a share they could not have set by hand either.
 */
async function takeOverCarried(templateId: number) {
  await fieldDefaults.takeFrom(templateFieldDefaultsPort(templateId))
  fieldDefaults.keepOnly(sheetFieldIds())
  const share = await federationShare.takeFrom(templateFederationPort(templateId))
  if (share.skipped) {
    federationNote.value = t('events.federationTemplateSkipped')
  } else if (share.droppedPartners > 0) {
    federationNote.value = t('events.federationTemplatePartnersDropped', {count: share.droppedPartners})
  } else {
    federationNote.value = ''
  }
  const files = await events.listTemplateAttachments(templateId)
  if (isEdit.value) {
    await attachTemplateFiles(files)
  } else {
    templateFiles.value = files
  }
}

/**
 * Hangs a template's files on the appointment being edited, in the template's order and with its
 * labels. A saved appointment has its files written as they change, and the server only links a
 * template's files when it creates an appointment, so an existing one gets them here. A file the
 * appointment already carries is left as it is.
 */
async function attachTemplateFiles(files: EventAttachment[]) {
  const port = eventAttachmentPort(eventId.value!)
  const carried = new Set(attachments.attachments.value.map(attachment => attachment.fileId))
  for (const file of files.filter(entry => !carried.has(entry.fileId))) {
    await port.attach(file.fileId, file.label, file.internal)
  }
  await attachments.load()
}

/** A question as the editor works on it: its definition, without the id of wherever it is stored. */
function asDefinition(field: RegistrationFieldDefinition): RegistrationFieldDefinition {
  return {
    name: field.name,
    fieldType: field.fieldType,
    config: field.config,
    overview: field.overview,
  }
}

/**
 * Loads the questions the event already asks, dropping their ids: the editor works on definitions
 * and the whole set is replaced on save.
 */
async function loadRegistrationFields(id: number) {
  const loadedFields = await events.listRegistrationFields(id).catch(() => [])
  registrationFields.value = loadedFields.map(asDefinition)
}

/** Fills the editor once the session is there, since the choices it offers depend on the station. */
const {loading, failure} = useAsyncLoader(async () => {
  await until(loaded).toBe(true)
  try {
    await data.load()
    await documentRequirements.load(isEdit.value ? {kind: 'events', id: eventId.value!} : null)
    if (isEdit.value) {
      await Promise.all([
        form.loadEvent(eventId.value!),
        eventGroups.eventGroupOfEvent(eventId.value!).then(part => { eventGroupPart.value = part }).catch(() => {}),
        fieldDefaults.load(eventFieldDefaultsPort(eventId.value!)),
        federationShare.load(eventFederationPort(eventId.value!)),
        loadRegistrationFields(eventId.value!),
        attachments.load(),
      ])
      openedName.value = form.state.name
      opened.value = snapshotOf()
      openedRecurring.value = isRecurringEvent(form.state.eventType)
      watch(attachments.attachments, () => { attachmentsChanged.value = true }, {deep: true})
    }
  } catch (e) {
    reportCaughtError(e, 'EventEditView.loadData')
    throw e
  }
})

/**
 * Saves the appointment and everything that hangs off it.
 *
 * <p>The appointment itself goes first, because the parts after it read what it now says: the
 * attendance sheet the field defaults and the ties of its questions are checked against. A change of
 * every date tells the people it concerns with that first save. A change of one date, or of one date
 * and the ones after it, is saved into a new appointment, which the parts are then saved against, and
 * tells once all of them have landed.
 *
 * @return the appointment everything was saved into
 */
async function writeEvent(choice: SaveChoice | null): Promise<number> {
  let savedEventId: number
  const scoped = choice != null && choice.scope.kind !== EditScopeKind.ALL
  if (isEdit.value) {
    const saved = await events.updateEvent(eventId.value!, {
      ...form.buildPayload(),
      scope: choice?.scope ?? null,
      changeNotice: scoped ? null : choice?.notice ?? null,
    })
    savedEventId = saved.id
    if (saved.id !== eventId.value) {
      savedInto.value = saved.id
      openedRecurring.value = isRecurringEvent(saved.eventType)
      await attachments.load()
    }
  } else {
    const created = await events.createEvent(form.buildPayload())
    savedEventId = created.id
  }

  await fieldDefaults.save(eventFieldDefaultsPort(savedEventId), isEdit.value)
  await events.setEventReminders(savedEventId, form.state.reminders)
  await events.setEventFields(savedEventId, {fields: form.namedFields()})
  if (!eventGroupPart.value) {
    await events.setRegistrationFields(savedEventId, registrationFields.value.filter(f => f.name?.trim()).map(asSaved))
  }
  await federationShare.save(eventFederationPort(savedEventId))
  await documentRequirements.save({kind: 'events', id: savedEventId})
  if (scoped) await tellLater(savedEventId, choice.notice)
  return savedEventId
}

/**
 * Sends the notice of an appointment a change was saved into. A notice that does not go through is
 * not worth keeping the editor open for: a moved start is told on its own a few minutes later.
 */
async function tellLater(id: number, notice: ChangeNotice) {
  try {
    await events.sendChangeNotice(id, notice)
  } catch (e) {
    reportCaughtError(e, 'EventEditView.sendChangeNotice')
  }
}

/**
 * What the editor changed, as a notice names it. The time and the date are left to the server, which
 * reads them from the save itself.
 */
const changedParts = computed<EventChangePart[]>(() => {
  const now = snapshotOf()
  const parts: EventChangePart[] = []
  if (!opened.value) return parts
  if (now.name !== opened.value.name) parts.push(EventChangePart.NAME)
  if (now.description !== opened.value.description) parts.push(EventChangePart.DESCRIPTION)
  if (now.fields !== opened.value.fields) parts.push(EventChangePart.FIELDS)
  if (now.questions !== opened.value.questions) parts.push(EventChangePart.REGISTRATION_QUESTIONS)
  if (attachmentsChanged.value) parts.push(EventChangePart.ATTACHMENTS)
  return parts
})

/** The parts a notice can name, as they stood when the editor opened, to tell what changed since. */
const opened = ref<ReturnType<typeof snapshotOf> | null>(null)
const attachmentsChanged = ref(false)
const openedRecurring = ref(false)

function snapshotOf() {
  return {
    name: form.state.name,
    description: form.state.description,
    fields: JSON.stringify(form.namedFields()),
    questions: JSON.stringify(registrationFields.value.filter(f => f.name?.trim()).map(asSaved)),
  }
}

/** The date the editor was opened from, which the save dialog starts on. */
const openedDate = computed(() => typeof route.query.date === 'string' ? route.query.date : null)

const saveChoice = useSaveChoice(() => eventId.value, () => form.buildPayload(), () => changedParts.value, t)

/** Whether the appointment has a date from today on, which is when saving asks what to tell. */
async function hasDatesAhead(id: number): Promise<boolean> {
  const next = await events.getNextDate(id).catch(() => null)
  return next != null && next >= todayIsoDate()
}

const {running: saving, failure: saveFailure, run: submit} = useAsyncAction(async () => {
  failure.value = null
  refused.value = ''
  if (form.endsBeforeItStarts.value) {
    refused.value = t('events.endBeforeStart')
    return
  }

  if (isEdit.value && await hasDatesAhead(eventId.value!)) {
    const fromEditor = openedDate.value && openedDate.value >= todayIsoDate() ? openedDate.value : null
    saveChoice.start(fromEditor ?? await events.getNextDate(eventId.value!).catch(() => null))
    return
  }
  await save(null)
})

const {running: confirming, failure: confirmFailure, run: confirmSave} = useAsyncAction(async () => {
  saveChoice.open.value = false
  await save(saveChoice.choice.value)
})

async function save(choice: SaveChoice | null) {
  let savedEventId: number
  try {
    savedEventId = await writeEvent(choice)
  } catch (e) {
    reportCaughtError(e, 'EventEditView.submit')
    throw e
  }
  if (savedInto.value != null) {
    await router.push({name: eventRoutes.detail, params: {id: savedEventId}})
    return
  }
  leaveEditor()
}

function leaveEditor() {
  const returnTo = typeof route.query.returnTo === 'string' ? route.query.returnTo : null
  if (returnTo && returnTo.startsWith('/')) {
    router.push(returnTo)
  } else {
    router.push({name: eventRoutes.index})
  }
}

function goBack() {
  leaveEditor()
}

const bodyProps = computed(() => ({
  isEdit: isEdit.value,
  saving: saving.value || confirming.value,
  canFederate: canFederate.value,
  federationNote: federationNote.value,
  eventGroupPart: eventGroupPart.value !== null,
  ...data.props.value,
  ...fieldDefaults.props.value,
  ...federationShare.props.value,
  ...form.props.value,
}))

const bodyHandlers = {
  ...form.handlers,
  ...fieldDefaults.handlers,
  ...federationShare.handlers,
  'apply-template': applyEventTemplate,
  cancel: goBack,
  submit,
}
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="pageSubtitle"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <FailureAlert v-if="refused" :message="refused" expected/>
      <FailureAlert v-else :failure="failure ?? saveFailure ?? confirmFailure"/>
      <Alert v-if="templateAppliedMessage" variant="success">{{ templateAppliedMessage }}</Alert>
      <EventGroupPartNotice v-if="eventGroupPart" :event-group="eventGroupPart" :message="t('eventGroups.lockedByEventGroup')"/>

      <EventEditBody
          v-if="!loading"
          v-model:registration-fields="registrationFields"
          v-bind="bodyProps"
          v-on="bodyHandlers"
      />

      <DocumentRequirementsEditor
          v-if="!loading"
          v-model="documentRequirements.chosen.value"
          :offered-count="documentRequirements.offeredCount.value"
      />

      <TemplateFilesNote v-if="!loading && !isEdit && templateFiles.length > 0" :files="templateFiles"/>

      <AttachmentsCard
          v-if="!loading && isEdit"
          v-model:attachments="attachments.attachments.value"
          :station-uid="stationUid"
          :failure="attachments.failure.value"
          @add="attachments.add"
          @save="attachments.save"
          @remove="attachments.remove"
          @reorder="attachments.reorder"
      />
    </div>

    <EventSaveDialog
        v-model="saveChoice.open.value"
        v-model:kind="saveChoice.kind.value"
        v-model:date="saveChoice.date.value"
        v-model:tell="saveChoice.tell.value"
        v-model:note="saveChoice.note.value"
        :series="openedRecurring"
        :preview="saveChoice.preview.value"
        :preview-failure="saveChoice.previewFailure.value"
        :files-changed="attachmentsChanged"
        :can-tell="saveChoice.canTell.value"
        :saving="confirming"
        @save="confirmSave"
    />
  </ViewContent>
</template>
