/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {OldPieceWhereabouts} from '@/api/generated/schema'

/**
 * The question calling a movement off asks about the piece the member handed in, which depends on
 * where that piece is.
 *
 * <p>At the station it can go back into the member's hands on the spot, which is the default; a broken
 * one is kept. Once it has left, only a hand-out of its own brings it back, and that is offered rather
 * than assumed, since it starts a new movement. Where the member still holds it, or nothing was handed
 * in, there is nothing to ask and nothing is drawn.
 */
const props = defineProps<{
  oldPiece: OldPieceWhereabouts
}>()

const giveBack = defineModel<boolean>('giveBack', {default: true})
const planWayBack = defineModel<boolean>('planWayBack', {default: false})

const {t} = useI18n()
const labelId = useId()

const atStation = computed(() => props.oldPiece === OldPieceWhereabouts.AT_STATION)
const away = computed(() => props.oldPiece === OldPieceWhereabouts.AWAY)

const outcome = computed(() => {
  if (atStation.value) return giveBack.value ? t('movements.callOffPiece.giveBackYes') : t('movements.callOffPiece.giveBackNo')
  return planWayBack.value ? t('movements.callOffPiece.wayBackYes') : t('movements.callOffPiece.wayBackNo')
})
</script>

<template>
  <div v-if="atStation || away" class="flex items-start justify-between gap-4 rounded-md border border-(--border) p-2" data-testid="call-off-choice">
    <div>
      <span :id="labelId" class="text-sm font-medium">
        {{ atStation ? t('movements.callOffPiece.giveBack') : t('movements.callOffPiece.wayBack') }}
      </span>
      <p class="text-xs text-(--text-muted)" data-testid="call-off-outcome">{{ outcome }}</p>
    </div>
    <ToggleInput v-if="atStation" v-model="giveBack" :aria-labelledby="labelId" data-testid="call-off-give-back"/>
    <ToggleInput v-else v-model="planWayBack" :aria-labelledby="labelId" data-testid="call-off-way-back"/>
  </div>
</template>
