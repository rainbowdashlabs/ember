/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FederatedEventRegistrationRow from '@/views/stationview/events/upcomingview/federatedeventssection/FederatedEventRegistrationRow.vue'
import type {FederatedEvent, FederatedRegistration} from '@/api/events'
import {formatTime} from '@/util/format'
import {chosenIfStillOffered} from '@/util/eventAnswers'

const props = defineProps<{
  fed: FederatedEvent
  eligibleMembers: { uid: string; name: string }[]
  registrations: FederatedRegistration[]
  registering: string | null
}>()

const emit = defineEmits<{
  register: [fed: FederatedEvent, memberUid: string]
  withdraw: [fed: FederatedEvent, memberUid: string]
}>()

const {t} = useI18n()
const router = useRouter()

const eventKey = computed(() => `${props.fed.partnerStationUid}-${props.fed.event.id}`)

function getRegistration(memberUid: string): FederatedRegistration | undefined {
  return props.registrations.find(r => r.eventId === props.fed.event.id && r.remoteMemberId === memberUid)
}

const membersWithoutRegistration = computed(() =>
    props.eligibleMembers.filter(m => !getRegistration(m.uid)))

const selectedMemberUid = ref('')

const selectedUid = computed(() => chosenIfStillOffered(
    selectedMemberUid.value || null,
    membersWithoutRegistration.value.map(member => member.uid)))

function openDetail() {
  router.push({
    name: 'federated-event-detail',
    params: {stationUid: props.fed.partnerStationUid, eventId: props.fed.event.id},
  })
}

function handleRegister() {
  if (!selectedUid.value) return
  const uid = selectedUid.value
  selectedMemberUid.value = ''
  emit('register', props.fed, uid)
}
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="federated-event">
    <div class="cursor-pointer" @click="openDetail">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <div>
          <span class="font-medium">{{ fed.event.name }}</span>
          <SecondaryBadge class="ml-2">{{ fed.partnerStationName }}</SecondaryBadge>
          <MutedText v-if="fed.event.startTime" size="sm" class="ml-2">
            {{ formatTime(fed.event.startTime) }} – {{ formatTime(fed.event.endTime) }}
          </MutedText>
        </div>
        <InfoBadge v-if="fed.event.requiresRegistration">{{ t('eventsUpcoming.registrationRequired') }}</InfoBadge>
      </div>
      <p v-if="fed.event.description" class="text-sm text-(--text-muted)">{{ fed.event.description }}</p>
    </div>
    <FederatedEventRegistrationRow
        v-if="fed.event.requiresRegistration"
        v-model:selected-member-uid="selectedMemberUid"
        :event-key="eventKey"
        :eligible-members="eligibleMembers"
        :members-without-registration="membersWithoutRegistration"
        :get-registration="getRegistration"
        :selected-uid="selectedUid"
        :registering="registering != null"
        @register="handleRegister"
        @withdraw="(uid) => emit('withdraw', fed, uid)"
    />
  </NeutralContainer>
</template>
