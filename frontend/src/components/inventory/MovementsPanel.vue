/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {movements} from '@/api'
import {MovementState, StepActor, type Movement} from '@/api/movements'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The movements a member is part of, beside their gear.
 *
 * <p>Their inventory lists what they hold and nothing else, so a piece handed in for an exchange
 * leaves it at that moment. This is where it goes: the piece by name, what is being done with it and
 * how far it has come. Without it, handing something in would look like losing it.
 *
 * <p>It carries the two things a member can do about one. Confirming receipt ends a chain, and it is
 * the only confirmation worth having, because a station saying so on their behalf is a claim rather
 * than a receipt. Calling off takes the whole thing back, and only while the piece is still on them.
 *
 * <p>Given a member, it shows theirs; given nobody, the reader's own. Whoever manages the inventory
 * reads the first on the screen that opens somebody's gear, where the same handed-in piece would
 * otherwise be missing without explanation.
 */
const props = defineProps<{
  /** Whose movements to show. Left out for the reader's own, which is the member's own page. */
  memberId?: number | null
}>()
const emit = defineEmits<{
  /** Something closed or moved on, so whatever lists the member's gear beside this should read again. */
  changed: []
}>()

const {t} = useI18n()

const open = ref<Movement[]>([])
const busy = ref(false)
const actionFailure = ref<Failure | null>(null)

/** Whether this is somebody's gear being looked at rather than the reader's own. */
const watching = computed(() => props.memberId != null)

/** The ones standing on a step of the member's own, which are the ones with something to press. */
const waitingOnMe = computed(() =>
    watching.value ? [] : open.value.filter(movement => movement.currentStepActor === StepActor.MEMBER))

/**
 * Why the list could not be fetched.
 *
 * <p>A failed fetch used to empty the list instead of saying so, which reads as "nothing is out",
 * the one answer nobody can act on. After calling a movement off it was worse still: the piece
 * vanished from the screen and looked handed back.
 */
const loadFailure = ref<Failure | null>(null)

async function load() {
  loadFailure.value = null
  try {
    const all = await movements.listMovements()
    open.value = all
        .filter(movement => movement.state === MovementState.OPEN)
        .filter(movement => !watching.value || movement.memberId === props.memberId)
  } catch (e) {
    loadFailure.value = describeFailure(e, t)
  }
}

watch(() => props.memberId, load)

/**
 * Takes the movement back, which a member may do while the piece is still on them.
 *
 * <p>Somebody who asks for a bigger jacket and finds the next morning that it fits after all should
 * not have to ask the station to undo it. Once they have handed it in, the button is gone: from that
 * moment the station is the one who can say what happens to it.
 */
async function callOff(movement: Movement) {
  busy.value = true
  actionFailure.value = null
  try {
    await movements.cancelMovement(movement.id, t('movements.callOffReason'))
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    busy.value = false
    return
  }
  await catchUp()
  busy.value = false
}

/**
 * Fetching the list again after something was done to it, which is not part of doing it.
 *
 * <p>Sharing one attempt meant a movement that really was called off, followed by a list that failed
 * to come back, read as a call-off that had failed. Somebody told that presses the button again, on a
 * movement that no longer exists.
 */
async function catchUp() {
  emit('changed')
  await load()
  if (loadFailure.value) {
    actionFailure.value = {...loadFailure.value, message: t('failure.staleAfterAction')}
    loadFailure.value = null
  }
}

async function confirm(movement: Movement) {
  busy.value = true
  actionFailure.value = null
  try {
    const detail = await movements.getMovement(movement.id)
    const step = detail.steps.find(candidate => candidate.current)
    if (!step) {
      busy.value = false
      return
    }
    await movements.acknowledgeStep(movement.id, {stepId: step.id, note: ''})
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    busy.value = false
    return
  }
  await catchUp()
  busy.value = false
}

onMounted(load)
</script>

<template>
  <NeutralContainer v-if="open.length > 0" class="space-y-3" data-testid="my-movements">
    <SectionHeader>{{ t('movements.mine') }}</SectionHeader>
    <MutedText size="sm" tag="p">{{ t('movements.mineHint') }}</MutedText>

    <FailureAlert :failure="actionFailure ?? loadFailure"/>

    <div
        v-for="movement in open"
        :key="movement.id"
        class="flex items-center justify-between gap-2 text-sm"
        data-testid="my-movement"
    >
      <div>
        <span class="font-medium">{{ movement.itemName || t(`movements.purpose.${movement.purpose}`) }}</span>
        <MutedText v-if="movement.itemName" size="sm" class="ml-2">
          {{ t(`movements.purpose.${movement.purpose}`) }}
        </MutedText>
        <span v-if="movement.reachedStepLabel" class="text-(--text-muted)"> · {{ movement.reachedStepLabel }}</span>
      </div>
      <ButtonRow pair align="end">
        <SecondaryButton
            v-if="movement.itemStillWithMember"
            :disabled="busy"
            data-testid="call-off"
            @click="callOff(movement)"
        >
          {{ t('movements.callOff') }}
        </SecondaryButton>
        <PrimaryButton
            v-if="waitingOnMe.includes(movement)"
            :disabled="busy"
            data-testid="confirm-receipt"
            @click="confirm(movement)"
        >
          {{ t('movements.confirmReceipt') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </NeutralContainer>
</template>
