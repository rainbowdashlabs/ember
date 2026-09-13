/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {MemberNotes} from '@/api/attendance'
import {StepActor} from '@/api/movements'

const {t} = useI18n()

/**
 * What stands beside a member's name while the sheet is worked through: the swaps of theirs that are
 * under way, anything of theirs that was found, and a birthday just gone.
 *
 * <p>Handing a piece over means naming which piece, and that is not a decision to make from a sheet
 * of names. Where none is set aside yet the note says so rather than offering a button the step
 * would refuse.
 */
const props = defineProps<{
  notes?: MemberNotes
  /** Whether this reader may move a swap on. Seeing one and moving it are different rights. */
  canMoveSwap?: boolean
  /** Whether this reader may sign a found item over. */
  canSignOffFound?: boolean
}>()

const emit = defineEmits<{
  moveSwap: [movementId: number, stepId: number, replacementItemId: number | null]
  signOffFound: [itemId: number]
}>()

const hasAnything = computed(
    () => !!props.notes && (props.notes.swaps.length > 0 || props.notes.foundItems.length > 0
        || props.notes.birthdayDaysAgo !== null),
)

/**
 * The birthday in words. Today is its own sentence rather than "no days ago", which is what somebody
 * standing in front of the member would actually say.
 */
const birthdayText = computed(() => {
  const days = props.notes?.birthdayDaysAgo
  if (days === null || days === undefined) return ''
  if (days === 0) return t('checkNotes.birthdayToday')
  if (days === 1) return t('checkNotes.birthdayYesterday')
  return t('checkNotes.birthdayDaysAgo', {days})
})
</script>

<template>
  <div v-if="hasAnything" class="space-y-2" data-testid="member-check-notes">
    <div
        v-if="notes?.birthdayDaysAgo !== null && notes?.birthdayDaysAgo !== undefined"
        class="flex items-center gap-2 text-sm"
        data-testid="note-birthday"
    >
      <font-awesome-icon :icon="['fas', 'cake-candles']" class="text-primary"/>
      <span>{{ birthdayText }}</span>
    </div>

    <div
        v-for="swap in notes?.swaps ?? []"
        :key="swap.movementId"
        :data-swap="swap.movementId"
        class="flex flex-wrap items-center gap-2 text-sm"
        data-testid="note-swap"
    >
      <font-awesome-icon :icon="['fas', 'right-left']" class="text-primary"/>
      <span>{{ swap.inventoryName }}</span>
      <InfoBadge>{{ swap.stepLabel }}</InfoBadge>
      <PrimaryButton
          v-if="canMoveSwap && swap.stepId !== null && swap.handOverNext && swap.replacementItemId !== null"
          class="text-xs"
          data-testid="note-swap-hand-over"
          @click="emit('moveSwap', swap.movementId, swap.stepId, swap.replacementItemId)"
      >
        {{ t('checkNotes.handOver') }}
      </PrimaryButton>
      <span
          v-else-if="swap.handOverNext"
          class="text-xs text-(--text-muted)"
          data-testid="note-swap-needs-replacement"
      >
        {{ t('checkNotes.replacementNotChosen') }}
      </span>
      <SecondaryButton
          v-else-if="canMoveSwap && swap.stepId !== null && swap.stepActor === StepActor.STATION"
          class="text-xs"
          data-testid="note-swap-move-on"
          @click="emit('moveSwap', swap.movementId, swap.stepId, swap.replacementItemId)"
      >
        {{ t('checkNotes.moveOn') }}
      </SecondaryButton>
    </div>

    <div
        v-for="item in notes?.foundItems ?? []"
        :key="item.itemId"
        class="flex flex-wrap items-center gap-2 text-sm"
        data-testid="note-found"
    >
      <font-awesome-icon :icon="['fas', 'box-archive']" class="text-primary"/>
      <span>{{ t('checkNotes.foundItem', {description: item.description}) }}</span>
      <PrimaryButton
          v-if="canSignOffFound"
          class="text-xs"
          data-testid="note-found-sign-off"
          @click="emit('signOffFound', item.itemId)"
      >
        {{ t('checkNotes.signOffFound') }}
      </PrimaryButton>
    </div>
  </div>
</template>
