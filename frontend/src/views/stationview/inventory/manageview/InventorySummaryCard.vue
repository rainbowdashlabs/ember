/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InventoryBadges from '@/components/inventory/InventoryBadges.vue'
import LendingShareButton from '@/components/lending/LendingShareButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {isLendableInventory, type InventorySummary} from '@/api/inventory'
import type {ShareSetting} from '@/api/lending'
import {useLendingShare} from '@/composables/useLendingShare'

const props = defineProps<{
  inv: InventorySummary
  /** What this inventory is currently offered as, where the reader may see and change that. */
  share?: ShareSetting | null
}>()

const emit = defineEmits<{
  open: [inv: InventorySummary]
  edit: [inv: InventorySummary]
  remove: [inv: InventorySummary]
  shareChanged: []
}>()

const {t} = useI18n()
const {visible: sharing, stateLabel} = useLendingShare(() => isLendableInventory(props.inv.inventoryType))
</script>

<template>
  <NeutralContainer data-testid="inventory-card" clickable @click="emit('open', props.inv)">
    <div class="flex items-center justify-between">
      <div class="min-w-0 space-y-1">
        <span class="font-medium">{{ props.inv.name }}</span>
        <InventoryBadges
            :inventory-type="props.inv.inventoryType"
            :has-sizes="props.inv.hasSizes"
            :homogeneous="props.inv.homogeneous"
            :art-count="props.inv.artCount"
        >
          <SecondaryBadge v-if="sharing" data-testid="inventory-badge-share">
            {{ stateLabel(props.share) }}
          </SecondaryBadge>
        </InventoryBadges>
      </div>
      <div class="flex items-center gap-2" @click.stop>
        <LendingShareButton
            :target-id="props.inv.id"
            :target-name="props.inv.name ?? ''"
            :lendable="isLendableInventory(props.inv.inventoryType)"
            target="inventory"
            @saved="emit('shareChanged')"
        />
        <EditButton @click="emit('edit', props.inv)" />
        <DeleteButton @click="emit('remove', props.inv)" />
      </div>
    </div>
    <MutedText tag="div" class="mt-1">
      {{ t('inventory.manage.itemCount', {count: props.inv.itemCount}) }}
      <template v-if="props.inv.lostCount > 0">
        &middot; <span class="text-error">{{ t('inventory.manage.lostCount', {count: props.inv.lostCount}) }}</span>
      </template>
      <template v-if="props.inv.lentOutCount > 0">
        &middot; <span class="text-secondary-accent dark:text-secondary">{{ t('inventory.manage.lentOutCount', {count: props.inv.lentOutCount}) }}</span>
      </template>
      <template v-if="props.inv.procurementCount > 0">
        &middot; <span class="text-info-accent dark:text-info">{{ t('inventory.manage.procurementCount', {count: props.inv.procurementCount}) }}</span>
      </template>
    </MutedText>
  </NeutralContainer>
</template>
