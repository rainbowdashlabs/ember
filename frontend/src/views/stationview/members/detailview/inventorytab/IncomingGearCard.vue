/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import type {MovementResponse} from '@/api/generated/schema'

/**
 * A piece an open movement is bringing the member, standing among the pieces of its inventory.
 *
 * <p>It names the piece where the movement already names one, and the inventory and the size where it
 * names only those. The step it waits for says how far it has come, and the card leads to the movement.
 */
const props = defineProps<{
  movement: MovementResponse
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()

const title = computed(() => props.movement.incomingItemName
    || props.movement.inventoryName
    || t(`movements.purpose.${props.movement.purpose}`))

/** The size asked for, which only says something while no piece is named yet. */
const wantedSize = computed(() => props.movement.incomingItemName
    ? null
    : props.movement.newSizeName ?? props.movement.oldSizeName)

const target = computed(() => ({name: routes.movement, params: {id: String(props.movement.id)}}))
</script>

<template>
  <NeutralContainer class="flex items-center justify-between gap-2 border-dashed" data-testid="incoming-gear">
    <div class="space-y-1">
      <div class="text-sm font-medium">
        {{ title }}
        <SizeBadge v-if="wantedSize">{{ wantedSize }}</SizeBadge>
      </div>
      <InfoBadge>{{ t('memberDetail.incomingBadge') }}</InfoBadge>
      <div v-if="movement.currentStepLabel" class="text-xs text-(--text-muted)">
        {{ t('memberDetail.incomingStep', {step: movement.currentStepLabel}) }}
      </div>
    </div>
    <router-link :to="target" class="text-sm text-primary hover:underline" data-testid="incoming-gear-link">
      {{ t('memberDetail.incomingOpen', {id: movement.id}) }}
    </router-link>
  </NeutralContainer>
</template>
