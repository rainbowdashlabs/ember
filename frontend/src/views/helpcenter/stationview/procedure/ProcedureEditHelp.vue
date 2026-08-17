/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Help article for editing a procedure. Creating and editing are the same screen, so this
 * renders {@link ProcedureCreateHelp} - a separate component file only so both routes own a
 * help entry instead of sharing one and tripping the duplicate-component check.
 */
import ProcedureCreateHelp from './ProcedureCreateHelp.vue'
</script>

<template>
  <ProcedureCreateHelp/>
</template>
