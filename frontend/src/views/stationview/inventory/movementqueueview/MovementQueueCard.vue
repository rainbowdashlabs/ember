/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import MemberInventoryLink from '@/components/inventory/MemberInventoryLink.vue'
import MovementRowActions from './MovementRowActions.vue'
import {useMovementRowView} from './movementRowView'
import type {Movement} from '@/api/movements'

/**
 * One movement on a narrow screen, where the columns of the wide one have nowhere to go.
 *
 * <p>The same facts stacked in the order somebody reads them: what it is about, what kind of movement,
 * who it is with, where it stands, and only then the dates. Poured into one column the wide layout
 * gave a badge and a name and a date each their own line with nothing tying them together.
 */
const props = defineProps<{
  movement: Movement
  showMember: boolean
  canCorrect: boolean
  picking?: boolean
  picked?: boolean
}>()

const emit = defineEmits<{
  acknowledge: []
  correct: []
  open: []
  pick: []
}>()

const {t} = useI18n()
const {chip, open, owner, startedOn, movedOn, standing} = useMovementRowView(toRef(props, 'movement'))
</script>

<template>
  <NeutralContainer :data-movement="props.movement.id" class="space-y-2" data-testid="movement-row">
    <div class="flex flex-wrap items-center gap-2">
      <PrimaryBadge data-testid="movement-purpose">
        {{ t(`movements.purpose.${props.movement.purpose}`) }}
      </PrimaryBadge>
      <SecondaryBadge data-testid="movement-owner">{{ owner }}</SecondaryBadge>
    </div>

    <ItemChip :source="chip"/>

    <div v-if="props.showMember" class="text-sm">
      <MemberInventoryLink
          v-if="props.movement.memberIdentity"
          :identity="props.movement.memberIdentity"
          :member-id="props.movement.memberId"
      />
      <MutedText v-else size="sm">{{ t('movements.queue.forTheStore') }}</MutedText>
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

    <div class="text-xs text-(--text-muted)" data-testid="movement-dates">
      {{ t('movements.startedOn', {date: startedOn}) }}
      <template v-if="movedOn"> · {{ t('movements.movedOn', {date: movedOn}) }}</template>
    </div>

    <MovementRowActions
        :can-correct="props.canCorrect"
        :movement="props.movement"
        :picked="props.picked"
        :picking="props.picking"
        @acknowledge="emit('acknowledge')"
        @correct="emit('correct')"
        @open="emit('open')"
        @pick="emit('pick')"
    />
  </NeutralContainer>
</template>
