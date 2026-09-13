/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {glyphFor} from '@/util/glyph'
import {formatDate} from '@/util/format'
import {MovementState, type Movement} from '@/api/movements'

/**
 * One Vorgang, as the queue reads it: what it is about, who it is with, and the step it stands on.
 *
 * <p>No status anywhere. Where a Vorgang stands is the step it is on and the party whose turn it is,
 * which is what the chain itself says, and the row says nothing the chain does not.
 */
const props = defineProps<{
  movement: Movement
  showMember: boolean
}>()

const {t} = useI18n()

const chip = computed(() => ({
  glyph: glyphFor({icon: props.movement.icon, color: props.movement.color}),
  name: props.movement.itemName ?? props.movement.incomingItemName ?? props.movement.inventoryName ?? '',
  internalId: props.movement.itemInternalId,
  sizeName: props.movement.itemSizeName,
}))

const open = computed(() => props.movement.state === MovementState.OPEN)
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <ItemChip :source="chip"/>
    <PrimaryBadge data-testid="movement-purpose">{{ t(`movements.purpose.${props.movement.purpose}`) }}</PrimaryBadge>
  </div>

  <div v-if="props.showMember" class="min-w-0 truncate text-sm">
    <MemberName v-if="props.movement.memberIdentity" :identity="props.movement.memberIdentity"/>
    <span v-else-if="props.movement.memberName">{{ props.movement.memberName }}</span>
    <MutedText v-else size="sm">{{ t('movements.queue.forTheStore') }}</MutedText>
  </div>

  <div class="flex flex-wrap items-center gap-2 text-sm">
    <template v-if="open">
      <InfoBadge data-testid="movement-step">{{ props.movement.currentStepLabel }}</InfoBadge>
      <SecondaryBadge v-if="props.movement.currentStepActor" data-testid="movement-turn">
        {{ t(`movements.actor.${props.movement.currentStepActor}`) }}
      </SecondaryBadge>
    </template>
    <SecondaryBadge v-else data-testid="movement-state">
      {{ t(`movements.state.${props.movement.state}`) }}
    </SecondaryBadge>
  </div>

  <MutedText size="sm">{{ formatDate(props.movement.createdAt) }}</MutedText>
</template>
