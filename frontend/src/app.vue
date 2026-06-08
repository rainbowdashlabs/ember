/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
const config = useRuntimeConfig()
const googleVerification = config.public.googleSiteVerification as string

useHead({
  titleTemplate: (title) => title ? `${title} — Ember` : 'Ember',
  htmlAttrs: {lang: 'de'},
  meta: [
    {name: 'robots', content: 'max-image-preview:large, max-snippet:-1, max-video-preview:-1'},
    {property: 'og:locale', content: 'de_DE'},
    {property: 'og:site_name', content: 'Ember'},
    ...(googleVerification ? [{name: 'google-site-verification', content: googleVerification}] : []),
  ],
  link: [
    {rel: 'icon', href: '/favicon/favicon.ico', sizes: 'any'},
    {rel: 'icon', href: '/favicon/favicon-32x32.png', sizes: '32x32', type: 'image/png'},
    {rel: 'icon', href: '/favicon/favicon-16x16.png', sizes: '16x16', type: 'image/png'},
    {rel: 'apple-touch-icon', href: '/favicon/apple-touch-icon.png'},
    {rel: 'manifest', href: '/favicon/site.webmanifest'},
    {rel: 'preconnect', href: '/api'},
  ],
})
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <ToastContainer />
</template>
