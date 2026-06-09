/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PatchNotesView from '~/views/PatchNotesView'

definePageMeta({
  layout: 'default',
  name: 'patch-notes',
})

useHead({
  title: 'Änderungsprotokoll',
})
</script>

<template>
  <PatchNotesView />
</template>
