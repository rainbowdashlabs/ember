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

const {t} = useI18n()

const props = defineProps<{
  notes?: MemberNotes
  /** Whether this reader may move a swap on. Seeing one and moving it are different rights. */
  canMoveSwap?: boolean
  /** Whether this reader may sign a found item over. */
  canSignOffFound?: boolean
}>()

const emit = defineEmits<{
  moveSwap: [exchangeId: number, nextStatus: string]
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
        :key="swap.exchangeId"
        class="flex flex-wrap items-center gap-2 text-sm"
        data-testid="note-swap"
    >
      <font-awesome-icon :icon="['fas', 'right-left']" class="text-primary"/>
      <span>{{ swap.inventoryName }}</span>
      <InfoBadge>{{ t('checkNotes.waitingOn.' + swap.status) }}</InfoBadge>
      <PrimaryButton
          v-if="canMoveSwap && swap.nextStatus && swap.handOverNext"
          class="text-xs"
          data-testid="note-swap-hand-over"
          @click="emit('moveSwap', swap.exchangeId, swap.nextStatus)"
      >
        {{ t('checkNotes.handOver') }}
      </PrimaryButton>
      <SecondaryButton
          v-else-if="canMoveSwap && swap.nextStatus"
          class="text-xs"
          data-testid="note-swap-move-on"
          @click="emit('moveSwap', swap.exchangeId, swap.nextStatus)"
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
