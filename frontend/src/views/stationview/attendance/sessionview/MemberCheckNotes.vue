/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {actionCount, hasBirthday} from './memberNotes'
import type {MemberNotes, SwapNote} from '@/api/generated/schema'

const {t} = useI18n()

/**
 * What stands beside a member's name while the sheet is worked through: the swaps of theirs that are
 * under way, anything of theirs that was found, and a birthday just gone.
 *
 * <p>Handing a piece over means naming which piece, and that is not a decision to make from a sheet
 * of names. Where none is set aside yet the note says so rather than offering a button the step
 * would refuse.
 */
const props = withDefaults(defineProps<{
  notes?: MemberNotes
  /** Whether this reader may move a swap on and call one off, which is the one right. */
  canManageSwap?: boolean
  /** Whether this reader may sign a found item over. */
  canSignOffFound?: boolean
  /**
   * Which of the notes to show: all of them, only the birthday, or only what there is to settle, so a
   * screen can put the birthday beside the name and what there is to settle below its own buttons.
   */
  part?: 'all' | 'birthday' | 'actions'
}>(), {notes: undefined, part: 'all'})

const emit = defineEmits<{
  moveSwap: [movementId: number, stepId: number, replacementItemId: number | null]
  dropSwap: [movementId: number]
  signOffFound: [itemId: number]
}>()

const showsBirthday = computed(() => props.part !== 'actions' && hasBirthday(props.notes))
const showsActions = computed(() => props.part !== 'birthday' && actionCount(props.notes) > 0)
const anything = computed(() => showsBirthday.value || showsActions.value)

/** The swap asked to be called off, held until said twice: a mis-press here cannot be got back. */
const dropping = ref<SwapNote | null>(null)
const confirming = computed({
  get: () => dropping.value !== null,
  set: open => { if (!open) dropping.value = null },
})

function dropConfirmed() {
  const swap = dropping.value
  dropping.value = null
  if (swap) emit('dropSwap', swap.movementId)
}

/**
 * What the note is about: the piece the step names, falling back to the inventory it is out of for a
 * step whose piece has not been picked yet.
 */
function pieceOf(swap: SwapNote): string {
  return swap.itemName?.trim() || swap.inventoryName
}

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
  <div v-if="anything" class="space-y-2" data-testid="member-check-notes">
    <div
        v-if="showsBirthday"
        class="flex items-center gap-2 text-sm"
        data-testid="note-birthday"
    >
      <font-awesome-icon :icon="['fas', 'cake-candles']" class="text-primary"/>
      <span>{{ birthdayText }}</span>
    </div>

    <ButtonRow
        v-for="swap in showsActions ? notes?.swaps ?? [] : []"
        :key="swap.movementId"
        :data-swap="swap.movementId"
        class="text-sm"
        data-testid="note-swap"
    >
      <font-awesome-icon :icon="['fas', 'right-left']" class="text-primary"/>
      <span>{{ pieceOf(swap) }}</span>
      <InfoBadge v-if="swap.itemSize">{{ swap.itemSize }}</InfoBadge>
      <span
          v-if="swap.handOverNext && swap.replacementItemId === null"
          class="text-xs text-(--text-muted)"
          data-testid="note-swap-needs-replacement"
      >
        {{ t('checkNotes.replacementNotChosen') }}
      </span>
      <PrimaryButton
          v-else-if="canManageSwap && swap.stepId !== null"
          class="text-xs"
          data-testid="note-swap-step"
          @click="emit('moveSwap', swap.movementId, swap.stepId, swap.replacementItemId)"
      >
        {{ swap.stepLabel }}
      </PrimaryButton>
      <InfoBadge v-else data-testid="note-swap-waiting">{{ swap.stepLabel }}</InfoBadge>
      <DeleteButton v-if="canManageSwap" data-testid="note-swap-drop" @click="dropping = swap"/>
    </ButtonRow>

    <div
        v-for="item in showsActions ? notes?.foundItems ?? [] : []"
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

    <ConfirmDeleteModal
        v-model="confirming"
        :message="t('checkNotes.dropSwapConfirm', {piece: dropping ? pieceOf(dropping) : ''})"
        @confirm="dropConfirmed"
    />
  </div>
</template>
