/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InventoryFieldsPanel from '@/components/inventory/InventoryFieldsPanel.vue'

/**
 * The fields of the inventory itself, the ones every piece in it carries.
 *
 * <p>A field belonging to one kind is written on that kind, and one belonging to a single piece on
 * that piece, so neither appears here.
 */
defineProps<{
  inventoryId: number
}>()
</script>

<template>
  <NeutralContainer>
    <InventoryFieldsPanel :inventory-id="inventoryId"/>
  </NeutralContainer>
</template>
