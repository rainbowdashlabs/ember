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
import {InventoryTypes, type InventorySummary} from '@/api/inventory'

const props = defineProps<{
  inv: InventorySummary
}>()

const emit = defineEmits<{
  open: [inv: InventorySummary]
  edit: [inv: InventorySummary]
  remove: [inv: InventorySummary]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer data-testid="inventory-card" clickable @click="emit('open', props.inv)">
    <div class="flex items-center justify-between">
      <div>
        <span class="font-medium">{{ props.inv.name }}</span>
        <MutedText class="ml-2">{{ t('inventory.manage.type.' + (props.inv.inventoryType ?? InventoryTypes.INTERNAL)) }}</MutedText>
        <span v-if="props.inv.hasSizes" class="ml-2 text-xs text-secondary-accent dark:text-secondary">{{ t('inventory.manage.withSizes') }}</span>
      </div>
      <div class="flex items-center gap-2" @click.stop>
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
