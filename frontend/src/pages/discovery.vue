/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PublicDiscoveryView from '~/views/public/PublicDiscoveryView'
import {useCanonical} from '~/composables/useCanonical'

definePageMeta({
  layout: 'default',
  name: 'public-discovery',
})

useCanonical('/discovery')

useHead({
  title: 'Stationen entdecken',
  meta: [
    {name: 'description', content: 'Finde Jugendfeuerwehren in deiner Nähe und entdecke ihre öffentlichen Stationen.'},
    {property: 'og:title', content: 'Stationen entdecken — Ember'},
    {property: 'og:description', content: 'Finde Jugendfeuerwehren in deiner Nähe und entdecke ihre öffentlichen Stationen.'},
    {property: 'og:type', content: 'website'},
    {name: 'twitter:card', content: 'summary'},
    {name: 'twitter:title', content: 'Stationen entdecken — Ember'},
    {name: 'twitter:description', content: 'Finde Jugendfeuerwehren in deiner Nähe und entdecke ihre öffentlichen Stationen.'},
  ],
  script: [
    {
      type: 'application/ld+json',
      children: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: 'Ember',
        potentialAction: {
          '@type': 'SearchAction',
          target: {
            '@type': 'EntryPoint',
            urlTemplate: '/discovery?q={search_term_string}',
          },
          'query-input': 'required name=search_term_string',
        },
      }),
    },
  ],
})
</script>

<template>
  <PublicDiscoveryView />
</template>
