/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PublicDiscoveryView from '~/views/public/PublicDiscoveryView.vue'
import {useCanonical} from '~/composables/useCanonical'

definePageMeta({
  layout: 'default',
  name: 'public-discovery',
})

useCanonical('/discovery')

const {t} = useI18n()

useHead({
  title: t('pageHead.discovery.title'),
  meta: [
    {name: 'description', content: t('pageHead.discovery.description')},
    {property: 'og:title', content: t('pageHead.discovery.socialTitle')},
    {property: 'og:description', content: t('pageHead.discovery.description')},
    {property: 'og:type', content: 'website'},
    {name: 'twitter:card', content: 'summary'},
    {name: 'twitter:title', content: t('pageHead.discovery.socialTitle')},
    {name: 'twitter:description', content: t('pageHead.discovery.description')},
  ],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
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
