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
import {events} from '@/api'
import {StationPermission} from '@/api/types'
import EventEditBody from './eventeditview/EventEditBody.vue'
import {useEventForm} from './eventeditview/useEventForm'
import {useEventEditData} from './eventeditview/useEventEditData'
import {useEventFieldDefaults} from './eventeditview/useEventFieldDefaults'
import {useEventFederationShare} from './eventeditview/useEventFederationShare'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const canFederate = computed(() => hasPermission(StationPermission.EVENTS_FEDERATE))
const eventId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => eventId.value !== null)

const form = useEventForm()
const data = useEventEditData(() => form.state.templateId)
const fieldDefaults = useEventFieldDefaults()
const federationShare = useEventFederationShare(canFederate)

const loading = ref(true)
const error = ref('')
const {message: templateAppliedMessage, flash: flashTemplateApplied} = useFlashMessage(3000)

async function applyEventTemplate(templateId: string | undefined) {
  if (!templateId) return
  try {
    form.applyTemplate(await events.getTemplate(Number(templateId)))
    flashTemplateApplied(t('eventTemplates.applied'))
  } catch (e) { reportCaughtError(e, 'applyEventTemplate'); error.value = t('common.error') }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    await data.load()
    if (isEdit.value) {
      await Promise.all([
        form.loadEvent(eventId.value!),
        fieldDefaults.load(eventId.value!),
        federationShare.load(eventId.value!),
      ])
    }
  } catch (e) {
    reportCaughtError(e, 'EventEditView.loadData')
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const {running: saving, error: saveError, run: submit} = useAsyncAction(async () => {
  error.value = ''

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
  await federationShare.save(savedEventId)

  leaveEditor()
}, {formatError: (e) => {
  reportCaughtError(e, 'EventEditView.submit')
  return t('common.error')
}})

function leaveEditor() {
  const returnTo = typeof route.query.returnTo === 'string' ? route.query.returnTo : null
  if (returnTo && returnTo.startsWith('/')) {
    router.push(returnTo)
  } else {
    router.push({name: 'events'})
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
      :title="t('pages.event-new.title')"
      :subtitle="t('pages.event-new.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || saveError" variant="error">{{ error || saveError }}</Alert>
      <Alert v-if="templateAppliedMessage" variant="success">{{ templateAppliedMessage }}</Alert>

      <EventEditBody
          v-if="!loading"
          v-bind="bodyProps"
          v-on="bodyHandlers"
      />
    </div>
  </ViewContent>
</template>
