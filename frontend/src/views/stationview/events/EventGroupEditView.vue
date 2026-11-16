/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {until} from '@vueuse/core'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EventGroupSetsEditor from './eventgroupeditview/EventGroupSetsEditor.vue'
import EventGroupDetailsForm from './eventgroupeditview/EventGroupDetailsForm.vue'
import StartingAppointmentNotice from './eventgroupeditview/StartingAppointmentNotice.vue'
import type {SetRow} from './eventgroupeditview/eventGroupSetRow'
import EventGroupPartsEditor from './eventgroupeditview/EventGroupPartsEditor.vue'
import EventGroupShareCard from './eventgroupeditview/EventGroupShareCard.vue'
import {useEventFederationShare} from './eventeditview/useEventFederationShare'
import {eventGroupFederationPort, templateFieldDefaultsPort} from './eventeditview/eventOwnerPorts'
import EventGroupQuestionsCard from './eventgroupeditview/EventGroupQuestionsCard.vue'
import EventGroupFieldsCard from './eventgroupeditview/EventGroupFieldsCard.vue'
import EventGroupDateSettingsSection from './eventgroupeditview/EventGroupDateSettingsSection.vue'
import EventGroupFilesSection from './eventgroupeditview/EventGroupFilesSection.vue'
import {useDocumentRequirements} from './eventshared/useDocumentRequirements'
import type {RequirementOwner} from '@/api/appointmentDocuments'
import {useEventGroupSettings} from './eventgroupeditview/useEventGroupSettings'
import EventGroupTemplateStart from './eventgroupeditview/EventGroupTemplateStart.vue'
import {groupFieldsOf, groupQuestionsOf, useEventGroupTemplate, newByName} from './eventgroupeditview/eventGroupTemplate'
import {emptyRestriction, toRestriction} from '@/components/input/restriction'
import {entryOf, namedEntries} from './eventshared/eventQuestions'
import type {EventGroupDetail, EventGroupDetails, MemberGroup, MemberWithName, EventFieldEntry, RegistrationFieldDefinition, StationEvent, UserTag} from '@/api/generated/schema'
import {EventType, StationPermission} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {eventGroups, events, memberGroups as memberGroupsApi, stationMembers, userTags as userTagsApi} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {showToast} from '@/util/toast'

/**
 * Creates or edits an event group: its name, description and sign-up (places, deadline, confirmation),
 * its sets with their minimums, who may see and sign up, and, once it exists, its parts. Saving
 * stores all of it in one request, so a refusal leaves nothing half saved, and hands the sign-up and
 * the audiences down to every part. Changing parts reads only the parts again and leaves unsaved
 * edits in the form alone.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission} = useSession()
const canFederate = computed(() => hasPermission(StationPermission.EVENTS_FEDERATE))
const federationShare = useEventFederationShare(canFederate)

const eventGroupId = computed(() => (route.params.eventGroupId ? Number(route.params.eventGroupId) : null))
const detail = ref<EventGroupDetail | null>(null)
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<MemberWithName[]>([])
const groupMembers = ref(new Map<number, MemberWithName[]>())

const details = ref<EventGroupDetails>({
  name: '', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false,
  color: null, minRegistrations: null, thresholdDays: null,
})
const sets = ref<SetRow[]>([{id: null, name: t('eventGroups.defaultSet'), minimum: 1}])
const registration = ref<RestrictionSelection>(emptyRestriction())
const view = ref<RestrictionSelection>(emptyRestriction())
const questions = ref<RegistrationFieldDefinition[]>([])
const fields = ref<EventFieldEntry[]>([])
const startingAppointment = ref<StationEvent | null>(null)
const saving = ref(false)
const dateSettings = useEventGroupSettings()
const cancelTarget = computed(() =>
    eventGroupId.value == null ? {name: 'events'} : {name: 'event-group', params: {eventGroupId: eventGroupId.value}})
const requirements = useDocumentRequirements()
const fromTemplate = useEventGroupTemplate()

/**
 * Fills the new event group from an appointment template: its description, category, attendance
 * template with its pre-fills, reminders, the fields an event group can take, its questions, documents
 * to bring and audiences. Times and sets stay as they are; the files are attached once it is saved.
 */
async function applyTemplate(templateId: number) {
  const template = await fromTemplate.read(templateId)
  if (template.template.description) details.value = {...details.value, description: template.template.description}
  dateSettings.takeFrom(template)
  await dateSettings.fieldDefaults.takeFrom(templateFieldDefaultsPort(templateId))
  const taken = groupFieldsOf(template.fields)
  const newFields = newByName(fields.value, taken.kept)
  const newQuestions = newByName(questions.value, groupQuestionsOf(template))
  fields.value = [...fields.value, ...newFields.added]
  questions.value = [...questions.value, ...newQuestions.added]
  fromTemplate.leftOut.value = taken.leftOut
  fromTemplate.present.value = [...newFields.present, ...newQuestions.present]
  if (template.restriction) {
    registration.value = toRestriction(template.restriction.register)
    view.value = toRestriction(template.restriction.view)
  }
  await requirements.takeFrom({kind: 'event-templates', id: templateId})
}

/** Says which of a template's files could not be attached because the library no longer holds them. */
function reportGoneFiles(gone: string[]) {
  if (gone.length > 0) showToast(t('eventGroups.fromTemplateFilesGone', {files: gone.join(', ')}), 'error')
}

function requirementOwner(id: number): RequirementOwner {
  return {kind: 'events/groups', id}
}
const saveFailure = ref<Failure | null>(null)

/** Fills the form from the stored event group. */
function fill(loadedDetail: EventGroupDetail) {
  detail.value = loadedDetail
  details.value = {...loadedDetail.eventGroup.details}
  sets.value = loadedDetail.sets.map(set => ({id: set.id, name: set.name, minimum: set.minimum}))
  registration.value = toRestriction(loadedDetail.registration)
  view.value = toRestriction(loadedDetail.view)
  questions.value = loadedDetail.questions.map(({name, fieldType, config, overview}) => ({name, fieldType, config, overview}))
  fields.value = loadedDetail.fields.map(field => ({...entryOf(field), id: field.id}))
}

function savedQuestions(): RegistrationFieldDefinition[] {
  return namedEntries(questions.value)
}

const {loading, failure} = useAsyncLoader(async () => {
  await until(loaded).toBe(true)
  const [memberGroups, userTags, stationMemberList] = await Promise.all([
    memberGroupsApi.listGroups(),
    userTagsApi.listChoosableTags(),
    stationMembers.listMembers(),
  ])
  groups.value = memberGroups
  tags.value = userTags
  members.value = stationMemberList
  const ofGroups = await Promise.all(memberGroups.map(async group => [group.id, await memberGroupsApi.getGroupMembers(group.id)] as const))
  groupMembers.value = new Map(ofGroups)
  if (eventGroupId.value != null) {
    fill(await eventGroups.getEventGroup(eventGroupId.value))
    await dateSettings.load(eventGroupId.value)
    await requirements.load(requirementOwner(eventGroupId.value))
    await federationShare.load(eventGroupFederationPort(eventGroupId.value))
  } else {
    await requirements.load(null)
    await fromTemplate.loadTemplates()
    await takeStartingAppointment()
  }
})

/**
 * A new event group started from an appointment ("new event group with this appointment") names it,
 * starts from its name and audiences, and takes it in as its first part when it is created, in the
 * same request: where it cannot join, no group is left behind. Only a one-time appointment of this
 * station is taken; any other address is ignored.
 */
async function takeStartingAppointment() {
  const id = Number(route.query.appointment)
  if (!Number.isInteger(id) || id <= 0) return
  const [event, audiences] = await Promise.all([
    events.getEvent(id).catch(() => null),
    events.getRestrictions(id).catch(() => null),
  ])
  if (!event || event.eventType !== EventType.ONE_TIME) return
  startingAppointment.value = event
  if (!details.value.name) details.value = {...details.value, name: event.name ?? ''}
  if (audiences) {
    registration.value = toRestriction(audiences.register)
    view.value = toRestriction(audiences.view)
  }
}

function dropStartingAppointment() {
  startingAppointment.value = null
}

/**
 * Reads the event group again after a part changed, touching nothing the form holds unsaved, except a
 * set minimum that now asks for more parts than the set holds and is not the one the set keeps while it
 * fills up: the form takes the server's, or the part count where that is lower, and says so, or the next
 * save would be refused. A minimum the server raised to the parts a set requires is taken the same way.
 */
async function refreshParts() {
  if (eventGroupId.value == null) return
  const before = new Map((detail.value?.sets ?? []).map(set => [set.id, set.minimum]))
  detail.value = await eventGroups.getEventGroup(eventGroupId.value)
  for (const set of detail.value.sets) {
    const parts = partCounts.value[set.id] ?? 0
    const row = sets.value.find(candidate => candidate.id === set.id)
    if (row && set.minimum > (before.get(set.id) ?? set.minimum) && row.minimum < set.minimum) {
      sets.value = sets.value.map(candidate => (candidate.id === set.id ? {...candidate, minimum: set.minimum} : candidate))
      showToast(t('eventGroups.setMinimumRaised', {set: set.name, from: before.get(set.id), to: set.minimum}))
      continue
    }
    if (!row || parts === 0 || row.minimum <= parts || row.minimum === set.minimum) continue
    const to = Math.min(set.minimum, parts)
    sets.value = sets.value.map(candidate => (candidate.id === set.id ? {...candidate, minimum: to} : candidate))
    showToast(t('eventGroups.setMinimumLowered', {set: set.name, from: before.get(set.id) ?? row.minimum, to}))
  }
}

/** The minimum each saved set was stored with, which a set still filling up may keep. */
const storedMinimums = computed<Record<number, number>>(() =>
    Object.fromEntries((detail.value?.sets ?? []).map(set => [set.id, set.minimum])))

/** How many parts each saved set holds, which bounds its minimum. */
const partCounts = computed<Record<number, number>>(() => {
  const counts: Record<number, number> = {}
  for (const part of detail.value?.parts ?? []) counts[part.setId] = (counts[part.setId] ?? 0) + 1
  return counts
})

/** The details as they are saved: an empty description and a minimum of none or zero, with its days, left out. */
function detailsToSave(): EventGroupDetails {
  const entered = details.value
  return {
    name: entered.name ?? '',
    description: entered.description?.trim() ? entered.description : null,
    registrationLimit: entered.registrationLimit ?? null,
    registrationDeadline: entered.registrationDeadline ?? null,
    requiresConfirmation: entered.requiresConfirmation ?? false,
    color: entered.color || null,
    minRegistrations: entered.minRegistrations || null,
    thresholdDays: entered.minRegistrations ? entered.thresholdDays ?? null : null,
  }
}

/**
 * The event group this form created, once it did. The settings, documents and files are saved after
 * the group itself; where one of those is refused, the group already exists, and saving again has to
 * change it rather than create a second one.
 */
const createdEventGroupId = ref<number | null>(null)

async function save() {
  saving.value = true
  saveFailure.value = null
  try {
    const drafts = sets.value.map(set => ({id: set.id, name: set.name, minimum: set.minimum}))
    const savedId = eventGroupId.value ?? createdEventGroupId.value
    if (savedId == null) {
      const created = await eventGroups.createEventGroup({
        details: detailsToSave(),
        sets: drafts,
        registration: registration.value,
        view: view.value,
        questions: savedQuestions(),
        fields: namedEntries(fields.value),
        appointmentId: startingAppointment.value?.id ?? null,
      })
      createdEventGroupId.value = created.eventGroup.id
      startingAppointment.value = null
      fill(created)
      await dateSettings.save(created.eventGroup.id)
      await requirements.save(requirementOwner(created.eventGroup.id))
      reportGoneFiles(await fromTemplate.attachFiles(created.eventGroup.id))
      showToast(t('eventGroups.saved'), 'success')
      await router.replace({name: 'event-group-edit', params: {eventGroupId: created.eventGroup.id}})
      return
    }
    fill(await eventGroups.updateEventGroup(savedId, detailsToSave(), drafts, {
      registration: registration.value,
      view: view.value,
    }, savedQuestions(), namedEntries(fields.value)))
    await dateSettings.save(savedId)
    await requirements.save(requirementOwner(savedId))
    if (eventGroupId.value == null) {
      reportGoneFiles(await fromTemplate.attachFiles(savedId))
      showToast(t('eventGroups.saved'), 'success')
      await router.replace({name: 'event-group-edit', params: {eventGroupId: savedId}})
      return
    }
    await federationShare.save(eventGroupFederationPort(savedId))
  } catch (e) {
    saveFailure.value = describeFailure(e, t)
    throw e
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ViewContent
      :title="eventGroupId == null ? t('pages.event-group-new.title') : t('pages.event-group-edit.title')"
      :subtitle="eventGroupId == null ? t('pages.event-group-new.subtitle') : t('pages.event-group-edit.subtitle')"
  >
    <AsyncSection :loading="loading" :failure="failure">
      <div class="space-y-6">
        <StartingAppointmentNotice v-if="startingAppointment" :name="startingAppointment.name" @drop="dropStartingAppointment"/>
        <EventGroupTemplateStart v-if="eventGroupId == null" v-bind="fromTemplate.props.value" @apply="applyTemplate"/>
        <EventGroupDetailsForm v-model="details" :event-group-id="eventGroupId"/>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('eventGroups.sets') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('eventGroups.setsHint') }}</MutedText>
          <EventGroupSetsEditor v-model="sets" :part-counts="partCounts" :stored-minimums="storedMinimums"/>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('eventGroups.audiences') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('eventGroups.audienceHint') }}</MutedText>
          <FieldLabel>{{ t('eventGroups.registerAudience') }}</FieldLabel>
          <RestrictionsField v-model="registration" :groups="groups" :tags="tags"/>
          <FieldLabel>{{ t('eventGroups.viewAudience') }}</FieldLabel>
          <RestrictionsField v-model="view" :groups="groups" :tags="tags"/>
        </NeutralContainer>

        <EventGroupDateSettingsSection :settings="dateSettings"/>
        <EventGroupFilesSection :event-group-id="eventGroupId" :requirements="requirements"/>
        <EventGroupFieldsCard v-model="fields" :all-members="members" :groups="groups" :group-members="groupMembers" :tags="tags"/>
        <EventGroupQuestionsCard v-model="questions"/>

        <EventGroupShareCard v-if="detail" :federation-share="federationShare" :can-federate="canFederate"/>

        <FailureAlert v-if="saveFailure" :failure="saveFailure"/>
        <ButtonRow pair align="end">
          <SecondaryButton :to="cancelTarget">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :action="save" :disabled="saving" data-testid="event-group-save"/>
        </ButtonRow>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('eventGroups.parts') }}</SectionHeader>
          <MutedText v-if="!detail" tag="p" size="sm">{{ t('eventGroups.saveFirst') }}</MutedText>
          <EventGroupPartsEditor v-else :detail="detail" :on-changed="refreshParts"/>
        </NeutralContainer>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
