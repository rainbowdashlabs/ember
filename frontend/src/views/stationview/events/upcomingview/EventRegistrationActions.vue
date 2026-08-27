/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {RegistrationStatus, type EventRegistrationEntry} from '@/api/events'

/**
 * Answering an appointment, for oneself and for whoever one answers for.
 *
 * <p>An appointment takes one answer, and which answer it is depends on what it expects. One that has
 * to be signed up for expects nobody unless they say so, so the only thing to say is that you are
 * coming, and the only thing to undo is that. One that expects everybody is the other way round: the
 * only thing to say is that you are not coming, and the only thing to undo is that.
 *
 * <p>Undoing is a deletion in both directions, never the opposite answer written down. Somebody who
 * takes back their place has not refused the appointment, and somebody who takes back their refusal
 * has not signed up for one that never asked them to.
 */
const props = defineProps<{
  eligibleMembers: { id: number; name: string }[]
  registrations: EventRegistrationEntry[]
  requiresRegistration: boolean
  /** When answers stop being taken, or nothing where they are taken until the appointment itself. */
  registrationDeadline?: string | null
  hasManagedMembers: boolean
  registering: boolean
}>()

const emit = defineEmits<{
  register: [memberId: number]
  decline: [memberId: number]
  withdraw: [registrationId: number]
}>()

const {t} = useI18n()
const selectedMemberId = ref('')

function getRegistration(memberId: number): EventRegistrationEntry | undefined {
  return props.registrations.find(r => r.memberId === memberId)
}

const membersWithoutAnswer = computed(() =>
    props.eligibleMembers.filter(m => !getRegistration(m.id)))

const selectedId = computed((): number | null => {
  const single = membersWithoutAnswer.value.length === 1 ? membersWithoutAnswer.value[0] : undefined
  if (single) return single.id
  return selectedMemberId.value ? Number(selectedMemberId.value) : null
})

/**
 * Whether an answer is still being taken.
 *
 * <p>The server refuses one once the deadline has passed, and the button offering to give one stayed
 * where it was, so the only thing pressing it produced was an error.
 */
const stillOpen = computed(() => {
  if (!props.registrationDeadline) return true
  return new Date(props.registrationDeadline).getTime() > Date.now()
})

/** The one thing somebody who has not answered yet can say, where it can still be said. */
const canAnswer = computed(() => !props.requiresRegistration || stillOpen.value)

/** What the button says for somebody who has not answered: coming, or not coming. */
const answerLabel = computed(() =>
    props.requiresRegistration ? t('eventsUpcoming.register') : t('eventsUpcoming.decline'))

function answerFor(memberId: number) {
  if (props.requiresRegistration) emit('register', memberId)
  else emit('decline', memberId)
}

function answerForSelected() {
  if (selectedId.value != null) answerFor(selectedId.value)
}

function namedFor(label: string, forLabel: string): string {
  if (selectedId.value == null || membersWithoutAnswer.value.length <= 1) return label
  const name = membersWithoutAnswer.value.find(m => m.id === selectedId.value)?.name ?? ''
  return t(forLabel, {name})
}
</script>

<template>
  <div data-onboarding="events.item.pending" class="flex items-center gap-2 flex-wrap">
    <template v-for="m in eligibleMembers" :key="`reg-${m.id}`">
      <div v-if="getRegistration(m.id)" class="flex items-center gap-1">
        <span v-if="hasManagedMembers" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
        <SuccessBadge v-if="getRegistration(m.id)!.status === RegistrationStatus.ACCEPTED">
          {{ t('eventsUpcoming.statusAccepted') }}
        </SuccessBadge>
        <InfoBadge v-else-if="getRegistration(m.id)!.status === RegistrationStatus.PENDING">
          {{ t('eventsUpcoming.statusPending') }}
        </InfoBadge>
        <ErrorBadge v-else-if="getRegistration(m.id)!.status === RegistrationStatus.DENIED">
          {{ t('eventsUpcoming.statusDenied') }}
        </ErrorBadge>
        <ErrorBadge v-else>{{ t('eventsUpcoming.statusDeclined') }}</ErrorBadge>

        <SecondaryButton
            :disabled="registering"
            :data-testid="`undo-answer-${m.id}`"
            class="text-sm"
            @click="emit('withdraw', getRegistration(m.id)!.id)"
        >
          <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1"/>
          {{ requiresRegistration ? t('eventsUpcoming.unregister') : t('eventsUpcoming.register') }}
        </SecondaryButton>

        <span v-if="getRegistration(m.id)?.createdByName" class="text-xs text-(--text-muted) italic">
          {{ t('common.createdBy', {name: getRegistration(m.id)!.createdByName}) }}
        </span>
      </div>
    </template>

    <template v-if="membersWithoutAnswer.length > 0 && canAnswer">
      <SelectInput v-if="membersWithoutAnswer.length > 1" v-model="selectedMemberId"
                   data-onboarding="events.item.member-select" class="text-sm w-40">
        <option disabled value="">{{ t('eventsUpcoming.selectMember') }}</option>
        <option v-for="m in membersWithoutAnswer" :key="m.id" :value="String(m.id)">{{ m.name }}</option>
      </SelectInput>

      <PrimaryButton
          v-if="requiresRegistration"
          :disabled="registering || selectedId == null"
          data-testid="answer-selected"
          class="text-sm"
          @click="answerForSelected"
      >
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ namedFor(answerLabel, 'eventsUpcoming.registerFor') }}
      </PrimaryButton>
      <ErrorButton
          v-else
          :disabled="registering || selectedId == null"
          data-testid="answer-selected"
          class="text-sm"
          @click="answerForSelected"
      >
        <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
        {{ namedFor(answerLabel, 'eventsUpcoming.declineFor') }}
      </ErrorButton>
    </template>
  </div>
</template>
