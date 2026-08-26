/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {movements} from '@/api'
import {MovementState, StepActor, type Movement} from '@/api/movements'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The movements a member is part of, beside their gear.
 *
 * <p>Their inventory lists what they hold and nothing else, so a piece handed in for an exchange
 * leaves it at that moment. This is where it goes: the piece by name, what is being done with it and
 * which step it waits on. Without it, handing something in would look like losing it.
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
const error = ref('')

/** Whether this is somebody's gear being looked at rather than the reader's own. */
const watching = computed(() => props.memberId != null)

/** The ones standing on a step of the member's own, which are the ones with something to press. */
const waitingOnMe = computed(() =>
    watching.value ? [] : open.value.filter(movement => movement.currentStepActor === StepActor.MEMBER))

async function load() {
  try {
    const all = await movements.listMovements()
    open.value = all
        .filter(movement => movement.state === MovementState.OPEN)
        .filter(movement => !watching.value || movement.memberId === props.memberId)
  } catch {
    open.value = []
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
  error.value = ''
  try {
    await movements.cancelMovement(movement.id, t('movements.callOffReason'))
    await load()
    emit('changed')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    busy.value = false
  }
}

async function confirm(movement: Movement) {
  busy.value = true
  error.value = ''
  try {
    const detail = await movements.getMovement(movement.id)
    const step = detail.steps.find(candidate => candidate.current)
    if (!step) return
    await movements.acknowledgeStep(movement.id, {stepId: step.id, note: ''})
    await load()
    emit('changed')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <NeutralContainer v-if="open.length > 0" class="space-y-3" data-testid="my-movements">
    <SectionHeader>{{ t('movements.mine') }}</SectionHeader>
    <MutedText size="sm" tag="p">{{ t('movements.mineHint') }}</MutedText>

    <Alert v-if="error" variant="error">{{ error }}</Alert>

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
        <span v-if="movement.currentStepLabel" class="text-(--text-muted)"> · {{ movement.currentStepLabel }}</span>
      </div>
      <div class="flex items-center gap-2">
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
      </div>
    </div>
  </NeutralContainer>
</template>
