/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MemberInventoryLink from '@/components/inventory/MemberInventoryLink.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {glyphFor} from '@/util/glyph'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {formatDate} from '@/util/format'
import type {Movement} from '@/api/movements'

/**
 * What is on the move, as the inventory overview shows it: the piece, whose it is, and the step it
 * stands on. No status, because a movement has none.
 */
const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()
const {isMobile} = useBreakpoint()

const props = defineProps<{
  movements: Movement[]
}>()

function chipOf(movement: Movement) {
  return {
    glyph: glyphFor({icon: movement.icon, color: movement.color}),
    name: movement.itemName ?? movement.incomingItemName ?? movement.inventoryName ?? '',
    internalId: movement.itemInternalId,
    sizeName: movement.itemSizeName,
    inventoryName: movement.itemName ? movement.inventoryName : null,
  }
}

function toQueue() {
  if (routes.movements) void router.push({name: routes.movements})
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2"/>
      {{ t('inventory.overview.exchanges') }} ({{ props.movements.length }})
    </SubHeader>

    <div v-if="isMobile" class="space-y-2">
      <NeutralContainer v-for="movement in props.movements" :key="movement.id" class="cursor-pointer space-y-1"
                        @click="toQueue">
        <ItemChip :source="chipOf(movement)"/>
        <div class="flex flex-wrap items-center gap-1">
          <PrimaryBadge>{{ t(`movements.purpose.${movement.purpose}`) }}</PrimaryBadge>
          <InfoBadge v-if="movement.reachedStepLabel">{{ movement.reachedStepLabel }}</InfoBadge>
          <SecondaryBadge v-if="movement.currentStepActor">
            {{ t(`movements.actor.${movement.currentStepActor}`) }}
          </SecondaryBadge>
        </div>
        <div class="text-xs text-(--text-muted)"><MemberInventoryLink :identity="movement.memberIdentity ?? null" :member-id="movement.memberId"/></div>
        <div class="text-xs text-(--text-muted)">
          {{ t('inventory.overview.createdAt') }}: {{ formatDate(movement.createdAt) }}
        </div>
      </NeutralContainer>
    </div>

    <DataTable v-else>
      <template #head>
        <Th>{{ t('inventory.overview.colItem') }}</Th>
        <Th>{{ t('movements.queue.columns.purpose') }}</Th>
        <Th>{{ t('inventory.overview.colOwner') }}</Th>
        <Th>{{ t('movements.queue.columns.step') }}</Th>
        <Th>{{ t('inventory.overview.colCreated') }}</Th>
      </template>
      <TRow v-for="movement in props.movements" :key="movement.id" class="cursor-pointer hover:bg-(--bg-accent)"
            @click="toQueue">
        <Td><ItemChip :source="chipOf(movement)"/></Td>
        <Td><PrimaryBadge>{{ t(`movements.purpose.${movement.purpose}`) }}</PrimaryBadge></Td>
        <Td><MemberInventoryLink :identity="movement.memberIdentity ?? null" :member-id="movement.memberId"/></Td>
        <Td>
          <InfoBadge v-if="movement.reachedStepLabel">{{ movement.reachedStepLabel }}</InfoBadge>
          <MutedText v-if="movement.currentStepActor" size="sm" class="ml-1">
            {{ t(`movements.actor.${movement.currentStepActor}`) }}
          </MutedText>
        </Td>
        <Td muted>{{ formatDate(movement.createdAt) }}</Td>
      </TRow>
    </DataTable>
  </div>
</template>
