/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import type {MemberOption} from '@/components/input/select/memberOption'
import {RegistrationStatus, type RemoteMemberRegistration} from '@/api/generated/schema'
import RegistrationStatusBadge from '@/views/stationview/events/eventshared/RegistrationStatusBadge.vue'
import {useConfirmAction} from '@/composables/useConfirmAction'
import SignOffConfirm from '@/views/stationview/events/eventshared/eventregistrationactions/SignOffConfirm.vue'

interface EligibleMember {
  uid: string
  name: string
}

const selectedMemberUid = defineModel<string>('selectedMemberUid', {required: true})

const props = defineProps<{
  eligibleMembers: EligibleMember[]
  registrations: RemoteMemberRegistration[]
  eventId: number
  registering: boolean
  /**
   * Whether this reader may choose who comes: the other station handed the choosing over, and
   * keeping this station's registrations is the reader's job. Both, or the button would be offered
   * to somebody the other station will refuse.
   */
  weDecide: boolean
  /** What the other station set aside, in words, or empty where it set nothing aside. */
  placesSummary: string
  /** Every place of the date is taken, as the other station counted them. */
  full?: boolean
  /** A sign-up lands on the waiting list, which the other station does by itself on a full date. */
  joinsQueue?: boolean
}>()

const emit = defineEmits<{
  register: []
  withdraw: [uid: string]
  confirm: [uid: string]
}>()

const {t} = useI18n()

/**
 * Signing somebody off a partner station's appointment, which asks first exactly as the station's own
 * appointments do. A place at somebody else's event is no easier to get back, and the button sits in
 * the same place on the page. Holding shift carries it out at once, as everywhere else.
 */
const {
  show: showSignOffConfirm,
  request: requestSignOff,
  confirm: confirmSignOff,
} = useConfirmAction<string>({
  onConfirm: async uid => emit('withdraw', uid),
})

function getRegistration(uid: string): RemoteMemberRegistration | undefined {
  return props.registrations.find(r => r.eventId === props.eventId && r.remoteMemberId === uid)
}

const membersWithoutReg = computed(() =>
    props.eligibleMembers.filter(m => !getRegistration(m.uid)),
)

const pickable = computed<MemberOption[]>(() =>
    membersWithoutReg.value.map(m => ({value: m.uid, name: m.name})))

const canSubmit = computed(() => {
  if (membersWithoutReg.value.length === 1) return true
  return !!selectedMemberUid.value
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center gap-2">
      <SubHeader>{{ t('eventsUpcoming.registration') }}</SubHeader>
      <ErrorBadge v-if="props.full" data-testid="partner-date-full">{{ t('registrationQueue.full') }}</ErrorBadge>
    </div>

    <MutedText v-if="props.placesSummary" tag="p" size="sm" data-testid="our-places">
      {{ props.placesSummary }}
    </MutedText>

    <div class="flex items-center gap-2 flex-wrap">
      <template v-for="m in props.eligibleMembers" :key="`reg-${m.uid}`">
        <div v-if="getRegistration(m.uid)" class="flex items-center gap-2 flex-wrap">
          <span v-if="props.eligibleMembers.length > 1" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
          <RegistrationStatusBadge :status="getRegistration(m.uid)!.status"/>
          <ButtonRow pair>
            <PrimaryButton
                v-if="props.weDecide && getRegistration(m.uid)!.status === RegistrationStatus.PENDING"
                :disabled="props.registering"
                compact
                class="text-xs"
                data-testid="confirm-own-member"
                @click="emit('confirm', m.uid)"
            >
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('common.confirm') }}
            </PrimaryButton>
            <ErrorButton
                :disabled="props.registering"
                compact
                class="text-xs"
                data-testid="sign-off-own-member"
                @click="requestSignOff(m.uid)"
            >
              <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
              {{ t('eventsUpcoming.unregister') }}
            </ErrorButton>
          </ButtonRow>
        </div>
      </template>

      <template v-if="membersWithoutReg.length > 0">
        <MemberSelectInput
            v-if="membersWithoutReg.length > 1"
            v-model="selectedMemberUid"
            class="w-56"
            :members="pickable"
            :placeholder="t('eventsUpcoming.selectMember')"
        />
        <PrimaryButton :disabled="props.registering || !canSubmit" data-testid="register-own-member" @click="emit('register')">
          <font-awesome-icon :icon="['fas', props.joinsQueue ? 'list-ol' : 'check']" class="mr-1"/>
          {{ props.joinsQueue ? t('registrationQueue.join') : t('eventsUpcoming.register') }}
        </PrimaryButton>
      </template>
    </div>

    <SignOffConfirm v-model="showSignOffConfirm" :busy="props.registering" @confirm="confirmSignOff"/>
  </NeutralContainer>
</template>
