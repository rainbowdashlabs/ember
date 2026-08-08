/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Help article for the inventory module's "my inventory" route. It is the same screen as
 * the one under the profile section, so this renders {@link InventoryHelp} — a separate
 * component file only so both app routes own a help entry instead of sharing one and
 * tripping the duplicate-component check.
 */
import InventoryHelp from './InventoryHelp.vue'
</script>

<template>
  <InventoryHelp/>
</template>
