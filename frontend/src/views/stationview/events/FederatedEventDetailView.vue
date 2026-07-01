/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {Comment} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'
import {comments as commentsApi, events, stationMembers} from '@/api'
import type {FederatedEventDetail, FederatedRegistration} from '@/api/events'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import HeaderCard from './federatedeventdetailview/HeaderCard.vue'
import RegistrationCard from './federatedeventdetailview/RegistrationCard.vue'
import CommentsCard from './federatedeventdetailview/CommentsCard.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {sessionInfo} = useSession()

const stationUid = ref(route.params.stationUid as string)
const eventId = ref(Number(route.params.eventId))

const detail = ref<FederatedEventDetail | null>(null)
const myRegistrations = ref<FederatedRegistration[]>([])
const registering = ref(false)
const selectedMemberUid = ref('')

const currentMemberUid = computed(() => sessionInfo.value?.member?.uid ?? '')
const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])

const eligibleMembers = computed(() => {
  const result: { uid: string; name: string }[] = []
  if (currentMemberUid.value) result.push({uid: currentMemberUid.value, name: t('eventsUpcoming.myself')})
  for (const m of managedMembers.value) {
    if (m.uid) result.push({uid: m.uid, name: m.name ?? m.email ?? `#${m.id}`})
  }
  return result
})

const eventData = computed(() => detail.value?.event ?? null)
const publicFields = computed(() => detail.value?.publicFields ?? [])

function getEventDate(): string {
  if (eventData.value?.startTime) return new Date(eventData.value.startTime as string).toISOString().split('T')[0]
  return new Date().toISOString().split('T')[0]
}

function selectedUidForRegister(): string | null {
  const without = eligibleMembers.value.filter(m => !myRegistrations.value.some(r => r.eventId === eventId.value && r.remoteMemberId === m.uid))
  if (without.length === 1) return without[0].uid
  return selectedMemberUid.value || null
}

async function registerForEvent() {
  const uid = selectedUidForRegister()
  if (!uid) return
  registering.value = true
  try {
    await events.registerForFederatedEvent(stationUid.value, eventId.value, getEventDate(), uid)
    myRegistrations.value.push({
      eventId: eventId.value, remoteMemberId: uid,
      eventDate: getEventDate(), status: 'PENDING', partnerId: 0,
    })
  } catch {
    error.value = t('common.error')
  }
  registering.value = false
}

async function withdrawRegistration(uid: string) {
  registering.value = true
  try {
    await events.withdrawFederatedRegistration(stationUid.value, eventId.value, getEventDate(), uid)
    myRegistrations.value = myRegistrations.value.filter(r => !(r.eventId === eventId.value && r.remoteMemberId === uid))
  } catch {
    error.value = t('common.error')
  }
  registering.value = false
}

const commentsList = ref<Comment[]>([])
const members = ref<MemberCompletion[]>([])
const commentsLoading = ref(false)

const {loading, error, reload} = useAsyncLoader(async () => {
  const [eventDetail, regs] = await Promise.all([
    events.getFederatedEvent(stationUid.value, eventId.value),
    events.listMyFederatedRegistrations().catch(() => []),
  ])
  detail.value = eventDetail
  myRegistrations.value = regs
  await loadComments()
})

async function loadComments() {
  commentsLoading.value = true
  try {
    const [c, m] = await Promise.all([
      commentsApi.listFederatedEventComments(stationUid.value, eventId.value),
      stationMembers.listCompletions(),
    ])
    commentsList.value = c
    members.value = m
  } catch {
    /* comments may fail silently */
  } finally {
    commentsLoading.value = false
  }
}

async function createComment(parentId: number | null, content: string) {
  try {
    await commentsApi.createFederatedEventComment(stationUid.value, eventId.value, {parentId, content})
    commentsList.value = await commentsApi.listFederatedEventComments(stationUid.value, eventId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function updateComment(commentId: number, content: string) {
  try {
    await commentsApi.updateFederatedEventComment(stationUid.value, commentId, {content})
    commentsList.value = await commentsApi.listFederatedEventComments(stationUid.value, eventId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function deleteComment(commentId: number) {
  try {
    await commentsApi.deleteFederatedEventComment(stationUid.value, commentId)
    commentsList.value = await commentsApi.listFederatedEventComments(stationUid.value, eventId.value)
  } catch {
    error.value = t('common.error')
  }
}

watch(() => [route.params.stationUid, route.params.eventId], () => {
  stationUid.value = route.params.stationUid as string
  eventId.value = Number(route.params.eventId)
  reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.federated-event-detail.title')"
      :subtitle="t('pages.federated-event-detail.subtitle')"
  >
    <div class="space-y-4">
      <SecondaryButton :icon="['fas', 'arrow-left']" compact @click="router.push({ name: 'events-upcoming' })">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="eventData && !loading">
        <HeaderCard :event="eventData" :public-fields="publicFields"/>

        <RegistrationCard
            v-if="eventData.requiresRegistration"
            v-model:selected-member-uid="selectedMemberUid"
            :eligible-members="eligibleMembers"
            :registrations="myRegistrations"
            :event-id="eventId"
            :registering="registering"
            @register="registerForEvent"
            @withdraw="withdrawRegistration"
        />

        <CommentsCard
            :comments="commentsList"
            :members="members"
            :loading="commentsLoading"
            @create="createComment"
            @update="updateComment"
            @delete="deleteComment"
        />
      </template>
    </div>
  </ViewContent>
</template>
