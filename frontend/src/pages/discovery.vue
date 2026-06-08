/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PublicDiscoveryView from '~/views/public/PublicDiscoveryView'

definePageMeta({
  layout: 'default',
  name: 'public-discovery',
})

useHead({
  title: 'Stationen entdecken',
  meta: [
    {name: 'description', content: 'Finde Jugendfeuerwehren in deiner Nähe und entdecke ihre öffentlichen Stationen.'},
  ],
})
</script>

<template>
  <PublicDiscoveryView />
</template>
