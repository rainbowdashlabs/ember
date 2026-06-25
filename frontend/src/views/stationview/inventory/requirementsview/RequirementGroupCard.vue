/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import type { InventoryRequirement } from '@/api/types'
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

    <DragList :items="group.items" :key-fn="(r) => r.id" @reorder="(from, to) => emit('reorder', props.group, from, to)">
      <template #default="{ item: req }">
        <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-grab active:cursor-grabbing">
          <MutedIcon size="md" :icon="['fas', 'grip-vertical']" />
          <div class="text-sm">{{ inventoryName(req.inventoryId) }}</div>
          <NumberInput :model-value="req.quantity" :min="1" @update:model-value="emit('updateQuantity', req, $event as number)" />
          <DeleteButton @click="emit('remove', req)" />
        </div>
      </template>
    </DragList>
  </NeutralContainer>
</template>
