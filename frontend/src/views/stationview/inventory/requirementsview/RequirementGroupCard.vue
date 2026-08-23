/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { InventoryRequirement } from '@/api/inventory'
import type { RequirementGroup } from './types'

const props = defineProps<{
  group: RequirementGroup
  inventoryName: (id: number) => string
}>()

const emit = defineEmits<{
  addItem: [target: { type: 'userType' | 'group'; key: string }]
  updateQuantity: [req: InventoryRequirement, quantity: number]
  remove: [req: InventoryRequirement]
  reorder: [group: RequirementGroup, fromIndex: number, toIndex: number]
}>()

const { t } = useI18n()

/**
 * The station's own rows, which are the ones it can reorder, count differently or take away.
 *
 * <p>The association's stand below them rather than among them: they are one definition read here, not a
 * copy the station holds, so there is nothing to drag and nothing to delete.
 */
const own = computed(() => props.group.items.filter(req => !req.clusterName))

const fromCluster = computed(() => props.group.items.filter(req => !!req.clusterName))

/** The association's inventory is not among the station's, so the name has to come with the row. */
function nameOf(req: InventoryRequirement): string {
  return req.inventoryName ?? props.inventoryName(req.inventoryId)
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>
        <span class="text-xs uppercase tracking-wide text-(--text-muted) mr-2">
          {{ group.type === 'userType' ? t('inventory.requirements.userType') : t('inventory.requirements.group') }}
        </span>
        {{ group.label }}
      </SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" @click="emit('addItem', { type: group.type, key: group.key })">
        {{ t('inventory.requirements.addItem') }}
      </SecondaryButton>
    </div>

    <DragList :items="own" :key-fn="(r) => r.id" @reorder="(from, to) => emit('reorder', props.group, from, to)">
      <template #default="{ item: req }">
        <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-grab active:cursor-grabbing">
          <MutedIcon size="md" :icon="['fas', 'grip-vertical']" />
          <div class="text-sm">{{ nameOf(req) }}</div>
          <NumberInput :model-value="req.quantity" :min="1" @update:model-value="emit('updateQuantity', req, $event as number)" />
          <DeleteButton @click="emit('remove', req)" />
        </div>
      </template>
    </DragList>

    <div
        v-for="req in fromCluster"
        :key="req.id"
        class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50"
        data-testid="cluster-requirement"
    >
      <MutedIcon size="md" :icon="['fas', 'building']" />
      <div class="flex items-center gap-2 min-w-0">
        <span class="text-sm truncate">{{ nameOf(req) }}</span>
        <InfoBadge data-testid="cluster-requirement-badge">{{ req.clusterName }}</InfoBadge>
      </div>
      <div class="text-sm text-(--text-muted)" data-testid="cluster-requirement-quantity">{{ req.quantity }}</div>
      <span/>
    </div>
  </NeutralContainer>
</template>
