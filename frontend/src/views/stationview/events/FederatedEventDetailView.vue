/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import CommentThread from '@/components/comment/CommentThread.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {Comment} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'
import {comments as commentsApi, events, stationMembers} from '@/api'
import type {FederatedEventDetail, FederatedRegistration} from '@/api/events'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {sessionInfo} = useSession()

const stationUid = ref(route.params.stationUid as string)
const eventId = ref(Number(route.params.eventId))

const detail = ref<FederatedEventDetail | null>(null)
const loading = ref(true)
const error = ref('')
const myRegistrations = ref<FederatedRegistration[]>([])
const registering = ref(false)
const selectedMemberUid = ref('')

const currentMemberUid = computed(() => sessionInfo.value?.member?.uid ?? '')
const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])

const eligibleMembers = computed(() => {
  const result: { uid: string; name: string }[] = []
  if (currentMemberUid.value) result.push({ uid: currentMemberUid.value, name: t('eventsUpcoming.myself') })
  for (const m of managedMembers.value) {
    if (m.uid) result.push({ uid: m.uid, name: m.name ?? m.email ?? `#${m.id}` })
  }
  return result
})

function getRegistration(uid: string): FederatedRegistration | undefined {
  return myRegistrations.value.find(r => r.eventId === eventId.value && r.remoteMemberId === uid)
}

function isRegistered(uid: string): boolean {
  return !!getRegistration(uid)
}

const membersWithoutReg = computed(() => eligibleMembers.value.filter(m => !isRegistered(m.uid)))

const selectedUid = computed((): string | null => {
  if (membersWithoutReg.value.length === 1) return membersWithoutReg.value[0].uid
  return selectedMemberUid.value || null
})

function getEventDate(): string {
  if (eventData.value?.startTime) return new Date(eventData.value.startTime as string).toISOString().split('T')[0]
  return new Date().toISOString().split('T')[0]
}

async function registerForEvent() {
  if (!selectedUid.value) return
  registering.value = true
  try {
    await events.registerForFederatedEvent(stationUid.value, eventId.value, getEventDate(), selectedUid.value)
    myRegistrations.value.push({
      eventId: eventId.value, remoteMemberId: selectedUid.value,
      eventDate: getEventDate(), status: 'PENDING', partnerId: 0,
    })
  } catch { error.value = t('common.error') }
  registering.value = false
}

async function withdrawRegistration(uid: string) {
  registering.value = true
  try {
    await events.withdrawFederatedRegistration(stationUid.value, eventId.value, getEventDate(), uid)
    myRegistrations.value = myRegistrations.value.filter(r => !(r.eventId === eventId.value && r.remoteMemberId === uid))
  } catch { error.value = t('common.error') }
  registering.value = false
}

// Comments
const commentsList = ref<Comment[]>([])
const members = ref<MemberCompletion[]>([])
const commentsLoading = ref(false)

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

const eventData = computed(() => detail.value?.event ?? null)
const publicFields = computed(() => detail.value?.publicFields ?? [])

const pad2 = (n: number) => String(n).padStart(2, '0')

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

function eventTypeBadge(type?: string): string {
  if (type === 'RECURRING') return t('federatedEventDetail.recurring')
  if (type === 'ONE_TIME') return t('federatedEventDetail.oneTime')
  return ''
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [eventDetail, regs] = await Promise.all([
      events.getFederatedEvent(stationUid.value, eventId.value),
      events.listMyFederatedRegistrations().catch(() => []),
    ])
    detail.value = eventDetail
    myRegistrations.value = regs
    await loadComments()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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

onMounted(loadData)
watch(() => [route.params.stationUid, route.params.eventId], () => {
  stationUid.value = route.params.stationUid as string
  eventId.value = Number(route.params.eventId)
  loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
      <SecondaryButton :icon="['fas', 'arrow-left']" compact @click="router.push({ name: 'events-upcoming' })">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="eventData && !loading">
        <NeutralContainer class="space-y-3">
          <!-- Header -->
          <div>
            <SubHeader>{{ eventData.name }}</SubHeader>
            <div class="flex items-center gap-2 mt-1 flex-wrap">
              <StationBadge :station-name="t('federatedEventDetail.partnerEvent')"/>
              <SecondaryBadge v-if="eventTypeBadge(eventData.eventType as string)">
                {{ eventTypeBadge(eventData.eventType as string) }}
              </SecondaryBadge>
              <InfoBadge v-if="eventData.requiresRegistration">
                {{ t('eventsUpcoming.registrationRequired') }}
              </InfoBadge>
            </div>
          </div>

          <!-- Time info -->
          <div v-if="eventData.startTime || eventData.dayOfWeek" class="flex items-center gap-3 text-sm">
            <span v-if="eventData.dayOfWeek && Number(eventData.dayOfWeek) > 0">
              <font-awesome-icon :icon="['fas', 'calendar-days']" class="mr-1 text-(--text-muted)"/>
              {{ dayNames[Number(eventData.dayOfWeek)] }}
            </span>
            <span v-if="eventData.startTime">
              <font-awesome-icon :icon="['fas', 'clock']" class="mr-1 text-(--text-muted)"/>
              {{ formatTime(eventData.startTime as string) }}
              <template v-if="eventData.endTime"> &ndash; {{ formatTime(eventData.endTime as string) }}</template>
            </span>
          </div>

          <!-- Description -->
          <p v-if="eventData.description" class="text-sm text-(--text-muted)">{{ eventData.description }}</p>

          <!-- Public fields -->
          <div v-if="publicFields.length > 0" class="space-y-1">
            <MutedText size="sm" class="font-medium">{{ t('federatedEventDetail.fields') }}</MutedText>
            <div class="flex flex-wrap gap-3 text-sm">
              <span v-for="field in publicFields" :key="field.id" class="text-(--text-muted)">
                <span class="font-medium">{{ field.name }}:</span> {{ field.value || '—' }}
              </span>
            </div>
          </div>
        </NeutralContainer>

        <!-- Registration -->
        <NeutralContainer v-if="eventData.requiresRegistration" class="space-y-3">
          <SubHeader>{{ t('eventsUpcoming.registration') }}</SubHeader>
          <div class="flex items-center gap-2 flex-wrap">
            <!-- Existing registrations -->
            <template v-for="m in eligibleMembers" :key="`reg-${m.uid}`">
              <div v-if="getRegistration(m.uid)" class="flex items-center gap-1">
                <span v-if="eligibleMembers.length > 1" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
                <SuccessBadge v-if="getRegistration(m.uid)!.status === 'ACCEPTED'">{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
                <InfoBadge v-else-if="getRegistration(m.uid)!.status === 'PENDING'">{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
                <ErrorBadge v-else-if="getRegistration(m.uid)!.status === 'DENIED'">{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
                <ErrorButton :disabled="registering" compact class="text-xs" @click="withdrawRegistration(m.uid)">
                  <font-awesome-icon :icon="['fas', 'xmark']" />
                </ErrorButton>
              </div>
            </template>

            <!-- Register new -->
            <template v-if="membersWithoutReg.length > 0">
              <SelectInput v-if="membersWithoutReg.length > 1" v-model="selectedMemberUid" class="text-sm w-40">
                <option disabled value="">{{ t('eventsUpcoming.selectMember') }}</option>
                <option v-for="m in membersWithoutReg" :key="m.uid" :value="m.uid">{{ m.name }}</option>
              </SelectInput>
              <PrimaryButton :disabled="registering || !selectedUid" @click="registerForEvent">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                {{ t('eventsUpcoming.register') }}
              </PrimaryButton>
            </template>
          </div>
        </NeutralContainer>

        <!-- Comments -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('comments.title') }}</SubHeader>
          <Spinner v-if="commentsLoading" size="sm"/>
          <template v-if="!commentsLoading">
            <CommentThread
                :comments="commentsList"
                :members="members"
                @create="createComment"
                @update="updateComment"
                @delete="deleteComment"
            />
          </template>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
