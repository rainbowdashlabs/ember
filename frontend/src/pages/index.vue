/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import HomeView from '~/views/HomeView.vue'

useHead({
  title: 'Digitale Verwaltung für Jugendfeuerwehren',
  meta: [
    {name: 'description', content: 'Ember ist die kostenlose Open-Source-Plattform für Jugendfeuerwehren — Termine, Anwesenheit, Inventar, Wissensdatenbank und mehr.'},
    {property: 'og:title', content: 'Ember — Digitale Verwaltung für Jugendfeuerwehren'},
    {property: 'og:description', content: 'Kostenlose Open-Source-Verwaltungsplattform für Jugendfeuerwehren.'},
    {property: 'og:type', content: 'website'},
  ],
  script: [
    {
      type: 'application/ld+json',
      children: JSON.stringify({
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
