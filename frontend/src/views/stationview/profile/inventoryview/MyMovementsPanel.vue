/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {movements} from '@/api'
import {MovementState, StepActor, type Movement} from '@/api/movements'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The chains this member is part of, and the button to say a piece arrived.
 *
 * <p>Every chain now ends with the person who ends up holding the gear confirming that they have it,
 * which is the only confirmation worth having: the station saying so for them is a claim, not a
 * receipt. That step was unreachable, because nothing on the member's own pages led to their chains
 * and the chain page is only linked from screens a member cannot open. The ability was there the
 * whole time; the way in was not.
 */
const {t} = useI18n()

const open = ref<Movement[]>([])
const busy = ref(false)
const error = ref('')

/** The ones standing on a step of the member's own, which are the ones with something to press. */
const waitingOnMe = computed(() => open.value.filter(movement => movement.currentStepActor === StepActor.MEMBER))

async function load() {
  try {
    open.value = (await movements.listMovements()).filter(movement => movement.state === MovementState.OPEN)
  } catch {
    open.value = []
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
        <span class="font-medium">{{ t(`movements.purpose.${movement.purpose}`) }}</span>
        <span v-if="movement.currentStepLabel" class="text-(--text-muted)"> · {{ movement.currentStepLabel }}</span>
      </div>
      <PrimaryButton
          v-if="waitingOnMe.includes(movement)"
          :disabled="busy"
          data-testid="confirm-receipt"
          @click="confirm(movement)"
      >
        {{ t('movements.confirmReceipt') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
