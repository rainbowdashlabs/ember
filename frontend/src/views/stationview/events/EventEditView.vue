/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {reportCaughtError} from '@/util/devErrorReporter'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure, type Failure} from '@/util/failure'
import {events} from '@/api'
import type {EventRegistrationFieldDefinition} from '@/api/events'
import {StationPermission} from '@/api/types'
import EventEditBody from './eventeditview/EventEditBody.vue'
import {useEventForm} from './eventeditview/useEventForm'
import {useEventEditData} from './eventeditview/useEventEditData'
import {useEventFieldDefaults} from './eventeditview/useEventFieldDefaults'
import {useEventFederationShare} from './eventeditview/useEventFederationShare'
import {useEventAttachments} from './eventeditview/useEventAttachments'
import AttachmentsCard from './eventeditview/AttachmentsCard.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
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
const attachments = useEventAttachments(() => eventId.value, t)
const data = useEventEditData(
    () => form.state.templateId,
    () => form.state.fields.map(f => f.attendanceFieldId).filter((id): id is number => id != null),
)
const fieldDefaults = useEventFieldDefaults()
const federationShare = useEventFederationShare(canFederate)

const loading = ref(true)
const failure = ref<Failure | null>(null)

/**
 * What the editor itself turned down, as opposed to what the server did. An end before its own
 * start is the reader's own typing, so it is said plainly and never offered as a bug to report.
 */
const refused = ref('')
const registrationFields = ref<EventRegistrationFieldDefinition[]>([])
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

async function applyEventTemplate(templateId: string | undefined) {
  if (!templateId) return
  try {
    form.applyTemplate(await events.getTemplate(Number(templateId)))
    flashTemplateApplied(t('eventTemplates.applied'))
  } catch (e) {
    reportCaughtError(e, 'applyEventTemplate')
    failure.value = describeFailure(e, t)
  }
}

/**
 * Loads the questions the event already asks, dropping their ids: the editor works on definitions
 * and the whole set is replaced on save.
 */
async function loadRegistrationFields(id: number) {
  const loadedFields = await events.listRegistrationFields(id).catch(() => [])
  registrationFields.value = loadedFields.map(f => ({
    name: f.name,
    fieldType: f.fieldType,
    config: f.config ?? {},
    overview: f.overview,
  }))
}

async function loadData() {
  loading.value = true
  failure.value = null
  try {
    await data.load()
    if (isEdit.value) {
      await Promise.all([
        form.loadEvent(eventId.value!),
        fieldDefaults.load(eventId.value!),
        federationShare.load(eventId.value!),
        loadRegistrationFields(eventId.value!),
        attachments.load(),
      ])
      openedName.value = form.state.name
    }
  } catch (e) {
    reportCaughtError(e, 'EventEditView.loadData')
    failure.value = describeFailure(e, t)
  } finally {
    loading.value = false
  }
}

async function writeEvent() {
  let savedEventId: number
  if (isEdit.value) {
    await events.updateEvent(eventId.value!, form.buildPayload())
    savedEventId = eventId.value!
  } else {
    const created = await events.createEvent(form.buildPayload())
    savedEventId = created.id
  }

  await fieldDefaults.save(savedEventId, isEdit.value)
  await events.setEventReminders(savedEventId, form.state.reminders)
  await events.setEventFields(savedEventId, {fields: form.namedFields()})
  await events.setRegistrationFields(savedEventId, registrationFields.value.filter(f => f.name.trim() !== ''))
  await federationShare.save(savedEventId)
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

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})

const bodyProps = computed(() => ({
  isEdit: isEdit.value,
  saving: saving.value,
  canFederate: canFederate.value,
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
