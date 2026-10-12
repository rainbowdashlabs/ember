/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {MovementState, type MovementResponse} from '@/api/generated/schema'

/**
 * What can be done with one movement from the list it is in.
 *
 * <p>The step it waits on is on the button, because pressing it is what makes those words true. While
 * rows are being ticked for an export the buttons give way to the tick: the list is being read then,
 * not worked through.
 *
 * <p>Laid out as a button row, so that on a phone, where these stand at the foot of a card, each
 * button takes the width rather than three of them fighting over one line.
 */
const props = defineProps<{
  movement: MovementResponse
  canCorrect: boolean
  picking?: boolean
  picked?: boolean
}>()

const emit = defineEmits<{
  acknowledge: []
  correct: []
  pick: []
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()

/** The movement's own page, a link so it also opens in a new tab; nothing where the area has no such page. */
const detailRoute = computed(() => routes.movement
    ? {name: routes.movement, params: {id: String(props.movement.id)}}
    : null)
</script>

<template>
  <div v-if="props.picking" class="flex items-center justify-end">
    <CheckboxInput :model-value="props.picked ?? false" data-testid="movement-pick"
                   @update:model-value="emit('pick')">
      {{ t('movements.queue.pickForExport') }}
    </CheckboxInput>
  </div>
  <ButtonRow v-else align="end">
    <PrimaryButton
        v-if="props.movement.actionable && props.movement.state === MovementState.OPEN"
        class="text-xs"
        data-testid="movement-acknowledge"
        @click="emit('acknowledge')"
    >
      {{ props.movement.currentStepLabel ?? t('movements.queue.acknowledge') }}
    </PrimaryButton>
    <SecondaryButton
        v-if="props.canCorrect && props.movement.state === MovementState.OPEN"
        class="text-xs"
        data-testid="movement-correct"
        @click="emit('correct')"
    >
      {{ t('movements.queue.correct') }}
    </SecondaryButton>
    <SecondaryButton v-if="detailRoute" class="text-xs" data-testid="movement-open" :to="detailRoute">
      {{ t('movements.queue.openDetail') }}
    </SecondaryButton>
  </ButtonRow>
</template>
