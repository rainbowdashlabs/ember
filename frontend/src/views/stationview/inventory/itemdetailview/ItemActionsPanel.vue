/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {computed} from 'vue'
import type {InventoryItem} from '@/api/generated/schema'
import {isWithinReach} from '@/components/input/search/itemsearch/itemFilters'

const props = withDefaults(defineProps<{
  item: InventoryItem
  /** Whether there is anybody to hand it to. An association keeps gear in containers, not on people. */
  canAssign?: boolean
  /**
   * Whether an open movement carries the piece. It is the movement's to move, so handing it out, taking
   * it back and reporting it missing are not offered here.
   */
  onMovement?: boolean
  /** Whether a loan to a partner station holds the piece, from its approval until its end date. */
  heldByLoan?: boolean
}>(), {
  canAssign: true,
  onMovement: false,
  heldByLoan: false,
})

/**
 * What the station may still do with the piece, offered only where it would not be refused.
 *
 * <p>A piece a movement carries or a loan holds belongs to that for now, so nothing is offered for it.
 * A missing piece is neither handed out nor taken back: it comes back by being found. Only a piece within
 * reach can be handed out at all: on the station's own shelf, at the station, or with a member, and not
 * in the store of an association that owns it.
 */
const missing = computed(() => !!props.item.lostAt)
const spokenFor = computed(() => props.onMovement || props.heldByLoan)
const reachable = computed(() => isWithinReach(props.item))
const mayHandOut = computed(() => props.canAssign && !missing.value && !spokenFor.value && reachable.value)
const mayTakeBack = computed(() => !!props.item.assignedTo && !missing.value && !spokenFor.value)
const mayReportMissing = computed(() => !missing.value && !spokenFor.value)

const emit = defineEmits<{
  assign: []
  unassign: []
  markLost: []
  markFound: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('itemDetail.actions') }}</SubHeader>
    <ButtonRow>
      <PrimaryButton v-if="mayHandOut" :icon="['fas', 'user-plus']" @click="emit('assign')">
        {{ t('itemDetail.assign') }}
      </PrimaryButton>
      <SecondaryButton v-if="mayTakeBack" :icon="['fas', 'user-minus']" @click="emit('unassign')">
        {{ t('itemDetail.unassign') }}
      </SecondaryButton>
      <ErrorButton v-if="mayReportMissing" :icon="['fas', 'triangle-exclamation']" @click="emit('markLost')">
        {{ t('itemDetail.markLost') }}
      </ErrorButton>
      <SuccessButton v-if="props.item.lostAt" :icon="['fas', 'check']" @click="emit('markFound')">
        {{ t('itemDetail.markFound') }}
      </SuccessButton>
    </ButtonRow>
    <MutedText v-if="missing && props.item.assignedTo" size="sm" tag="p" data-testid="item-missing-hint">
      {{ t('itemDetail.missingHint') }}
    </MutedText>
    <MutedText v-else-if="spokenFor" size="sm" tag="p" data-testid="item-spoken-for-hint">
      {{ props.onMovement ? t('itemDetail.onMovementHint') : t('itemDetail.heldByLoanHint') }}
    </MutedText>
  </NeutralContainer>
</template>
