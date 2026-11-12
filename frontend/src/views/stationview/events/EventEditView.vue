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
import {events} from '@/api'
import {StationPermission, type EventAttachment, type RegistrationFieldDefinition} from '@/api/generated/schema'
import EventEditBody from './eventeditview/EventEditBody.vue'
import {useEventForm} from './eventeditview/useEventForm'
import {useEventEditData} from './eventeditview/useEventEditData'
import {useEventFieldDefaults} from './eventeditview/useEventFieldDefaults'
import {useEventFederationShare} from './eventeditview/useEventFederationShare'
import {useEventAttachments} from './eventeditview/useEventAttachments'
import AttachmentsCard from './eventeditview/AttachmentsCard.vue'
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
const eventId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => eventId.value !== null)

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
        fieldDefaults.load(eventFieldDefaultsPort(eventId.value!)),
        federationShare.load(eventFederationPort(eventId.value!)),
        loadRegistrationFields(eventId.value!),
        attachments.load(),
      ])
      openedName.value = form.state.name
    }
  } catch (e) {
    reportCaughtError(e, 'EventEditView.loadData')
    throw e
  }
})

async function writeEvent() {
  let savedEventId: number
  if (isEdit.value) {
    await events.updateEvent(eventId.value!, form.buildPayload())
    savedEventId = eventId.value!
  } else {
    const created = await events.createEvent(form.buildPayload())
    savedEventId = created.id
  }

  await fieldDefaults.save(eventFieldDefaultsPort(savedEventId), isEdit.value)
  await events.setEventReminders(savedEventId, form.state.reminders)
  await events.setEventFields(savedEventId, {fields: form.namedFields()})
  await events.setRegistrationFields(savedEventId, registrationFields.value.filter(f => f.name?.trim()).map(asSaved))
  await federationShare.save(eventFederationPort(savedEventId))
  await documentRequirements.save({kind: 'events', id: savedEventId})
}

const {running: saving, failure: saveFailure, run: submit} = useAsyncAction(async () => {
  failure.value = null
  refused.value = ''
  if (form.endsBeforeItStarts.value) {
    refused.value = t('events.endBeforeStart')
    return
  }

  try {
    await writeEvent()
  } catch (e) {
    reportCaughtError(e, 'EventEditView.submit')
    throw e
  }

  leaveEditor()
})

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
  saving: saving.value,
  canFederate: canFederate.value,
  federationNote: federationNote.value,
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
      <FailureAlert v-else :failure="failure ?? saveFailure"/>
      <Alert v-if="templateAppliedMessage" variant="success">{{ templateAppliedMessage }}</Alert>

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
  </ViewContent>
</template>
