/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import EventGroupShortfall from './EventGroupShortfall.vue'
import type {EventGroupDetail, EventGroupPartnerSignUp} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import {eventGroups} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import type {Failure} from '@/util/failure'
import {fullPeoplePlaces, keepsWaitingList} from '@/util/eventGroupChoice'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FullDateDialog from '../eventshared/FullDateDialog.vue'
import {useQueueActions} from '../eventshared/useQueueActions'
import {eventGroupQueueRoutes} from '../eventshared/queueRoutes'

/**
 * The sign-ups partner stations' members sent for a shared event group, decided the same way as the
 * station's own. Nothing is shown while no partner member signed up.
 *
 * <p>A partner's member on the waiting list says their place in line, which counts over the one list
 * the station's own wait in too, and is moved up by hand from here. One waiting for a decision can be
 * put on the list. Accepting where the people or a chosen part is full asks first.
 */
const props = defineProps<{
  detail: EventGroupDetail
  /** Called after a decision, so the page reloads its counts. */
  onChanged?: () => void | Promise<void>
}>()

const {t} = useI18n()

const signUps = ref<EventGroupPartnerSignUp[]>([])
const {failure: loadFailure, reload} = useAsyncLoader(async () => {
  signUps.value = await eventGroups.listPartnerSignUps(props.detail.eventGroup.id)
}, {autoLoad: false})

/** Whether the event group keeps a waiting list, which needs a limit on people or on parts. */
const keepsQueue = computed(() => keepsWaitingList(props.detail.eventGroup.details, props.detail.parts))

/**
 * Deciding the partners' sign-ups and keeping them on the waiting list. While one action is on its way
 * every button waits, so a second click is not dropped without a word.
 */
const decideFailure = ref<Failure | null>(null)
const actions = useQueueActions(
    () => fullPeoplePlaces(props.detail.eventGroup),
    async () => { await props.onChanged?.() },
    decideFailure,
    eventGroupQueueRoutes(props.detail.eventGroup.id))
const deciding = actions.busy
const failure = computed(() => decideFailure.value ?? loadFailure.value)

watch(() => props.detail.eventGroup.id, reload, {immediate: true})

defineExpose({reload})
</script>

<template>
  <NeutralContainer v-if="signUps.length > 0 || failure" class="space-y-3" data-testid="event-group-partner-sign-ups">
    <SectionHeader>{{ t('eventGroups.partnerSignUps') }}</SectionHeader>
    <FailureAlert v-if="failure" :failure="failure"/>
    <ul class="divide-y divide-(--border)">
      <li v-for="entry in signUps" :key="entry.id" class="flex flex-wrap items-center justify-between gap-2 py-2"
          data-testid="event-group-partner-sign-up-row">
        <div class="flex flex-wrap items-center gap-2 min-w-0">
          <MemberName :identity="entry.member ?? null"/>
          <RegistrationStatusBadge v-if="entry.status" :status="entry.status" :queue-position="entry.queuePosition"/>
          <MutedText tag="span" size="sm">{{ t('eventGroups.chosen', {count: entry.parts.length}) }}</MutedText>
          <EventGroupShortfall :sets="entry.sets"/>
        </div>
        <ButtonRow :pair="!keepsQueue" align="end">
          <SuccessButton v-if="entry.status !== RegistrationStatus.ACCEPTED" compact :disabled="deciding"
                         data-testid="event-group-partner-accept" @click="actions.acceptPartner(entry.id)">
            {{ entry.status === RegistrationStatus.QUEUED ? t('registrationQueue.moveUp') : t('eventGroups.accept') }}
          </SuccessButton>
          <SecondaryButton v-if="keepsQueue && entry.status === RegistrationStatus.PENDING" compact :disabled="deciding"
                           :data-testid="`event-group-partner-queue-${entry.id}`" @click="actions.queuePartners([entry.id])">
            {{ t('registrationQueue.putOnList') }}
          </SecondaryButton>
          <ErrorButton v-if="entry.status !== RegistrationStatus.DENIED" compact :disabled="deciding"
                       data-testid="event-group-partner-deny" @click="actions.deny(entry.id, true)">
            {{ t('eventGroups.deny') }}
          </ErrorButton>
        </ButtonRow>
      </li>
    </ul>
    <FullDateDialog :question="actions.fullDateQuestion.value" @answer="actions.answerFullDate"/>
  </NeutralContainer>
</template>
