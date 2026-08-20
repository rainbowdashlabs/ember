/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import InstallView from '~/views/InstallView.vue'

definePageMeta({
  layout: 'default',
  name: 'install',
})

useHead({
  title: 'Ember installieren',
  meta: [
    {name: 'description', content: 'Eine Ember-Installation zusammenstellen und als ein Befehl ausführen.'},
  ],
})
</script>

<template>
  <InstallView/>
</template>
