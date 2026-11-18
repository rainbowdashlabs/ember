/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import RegistrationFieldInputs from '../eventshared/RegistrationFieldInputs.vue'
import RegistrationSigningStep from '../eventshared/RegistrationSigningStep.vue'
import {eventGroupSigningStep} from './eventGroupSigning'
import type {SigningStep} from '@/composables/useRegistrationSigningStep'
import {answersToSend, missingAnswers, seedAnswers, type RegistrationAnswers} from '../eventshared/registrationAnswers'
import type {EventGroupDetail, EventGroupSignUp} from '@/api/generated/schema'
import {ownEventGroupPort, type EventGroupSignUpPort} from './eventGroupSignUpPort'
import {useSession} from '@/composables/useSession'
import {
  blockedByMaximum,
  choiceMeetsRules,
  choiceWaits,
  evaluateChoice,
  holdsOrWaitsForPlace,
  isPartFull,
  isSingleChoice,
  missingOf,
  partsOfSet,
  ruleOf,
} from '@/util/eventGroupChoice'
import {RegistrationStatus} from '@/api/generated/schema'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {useFullDateChoice} from '@/composables/useFullDateChoice'
import FullDateDialog from '../eventshared/FullDateDialog.vue'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDateTime} from '@/util/format'

/**
 * Signs somebody up for an event group, or changes the parts they chose: every part as a box, grouped by
 * set with the set's minimum beside it. Saving stays off while a set is short, and the dialog says
 * which one and by how much. A part called off cannot be newly chosen; one already held stays. A
 * part that already took place stays as it is. A new sign-up also answers the event group's questions;
 * those are changed on their own afterwards.
 *
 * <p>A part its set requires is ticked from the start of a new sign-up and cannot be unticked while it
 * takes place.
 *
 * <p>A block with a maximum takes no further part once it holds that many, and a block of exactly one
 * reads as a choice between alternatives: ticking one part takes the other off. A part whose places
 * are all taken says so; choosing it puts the whole sign-up on the waiting list, which the dialog says
 * before it is sent. A sign-up already accepted cannot add a full part, so such a part stays off.
 *
 * <p>Whoever runs events signing somebody else up may break the rules: the shortfalls and the excess
 * over a maximum are then only shown, the required parts can be unticked, a block at its maximum or
 * of exactly one takes more parts, and the event group marks the person as short. Where the
 * choice turns out to be full, they are asked whether it goes beyond the limit or onto the waiting
 * list, and the sign-up is sent again with the answer.
 *
 * <p>Where the event group asks every part for documents to sign, a sign-up of this station's event group
 * ends on the signing step once, for the whole event group; closing it finishes the sign-up.
 */
const props = withDefaults(defineProps<{
  detail: EventGroupDetail
  entry: EventGroupSignUp
  /** Whether whoever runs events signs somebody up, which passes the rules and parts already past. */
  override?: boolean
  /** Where the sign-up goes; an event group of this station unless a partner's event group page says otherwise. */
  port?: EventGroupSignUpPort
}>(), {
  override: false,
  port: undefined,
})

const emit = defineEmits<{
  close: []
  saved: []
}>()

const {t} = useI18n()
const {stationTimezone} = useSession()

const open = ref(true)
const busy = ref(false)
const failure = ref<Failure | null>(null)
const changing = computed(() => holdsOrWaitsForPlace(props.entry.status))
const held = new Set(changing.value ? props.entry.parts : [])
const chosen = ref<Set<number>>(new Set(changing.value ? held : startingChoice()))

/** A new sign-up starts with every required part that still takes place. */
function startingChoice(): number[] {
  return props.detail.parts
      .filter(part => part.required && !part.calledOff && !part.tookPlace)
      .map(part => part.eventId)
}
const name = computed(() => props.entry.member.name ?? '')
const asking = computed(() => !changing.value && props.detail.questions.length > 0)
const answers = ref<RegistrationAnswers>(seedAnswers(props.detail.questions))
const signingStep = ref<SigningStep | null>(null)
const signingOpen = ref(false)

const results = computed(() => evaluateChoice(props.detail.sets, props.detail.parts, chosen.value))
const accepted = props.entry.status === RegistrationStatus.ACCEPTED
const fullDate = useFullDateChoice()

/**
 * Whether a new sign-up as chosen would wait on the waiting list rather than get its places at once.
 * A sign-up already standing keeps where it stands when its parts change, and whoever runs events is
 * asked instead of told.
 */
const waits = computed(() => !changing.value && !props.override
    && choiceWaits(props.detail.eventGroup, props.detail.parts, chosen.value))
const answered = computed(() => !asking.value || missingAnswers(props.detail.questions, answers.value).length === 0)
const meets = computed(() => answered.value && (props.override
    ? chosen.value.size > 0
    : choiceMeetsRules(props.detail.sets, props.detail.parts, chosen.value)))

/**
 * Whether a part can be ticked or unticked: a part that took place stays as it is, and a part called
 * off can only stay where it was held. Whoever runs events passes the first rule.
 */
function selectable(eventId: number): boolean {
  const part = props.detail.parts.find(candidate => candidate.eventId === eventId)!
  if (part.tookPlace && !props.override) return false
  if (part.required && !part.calledOff && chosen.value.has(eventId) && !props.override) return false
  if (accepted && !props.override && !held.has(eventId) && isPartFull(part)) return false
  if (!props.override && !isSingleChoiceSet(part.setId)
      && blockedByMaximum(part, props.detail.sets, props.detail.parts, chosen.value)) return false
  return held.has(eventId) || !part.calledOff
}

function isSingleChoiceSet(setId: number): boolean {
  const set = props.detail.sets.find(candidate => candidate.id === setId)
  return set !== undefined && isSingleChoice(set)
}

/**
 * Ticks or unticks a part; in a block of exactly one, ticking a part takes the others off. Whoever
 * runs events passes that rule as they pass the maximum, and sees the excess as a shortfall instead.
 */
function toggle(eventId: number, on: boolean) {
  const next = new Set(chosen.value)
  const setId = props.detail.parts.find(part => part.eventId === eventId)?.setId
  if (on && !props.override && setId !== undefined && isSingleChoiceSet(setId)) {
    for (const other of partsOfSet(setId, props.detail.parts)) {
      if (!other.calledOff) next.delete(other.eventId)
    }
  }
  if (on) next.add(eventId)
  else next.delete(eventId)
  chosen.value = next
}

function nameOf(eventId: number): string {
  return props.detail.parts.find(part => part.eventId === eventId)?.name ?? ''
}

function close() {
  open.value = false
  emit('close')
}

/** Closing the signing step finishes the sign-up; the documents stay among the open tasks. */
function signingClosed(value: boolean) {
  if (value) return
  signingStep.value = null
  emit('saved')
}

async function save() {
  busy.value = true
  failure.value = null
  try {
    const parts = [...chosen.value]
    const port = props.port ?? ownEventGroupPort(props.detail.eventGroup.id)
    if (changing.value) {
      await port.changeParts(parts, props.entry.memberId)
    } else {
      const sent = await fullDate.sendAsking('signUp', null, whenFull =>
          port.signUp(parts, answersToSend(props.detail.questions, answers.value), props.entry.memberId, whenFull))
      if (!sent) return
    }
    open.value = false
    signingStep.value = port.signsHere
      ? await eventGroupSigningStep(props.detail.parts, parts, props.entry.memberId, stationTimezone.value)
      : null
    if (signingStep.value) signingOpen.value = true
    else emit('saved')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="lg" @update:model-value="value => { if (!value) close() }">
    <div class="space-y-4" data-testid="event-group-sign-up-dialog">
      <SubHeader>{{ changing ? t('eventGroups.dialogChangeTitle', {name}) : t('eventGroups.dialogTitle', {name}) }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('eventGroups.dialogHint', {name}) }}</MutedText>
      <FailureAlert v-if="failure" :failure="failure"/>
      <fieldset v-for="set in detail.sets" :key="set.id" class="space-y-1">
        <legend class="font-medium">{{ set.name }} <MutedText tag="span" size="sm">({{ ruleOf(set.id, detail.sets, detail.parts, t) }})</MutedText></legend>
        <FieldLabel v-for="part in partsOfSet(set.id, detail.parts)" :key="part.eventId" inline>
          <CheckboxInput
              :model-value="chosen.has(part.eventId)"
              :disabled="!selectable(part.eventId)"
              :data-testid="`event-group-part-${part.eventId}`"
              @update:model-value="value => toggle(part.eventId, value === true)"
          />
          <span :class="{'line-through': part.calledOff}">{{ part.name }}</span>
          <MutedText tag="span" size="sm">{{ formatDateTime(part.startTime, stationTimezone) }}</MutedText>
          <ErrorBadge v-if="isPartFull(part) && !part.calledOff" :data-testid="`event-group-part-full-${part.eventId}`">
            {{ t('eventGroups.partFull') }}
          </ErrorBadge>
        </FieldLabel>
      </fieldset>
      <ul class="text-sm space-y-1" :class="override ? 'text-(--text-muted)' : 'text-error'" data-testid="event-group-shortfalls">
        <li v-if="chosen.size === 0">{{ t('eventGroups.chooseSomething') }}</li>
        <template v-for="result in results" :key="result.setId">
          <li v-if="missingOf(result) > 0">{{ t('eventGroups.setShort', {name: result.name, missing: missingOf(result)}) }}</li>
          <li v-for="partId in result.missingRequired" :key="partId">{{ t('eventGroups.requiredMissing', {name: nameOf(partId)}) }}</li>
          <li v-if="result.tooMany > 0">{{ t('eventGroups.setTooMany', {name: result.name, maximum: result.maximum}) }}</li>
        </template>
      </ul>
      <MutedText v-if="waits" tag="p" size="sm" data-testid="event-group-sign-up-waits">{{ t('eventGroups.choiceWaits') }}</MutedText>
      <div v-if="asking" class="space-y-3" data-testid="event-group-questions">
        <SubHeader>{{ t('eventGroups.questions') }}</SubHeader>
        <RegistrationFieldInputs v-model="answers" :fields="detail.questions" test-id-prefix="event-group-question"/>
      </div>
      <ButtonRow pair align="end">
        <SecondaryButton data-cancel :disabled="busy" @click="close">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="busy || !meets" data-testid="event-group-sign-up-save" @click="save">
          {{ changing ? t('eventGroups.changeParts') : t('eventGroups.signUp') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
  <RegistrationSigningStep v-if="signingStep" :model-value="signingOpen" :step="signingStep"
                           @update:model-value="signingClosed"/>
  <FullDateDialog :question="fullDate.question.value" @answer="fullDate.answer"/>
</template>
