/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import HomeView from '~/views/HomeView.vue'
import {useCanonical} from '~/composables/useCanonical'
import {useReturnToLastArea} from '~/composables/useReturnToLastArea'

useCanonical('/')
useReturnToLastArea()

useHead({
  title: 'Digitale Verwaltung für Jugendfeuerwehren',
  meta: [
    {name: 'description', content: 'Ember ist die kostenlose Open-Source-Plattform für Jugendfeuerwehren - Termine, Anwesenheit, Inventar, Wiki und mehr.'},
    {property: 'og:title', content: 'Ember - Digitale Verwaltung für Jugendfeuerwehren'},
    {property: 'og:description', content: 'Kostenlose Open-Source-Verwaltungsplattform für Jugendfeuerwehren.'},
    {property: 'og:type', content: 'website'},
    {name: 'twitter:card', content: 'summary'},
    {name: 'twitter:title', content: 'Ember - Digitale Verwaltung für Jugendfeuerwehren'},
    {name: 'twitter:description', content: 'Kostenlose Open-Source-Verwaltungsplattform für Jugendfeuerwehren.'},
  ],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: 'Ember',
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        description: 'Digitale Verwaltung für Jugendfeuerwehren',
        offers: {'@type': 'Offer', price: '0', priceCurrency: 'EUR'},
        author: {'@type': 'Organization', name: 'RainbowDashLabs'},
      }),
    },
  ],
})
</script>

<template>
  <HomeView />
</template>
