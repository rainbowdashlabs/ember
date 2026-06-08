/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ImprintView from '~/views/ImprintView'
import {useCanonical} from '~/composables/useCanonical'

definePageMeta({
  layout: 'default',
  name: 'imprint',
})

useCanonical('/imprint')

useHead({
  title: 'Impressum',
  meta: [
    {name: 'description', content: 'Impressum und rechtliche Informationen zu Ember.'},
    {property: 'og:title', content: 'Impressum — Ember'},
    {property: 'og:type', content: 'website'},
  ],
})
</script>

<template>
  <ImprintView />
</template>
