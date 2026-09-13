/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {glyphFor} from '@/util/glyph'
import {useMovementParties} from '@/composables/useMovementParties'
import {MovementPurpose, type Movement} from '@/api/movements'

/**
 * Both ends of a movement: what is going back, what is coming for it, and whose gear that is.
 *
 * <p>A swap that has not chosen a replacement yet still says what was asked for, down to the size,
 * because "which piece am I getting" is the question the member asks and the record half answers it
 * from the moment it is raised.
 */
const {t} = useI18n()

const props = defineProps<{movement: Movement}>()

const {ownerLabel} = useMovementParties(toRef(props, 'movement'))

/** A swap, an issue and a request all end with a piece arriving; a return does not. */
const awaitsAPiece = computed(() =>
    props.movement.purpose === MovementPurpose.EXCHANGE
    || props.movement.purpose === MovementPurpose.REQUEST
    || props.movement.purpose === MovementPurpose.ISSUE)

const glyph = computed(() => glyphFor({icon: props.movement.icon, color: props.movement.color}))

const outgoing = computed(() => (props.movement.itemName || props.movement.itemInternalId
    ? {
      glyph: glyph.value,
      name: props.movement.itemName ?? props.movement.itemInternalId ?? '',
      internalId: props.movement.itemInternalId,
      sizeName: props.movement.oldSizeName,
    }
    : null))

const incoming = computed(() => (awaitsAPiece.value || props.movement.incomingItemName
    ? {
      glyph: glyph.value,
      name: props.movement.incomingItemName ?? props.movement.inventoryName ?? '',
      sizeName: props.movement.newSizeName ?? props.movement.oldSizeName,
    }
    : null))
</script>

<template>
  <div class="space-y-1">
    <div v-if="outgoing" class="flex flex-wrap items-center gap-2" data-testid="movement-outgoing">
      <span class="text-(--text-muted)">{{ t('movements.outgoing') }}:</span>
      <ItemChip :source="outgoing"/>
    </div>

    <div v-if="incoming" class="flex flex-wrap items-center gap-2" data-testid="movement-incoming">
      <span class="text-(--text-muted)">{{ t('movements.incoming') }}:</span>
      <ItemChip :source="incoming"/>
    </div>

    <div class="text-(--text-muted)" data-testid="movement-owner">
      {{ t('movements.ownedBy', {owner: ownerLabel}) }}
    </div>
  </div>
</template>
