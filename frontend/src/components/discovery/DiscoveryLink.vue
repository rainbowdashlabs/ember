/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * A link from a discovery tile to a station's public page or a part of it. A station of another instance
 * is left in the same tab like any other link leaving the site; `rel` marks it external and sends neither
 * an opener nor a referrer along.
 */
defineProps<{
  href: string
  external: boolean
}>()
</script>

<template>
  <NuxtLink
      :to="href"
      :external="external || undefined"
      :rel="external ? 'external noopener noreferrer' : undefined"
      class="inline-flex items-center gap-1.5 text-sm text-[var(--link)] hover:underline"
  >
    <slot/>
  </NuxtLink>
</template>
