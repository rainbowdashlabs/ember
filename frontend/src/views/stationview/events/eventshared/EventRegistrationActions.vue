/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts" generic="K extends string | number, U">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AnsweredAnswers from './eventregistrationactions/AnsweredAnswers.vue'
import SignOffConfirm from './eventregistrationactions/SignOffConfirm.vue'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {isRefusal, notOpenLabelKey, type AnswerablePerson, type GivenAnswer} from '@/util/eventAnswers'

/**
 * Answering an appointment, for oneself and for whoever one answers for.
 *
 * <p>What an appointment asks depends on what it expects. One that has to be signed up for expects
 * nobody unless they say so: the answer is coming, or not coming for whoever wants no more reminders
 * that registration is closing. One that expects everybody takes only the refusal.
 *
 * <p>Undoing is a deletion in both directions, never the opposite answer written down. Somebody who
 * takes back their place has not refused the appointment, and somebody who takes back their refusal
 * has not signed up for one that never asked them to.
 *
 * <p>People are identified by whatever the screen holds rather than by a member id, so the same
 * controls serve the station's own appointments and those a partner station shares. A second copy for
 * the partner's ones drifted away from this one the moment either was touched.
 */
const props = withDefaults(defineProps<{
  /** Everyone the appointment can be answered for here, whether they have answered or not. */
  people: AnswerablePerson<K>[]
  /** What each of them has said so far. */
  answers: GivenAnswer<K, U>[]
  requiresRegistration: boolean
  /** When answers stop being taken, or nothing where they are taken until the appointment itself. */
  registrationDeadline?: string | null
  hasManagedMembers: boolean
  registering: boolean
  /**
   * Whether an appointment that has to be signed up for can also be refused here. A partner station
   * takes sign-ups only, so its appointments are not.
   */
  declinable?: boolean
}>(), {declinable: true, registrationDeadline: null})

const emit = defineEmits<{
  register: [people: AnswerablePerson<K>[]]
  decline: [people: AnswerablePerson<K>[]]
  withdraw: [reference: U]
  update: [reference: U]
}>()

const {t} = useI18n()

const answeredKeys = computed(() => new Set(props.answers.map(answer => answer.key)))

const peopleWithoutAnswer = computed(() => props.people.filter(person => !answeredKeys.value.has(person.key)))

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

/** Why there is nothing to press, where the appointment is open to nobody in this household. */
const notOpenLabel = computed(() => props.people.length > 0 ? '' : t(notOpenLabelKey(props.hasManagedMembers)))

/** What the button says for somebody who has not answered: coming, or not coming. */
const answerLabel = computed(() =>
    props.requiresRegistration ? t('eventsUpcoming.register') : t('eventsUpcoming.decline'))

/**
 * What the one button says. Where it answers for a single named person out of a household, it says
 * whose answer it is, so a guardian pressing it knows which of their children it settles.
 */
const buttonLabel = computed(() => {
  const open = peopleWithoutAnswer.value
  if (!props.hasManagedMembers || open.length !== 1) return answerLabel.value
  const forLabel = props.requiresRegistration ? 'eventsUpcoming.registerFor' : 'eventsUpcoming.declineFor'
  return t(forLabel, {name: open[0]!.name})
})

/** What the refusal beside the sign-up says, naming the person where it answers for one of a household. */
const declineLabel = computed(() => {
  const open = peopleWithoutAnswer.value
  if (!props.hasManagedMembers || open.length !== 1) return t('eventsUpcoming.decline')
  return t('eventsUpcoming.declineFor', {name: open[0]!.name})
})

/**
 * Signing somebody off, which is the one answer worth asking about twice.
 *
 * <p>A place given up is gone: whoever wants it back joins the queue again, and where the list is
 * already full that is the end of it. Signing on needs no such question, and neither does taking a
 * refusal back. Holding shift carries it out at once, as everywhere else.
 */
const {
  show: showSignOffConfirm,
  request: requestSignOff,
  confirm: confirmSignOff,
} = useConfirmAction<() => void>({
  onConfirm: async signOff => signOff(),
})

/**
 * Giving the one answer this appointment takes, for everybody who still owes it.
 *
 * <p>Who exactly it is for is settled after this press, not before: where more than one person is
 * still open, or the appointment asks questions, a dialog takes both. Only a refusal for a single
 * person asks first, because a place given up is gone.
 */
function answer() {
  const open = peopleWithoutAnswer.value
  if (props.requiresRegistration) {
    emit('register', open)
    return
  }
  if (open.length === 1) {
    requestSignOff(() => emit('decline', open))
    return
  }
  emit('decline', open)
}

/**
 * Saying "not coming" to an appointment that has to be signed up for, for everybody still open.
 *
 * <p>Nobody here holds a place, so nothing is lost and nothing is asked first. It stops the reminders
 * that registration is closing, for whoever simply has no time on that date.
 */
function declineRegistration() {
  emit('decline', peopleWithoutAnswer.value)
}

/**
 * Taking an answer back. Only giving up a place asks first: undoing a refusal costs nobody anything. A
 * place already given up is not taken back but asked for anew.
 */
function undoAnswer(answered: GivenAnswer<K, U>) {
  if (answered.placeGivenUp) {
    const person = props.people.find(entry => entry.key === answered.key)
    if (person) emit('register', [person])
    return
  }
  if (isRefusal(answered.status)) {
    emit('withdraw', answered.undo)
    return
  }
  requestSignOff(() => emit('withdraw', answered.undo))
}

function onUndo(reference: U) {
  const answered = props.answers.find(entry => entry.undo === reference)
  if (answered) undoAnswer(answered)
}
</script>

<template>
  <div data-onboarding="events.item.pending" class="space-y-1">
    <AnsweredAnswers
        :rows="answers" :show-names="hasManagedMembers" :requires-registration="requiresRegistration"
        :sign-up-open="stillOpen" :registering="registering" @undo="onUndo" @update="reference => emit('update', reference)"/>

    <MutedText v-if="notOpenLabel" class="text-sm">{{ notOpenLabel }}</MutedText>

    <ButtonRow v-if="peopleWithoutAnswer.length > 0 && canAnswer" :pair="requiresRegistration && declinable">
      <PrimaryButton
          v-if="requiresRegistration"
          :disabled="registering"
          data-onboarding="events.item.registration"
          data-testid="answer-selected"
          class="text-sm"
          @click.stop="answer"
      >
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ buttonLabel }}
      </PrimaryButton>
      <SecondaryButton
          v-if="requiresRegistration && declinable"
          :disabled="registering"
          data-testid="decline-registration"
          class="text-sm"
          @click.stop="declineRegistration"
      >
        <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
        {{ declineLabel }}
      </SecondaryButton>
      <ErrorButton
          v-else-if="!requiresRegistration"
          :disabled="registering"
          data-onboarding="events.item.attendance"
          data-testid="answer-selected"
          class="text-sm"
          @click.stop="answer"
      >
        <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
        {{ buttonLabel }}
      </ErrorButton>
    </ButtonRow>

    <SignOffConfirm v-model="showSignOffConfirm" :busy="registering" @confirm="confirmSignOff"/>
  </div>
</template>
