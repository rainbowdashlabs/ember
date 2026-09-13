/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberInventoryLink from '@/components/inventory/MemberInventoryLink.vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {useMovementRowView} from './movementRowView'
import type {Movement} from '@/api/movements'

/**
 * One movement as the columns of a wide screen read it: what kind it is, what it is about, who it is
 * with, where it stands, and since when.
 *
 * <p>No status anywhere. Where a movement stands is the last step whose words are already true, and a
 * step is named after the state it brings about: the step it is waiting on has not happened yet, so
 * wearing that label would say a piece had been taken in while it is still on the member. The step it
 * waits on is on the button instead, where pressing it is what makes those words true.
 */
const props = defineProps<{
  movement: Movement
  showMember: boolean
}>()

const {t} = useI18n()
const {chip, open, owner, startedOn, movedOn, standing} = useMovementRowView(toRef(props, 'movement'))
</script>

<template>
  <div class="flex flex-col items-start gap-1">
    <PrimaryBadge data-testid="movement-purpose">{{ t(`movements.purpose.${props.movement.purpose}`) }}</PrimaryBadge>
    <SecondaryBadge data-testid="movement-owner">{{ owner }}</SecondaryBadge>
  </div>

  <div class="flex min-w-0 flex-col gap-0.5">
    <ItemChip :source="chip"/>
  </div>

  <div class="min-w-0 truncate text-sm">
    <template v-if="props.showMember">
      <MemberInventoryLink
          v-if="props.movement.memberIdentity"
          :identity="props.movement.memberIdentity"
          :member-id="props.movement.memberId"
      />
      <MutedText v-else size="sm">{{ t('movements.queue.forTheStore') }}</MutedText>
    </template>
  </div>

  <div class="flex flex-wrap items-center gap-2 text-sm">
    <template v-if="open">
      <InfoBadge v-if="props.movement.reachedStepLabel" data-testid="movement-step">
        {{ props.movement.reachedStepLabel }}
      </InfoBadge>
      <SecondaryBadge v-if="standing" data-testid="movement-turn">{{ standing }}</SecondaryBadge>
    </template>
    <SecondaryBadge v-else data-testid="movement-state">{{ standing }}</SecondaryBadge>
  </div>

  <div class="flex flex-col text-xs text-(--text-muted)" data-testid="movement-dates">
    <span>{{ t('movements.startedOn', {date: startedOn}) }}</span>
    <span v-if="movedOn">{{ t('movements.movedOn', {date: movedOn}) }}</span>
  </div>
</template>
