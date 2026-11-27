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
import MutedText from '@/components/typography/MutedText.vue'
import HintedSubHeader from '@/components/typography/HintedSubHeader.vue'
import FormSection from '@/components/container/FormSection.vue'
import {audienceSummary} from './eventshared/sectionSummaries'
import {deadlineRuleOf, noDeadline} from './eventshared/registrationDeadline'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EventGroupSetsEditor from './eventgroupeditview/EventGroupSetsEditor.vue'
import EventGroupAboutSection from './eventgroupeditview/EventGroupAboutSection.vue'
import EventGroupSignUpFields from './eventgroupeditview/EventGroupSignUpFields.vue'
import AudienceRestrictions from './eventshared/AudienceRestrictions.vue'
import StartingAppointmentNotice from './eventgroupeditview/StartingAppointmentNotice.vue'
import type {SetRow} from './eventgroupeditview/eventGroupSetRow'
import EventGroupPartsEditor from './eventgroupeditview/EventGroupPartsEditor.vue'
import EventGroupShareCard from './eventgroupeditview/EventGroupShareCard.vue'
import {useEventFederationShare} from './eventeditview/useEventFederationShare'
import {eventGroupFederationPort, templateFieldDefaultsPort} from './eventeditview/eventOwnerPorts'
import EventGroupQuestionsCard from './eventgroupeditview/EventGroupQuestionsCard.vue'
import EventGroupFilesSection from './eventgroupeditview/EventGroupFilesSection.vue'
import {useDocumentRequirements} from './eventshared/useDocumentRequirements'
import type {RequirementOwner} from '@/api/appointmentDocuments'
import {useEventGroupSettings} from './eventgroupeditview/useEventGroupSettings'
import EventGroupTemplateStart from './eventgroupeditview/EventGroupTemplateStart.vue'
import {groupFieldsOf, groupQuestionsOf, useEventGroupTemplate, newByName} from './eventgroupeditview/eventGroupTemplate'
import {emptyRestriction, toRestriction} from '@/components/input/restriction'
import {entryOf, namedEntries} from './eventshared/eventQuestions'
import type {EventGroupDetail, EventGroupDetails, EventGroupSet, MemberGroup, MemberWithName, EventFieldEntry, RegistrationFieldDefinition, StationEvent, UserTag} from '@/api/generated/schema'
import {EventType, StationPermission} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {eventGroups, events, memberGroups as memberGroupsApi, stationMembers, userTags as userTagsApi} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {showToast} from '@/util/toast'

/**
 * Creates or edits an event group: its name, description and sign-up (places, deadline, confirmation),
 * its sets with their minimums, who may see and sign up, how it is shared with partners, and, once it
 * exists, its parts. Saving stores the event group itself in one request, so a refusal leaves nothing
 * half saved, and hands the sign-up and the audiences down to every part. Changing parts reads only the
 * parts again and leaves unsaved edits in the form alone.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission, stationTimezone} = useSession()
const canFederate = computed(() => hasPermission(StationPermission.EVENTS_FEDERATE))
const federationShare = useEventFederationShare(canFederate)

const eventGroupId = computed(() => (route.params.eventGroupId ? Number(route.params.eventGroupId) : null))
const detail = ref<EventGroupDetail | null>(null)
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<MemberWithName[]>([])
const groupMembers = ref(new Map<number, MemberWithName[]>())

const details = ref<EventGroupDetails>({
  name: '', description: null, registrationLimit: null, registrationDeadline: noDeadline(), requiresConfirmation: false,
  color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null,
})
const sets = ref<SetRow[]>([{id: null, name: t('eventGroups.defaultSet'), minimum: 1, maximum: null}])
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
  const taken = groupFieldsOf(template.fields, template.template.attendanceTemplateId != null)
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

/** Reports which of a template's files could not be attached because the library no longer holds them. */
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
  sets.value = loadedDetail.sets.map(set => ({id: set.id, name: set.name, minimum: set.minimum, maximum: set.maximum}))
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
    await federationShare.loadPartners()
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
 * fills up: the form takes the server's, or the part count where that is lower, and reports it, or the next
 * save would be refused. A minimum the server raised to the parts a set requires is taken the same way.
 */
async function refreshParts() {
  if (eventGroupId.value == null) return
  const before = new Map((detail.value?.sets ?? []).map(set => [set.id, set.minimum]))
  const maximumsBefore = new Map((detail.value?.sets ?? []).map(set => [set.id, set.maximum]))
  detail.value = await eventGroups.getEventGroup(eventGroupId.value)
  for (const set of detail.value.sets) {
    takeRaisedMaximum(set, maximumsBefore.get(set.id) ?? null)
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

/**
 * Takes a maximum the server raised because a part of the set became required, the same way a raised
 * minimum is taken, and reports it. A maximum the form already holds at least as high is left alone.
 */
function takeRaisedMaximum(set: EventGroupSet, before: number | null) {
  const row = sets.value.find(candidate => candidate.id === set.id)
  if (!row || set.maximum == null || before == null || set.maximum <= before) return
  if (row.maximum != null && row.maximum >= set.maximum) return
  sets.value = sets.value.map(candidate => (candidate.id === set.id ? {...candidate, maximum: set.maximum} : candidate))
  showToast(t('eventGroups.setMaximumRaised', {set: set.name, from: before, to: set.maximum}))
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
    registrationDeadline: deadlineRuleOf(entered.registrationDeadline),
    requiresConfirmation: entered.requiresConfirmation ?? false,
    color: entered.color || null,
    minRegistrations: entered.minRegistrations || null,
    thresholdDays: entered.minRegistrations ? entered.thresholdDays ?? null : null,
    partRegistrationLimit: entered.partRegistrationLimit || null,
    queueOrder: entered.queueOrder ?? null,
  }
}

/**
 * The event group this form created, once it did. The settings, documents, share and files are saved
 * after the group itself; where one of those is refused, the group already exists, and saving again has
 * to change it rather than create a second one.
 */
const createdEventGroupId = ref<number | null>(null)

/** The files section, which keeps the files added before the event group existed until it is created. */
const filesSection = ref<InstanceType<typeof EventGroupFilesSection> | null>(null)

/** Creates the event group with everything that goes in the same request, and fills the form from it. */
async function createEventGroup(drafts: SetRow[]): Promise<number> {
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
  return created.eventGroup.id
}

/**
 * Saves the event group, then its settings, documents and share. A new event group is shared in the
 * same save, the way a new appointment is; one that is not shared sends nothing about sharing, since
 * there is nothing to take away. The template's files go last, followed by the files added before the
 * group existed, and the form moves to the saved group.
 */
async function save() {
  saving.value = true
  saveFailure.value = null
  try {
    const drafts = sets.value.map(set => ({id: set.id, name: set.name, minimum: set.minimum, maximum: set.maximum}))
    const storedId = eventGroupId.value ?? createdEventGroupId.value
    const savedId = storedId ?? await createEventGroup(drafts)
    if (storedId != null) {
      fill(await eventGroups.updateEventGroup(storedId, detailsToSave(), drafts, {
        registration: registration.value,
        view: view.value,
      }, savedQuestions(), namedEntries(fields.value)))
    }
    await dateSettings.save(savedId)
    await requirements.save(requirementOwner(savedId))
    if (storedId != null || federationShare.state.federationShared) {
      await federationShare.save(eventGroupFederationPort(savedId))
    }
    if (eventGroupId.value != null) return
    const fromTemplateFiles = new Set(fromTemplate.files.value.map(file => file.fileId))
    reportGoneFiles(await fromTemplate.attachFiles(savedId))
    await filesSection.value?.attachPendingTo(savedId, fromTemplateFiles)
    showToast(t('eventGroups.saved'), 'success')
    await router.replace({name: 'event-group-edit', params: {eventGroupId: savedId}})
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

        <NeutralContainer sheet data-testid="event-group-form">
          <EventGroupTemplateStart v-if="eventGroupId == null" v-bind="fromTemplate.props.value" compact @apply="applyTemplate"/>

          <EventGroupAboutSection
              v-model:details="details" v-model:fields="fields" :event-group-id="eventGroupId" :settings="dateSettings"
              :members="members" :groups="groups" :group-members="groupMembers" :tags="tags"
          />

          <EventGroupSignUpFields v-model="details" :parts="detail?.parts ?? []" :time-zone="stationTimezone"/>

          <div class="space-y-3">
            <HintedSubHeader :hint="t('eventGroups.setsHint')">{{ t('eventGroups.sets') }}</HintedSubHeader>
            <EventGroupSetsEditor v-model="sets" :part-counts="partCounts" :stored-minimums="storedMinimums"/>
          </div>

          <FormSection
              :title="t('eventGroups.audiences')" :summary="audienceSummary(registration, view, t)" :attention="!!saveFailure" data-testid="form-section-audience"
          >
            <MutedText tag="p" size="sm">{{ t('eventGroups.audienceHint') }}</MutedText>
            <AudienceRestrictions
                v-model:registration="registration" v-model:visibility="view" :groups="groups" :tags="tags"
                :registration-hint="t('eventGroups.registerAudienceHint')" :visibility-hint="t('eventGroups.viewAudienceHint')"
            />
          </FormSection>

          <EventGroupQuestionsCard v-model="questions" compact/>
          <EventGroupFilesSection ref="filesSection" :event-group-id="eventGroupId" :requirements="requirements" compact/>
          <EventGroupShareCard :federation-share="federationShare" :can-federate="canFederate" :attention="!!saveFailure" compact/>

          <div class="space-y-3">
            <FailureAlert v-if="saveFailure" :failure="saveFailure"/>
            <ButtonRow pair align="end">
              <SecondaryButton :to="cancelTarget">{{ t('common.cancel') }}</SecondaryButton>
              <SaveButton :action="save" :disabled="saving" data-testid="event-group-save"/>
            </ButtonRow>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('eventGroups.parts') }}</SectionHeader>
          <MutedText v-if="!detail" tag="p" size="sm">{{ t('eventGroups.saveFirst') }}</MutedText>
          <EventGroupPartsEditor v-else :detail="detail" :on-changed="refreshParts"/>
        </NeutralContainer>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
