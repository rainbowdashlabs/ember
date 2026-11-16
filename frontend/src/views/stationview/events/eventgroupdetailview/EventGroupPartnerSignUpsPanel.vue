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
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * The sign-ups partner stations' members sent for a shared event group, decided the same way as the
 * station's own. Nothing is shown while no partner member signed up.
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

/**
 * Accepts or declines one partner sign-up. While one decision is on its way every decision button
 * waits, so a second click is not dropped without a word.
 */
const {running: deciding, failure: decideFailure, run: decide} = useAsyncAction(
    async (entry: EventGroupPartnerSignUp, status: RegistrationStatus) => {
      await eventGroups.decidePartner(props.detail.eventGroup.id, entry.id, status)
      await reload()
      await props.onChanged?.()
    })
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
          <RegistrationStatusBadge v-if="entry.status" :status="entry.status"/>
          <MutedText tag="span" size="sm">{{ t('eventGroups.chosen', {count: entry.parts.length}) }}</MutedText>
          <EventGroupShortfall :sets="entry.sets"/>
        </div>
        <ButtonRow pair align="end">
          <SuccessButton v-if="entry.status !== RegistrationStatus.ACCEPTED" compact :disabled="deciding"
                         data-testid="event-group-partner-accept" @click="decide(entry, RegistrationStatus.ACCEPTED)">
            {{ t('eventGroups.accept') }}
          </SuccessButton>
          <ErrorButton v-if="entry.status !== RegistrationStatus.DENIED" compact :disabled="deciding"
                       data-testid="event-group-partner-deny" @click="decide(entry, RegistrationStatus.DENIED)">
            {{ t('eventGroups.deny') }}
          </ErrorButton>
        </ButtonRow>
      </li>
    </ul>
  </NeutralContainer>
</template>
