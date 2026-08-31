/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts" generic="K extends string | number, U">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AnsweredAnswers from './eventregistrationactions/AnsweredAnswers.vue'
import SignOffConfirm from './eventregistrationactions/SignOffConfirm.vue'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {isRefusal, type AnswerablePerson, type GivenAnswer} from '@/util/eventAnswers'

/**
 * Answering an appointment, for oneself and for whoever one answers for.
 *
 * <p>An appointment takes one answer, and which answer it is depends on what it expects. One that has
 * to be signed up for expects nobody unless they say so, so the only thing to say is that you are
 * coming. One that expects everybody is the other way round: the only thing to say is that you are
 * not coming.
 *
 * <p>Undoing is a deletion in both directions, never the opposite answer written down. Somebody who
 * takes back their place has not refused the appointment, and somebody who takes back their refusal
 * has not signed up for one that never asked them to.
 *
 * <p>People are identified by whatever the screen holds rather than by a member id, so the same
 * controls serve the station's own appointments and those a partner station shares. A second copy for
 * the partner's ones drifted away from this one the moment either was touched.
 */
const props = defineProps<{
  /** Everyone the appointment can be answered for here, whether they have answered or not. */
  people: AnswerablePerson<K>[]
  /** What each of them has said so far. */
  answers: GivenAnswer<K, U>[]
  requiresRegistration: boolean
  /** When answers stop being taken, or nothing where they are taken until the appointment itself. */
  registrationDeadline?: string | null
  hasManagedMembers: boolean
  registering: boolean
}>()

const emit = defineEmits<{
  register: [people: AnswerablePerson<K>[]]
  decline: [people: AnswerablePerson<K>[]]
  withdraw: [reference: U]
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

/**
 * Why there is nothing to press, where the appointment is open to nobody in this household.
 *
 * <p>An appointment narrowed for registration is still shown to everybody, so without a word here the
 * reader would meet a date with no answer on it and no reason given. Silence would read as a fault.
 */
const notOpenLabel = computed(() => {
  if (props.people.length > 0) return ''
  return props.hasManagedMembers
      ? t('events.notOpenToHousehold')
      : t('events.notOpenToYou')
})

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
 * Taking an answer back. Only giving up a place asks first: undoing a refusal costs nobody anything.
 */
function undoAnswer(answered: GivenAnswer<K, U>) {
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
        :registering="registering" @undo="onUndo"/>

    <MutedText v-if="notOpenLabel" class="text-sm">{{ notOpenLabel }}</MutedText>

    <div v-if="peopleWithoutAnswer.length > 0 && canAnswer" class="flex flex-wrap items-center gap-2">
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
      <ErrorButton
          v-else
          :disabled="registering"
          data-onboarding="events.item.attendance"
          data-testid="answer-selected"
          class="text-sm"
          @click.stop="answer"
      >
        <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
        {{ buttonLabel }}
      </ErrorButton>
    </div>

    <SignOffConfirm v-model="showSignOffConfirm" :busy="registering" @confirm="confirmSignOff"/>
  </div>
</template>
