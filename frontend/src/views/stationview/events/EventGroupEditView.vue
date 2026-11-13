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
import type {SetRow} from './eventgroupeditview/eventGroupSetRow'
import EventGroupPartsEditor from './eventgroupeditview/EventGroupPartsEditor.vue'
import EventGroupShareCard from './eventgroupeditview/EventGroupShareCard.vue'
import {useEventFederationShare} from './eventeditview/useEventFederationShare'
import {eventGroupFederationPort} from './eventeditview/eventOwnerPorts'
import EventGroupQuestionsCard from './eventgroupeditview/EventGroupQuestionsCard.vue'
import {asSaved} from './eventshared/eventQuestions'
import type {EventGroupDetail, MemberGroup, RegistrationFieldDefinition, RestrictionAudience, UserTag} from '@/api/generated/schema'
import {StationPermission} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {eventGroups, memberGroups as memberGroupsApi, userTags as userTagsApi} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {instantToLocalInput, localInputToInstant} from '@/util/format'
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

const name = ref('')
const description = ref('')
const limit = ref<number | undefined>(undefined)
const deadline = ref('')
const confirm = ref(false)
const sets = ref<SetRow[]>([{id: null, name: t('eventGroups.defaultSet'), minimum: 1}])
const registration = ref<RestrictionSelection>(emptySelection())
const view = ref<RestrictionSelection>(emptySelection())
const questions = ref<RegistrationFieldDefinition[]>([])
const saving = ref(false)
const saveFailure = ref<Failure | null>(null)

function emptySelection(): RestrictionSelection {
  return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}
}

function selectionOf(audience: RestrictionAudience): RestrictionSelection {
  return {
    userTypes: [...audience.userTypes],
    groupIds: [...audience.groupIds],
    tagIds: [...audience.tagIds],
    memberIds: [...audience.memberIds],
    mode: audience.mode,
  }
}

/** Fills the form from the stored event group. */
function fill(loadedDetail: EventGroupDetail) {
  detail.value = loadedDetail
  name.value = loadedDetail.eventGroup.name
  description.value = loadedDetail.eventGroup.description ?? ''
  limit.value = loadedDetail.eventGroup.registrationLimit ?? undefined
  deadline.value = instantToLocalInput(loadedDetail.eventGroup.registrationDeadline)
  confirm.value = loadedDetail.eventGroup.requiresConfirmation
  sets.value = loadedDetail.sets.map(set => ({id: set.id, name: set.name, minimum: set.minimum}))
  registration.value = selectionOf(loadedDetail.registration)
  view.value = selectionOf(loadedDetail.view)
  questions.value = loadedDetail.questions.map(({name, fieldType, config, overview}) => ({name, fieldType, config, overview}))
}

function savedQuestions(): RegistrationFieldDefinition[] {
  return questions.value.filter(question => question.name?.trim()).map(asSaved)
}

const {loading, failure} = useAsyncLoader(async () => {
  await until(loaded).toBe(true)
  const [memberGroups, userTags] = await Promise.all([
    memberGroupsApi.listGroups(),
    userTagsApi.listChoosableTags(),
  ])
  groups.value = memberGroups
  tags.value = userTags
  if (eventGroupId.value != null) {
    fill(await eventGroups.getEventGroup(eventGroupId.value))
    await federationShare.load(eventGroupFederationPort(eventGroupId.value))
  }
})

/** Reads the event group again after a part changed, touching nothing the form holds unsaved. */
async function refreshParts() {
  if (eventGroupId.value == null) return
  detail.value = await eventGroups.getEventGroup(eventGroupId.value)
}

function details() {
  return {
    name: name.value,
    description: description.value.trim() === '' ? null : description.value,
    registrationLimit: limit.value ?? null,
    registrationDeadline: deadline.value ? localInputToInstant(deadline.value) : null,
    requiresConfirmation: confirm.value,
  }
}

async function save() {
  saving.value = true
  saveFailure.value = null
  try {
    const drafts = sets.value.map(set => ({id: set.id, name: set.name, minimum: set.minimum}))
    if (eventGroupId.value == null) {
      const created = await eventGroups.createEventGroup({
        details: details(),
        sets: drafts,
        registration: registration.value,
        view: view.value,
        questions: savedQuestions(),
      })
      showToast(t('eventGroups.saved'), 'success')
      await router.replace({name: 'event-group-edit', params: {eventGroupId: created.eventGroup.id}})
      fill(created)
      return
    }
    fill(await eventGroups.updateEventGroup(eventGroupId.value, details(), drafts, {
      registration: registration.value,
      view: view.value,
    }, savedQuestions()))
    await federationShare.save(eventGroupFederationPort(eventGroupId.value))
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
        <EventGroupDetailsForm
            v-model:name="name"
            v-model:description="description"
            v-model:limit="limit"
            v-model:deadline="deadline"
            v-model:confirm="confirm"
        />

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('eventGroups.sets') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('eventGroups.setsHint') }}</MutedText>
          <EventGroupSetsEditor v-model="sets"/>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('eventGroups.audiences') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('eventGroups.audienceHint') }}</MutedText>
          <FieldLabel>{{ t('eventGroups.registerAudience') }}</FieldLabel>
          <RestrictionsField v-model="registration" :groups="groups" :tags="tags"/>
          <FieldLabel>{{ t('eventGroups.viewAudience') }}</FieldLabel>
          <RestrictionsField v-model="view" :groups="groups" :tags="tags"/>
        </NeutralContainer>

        <EventGroupQuestionsCard v-model="questions"/>

        <EventGroupShareCard v-if="detail" :federation-share="federationShare" :can-federate="canFederate"/>

        <FailureAlert v-if="saveFailure" :failure="saveFailure"/>
        <ButtonRow pair align="end">
          <SecondaryButton
              :to="eventGroupId == null ? {name: 'event-groups'} : {name: 'event-group', params: {eventGroupId}}"
          >
            {{ t('common.cancel') }}
          </SecondaryButton>
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
