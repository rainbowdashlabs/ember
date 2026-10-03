/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'

/**
 * A text link, led by an icon where one is given. A link within the site navigates without reloading the
 * page. An `external` one leaves the site: `rel` marks it external and sends neither an opener nor a
 * referrer along, and it opens in a new tab unless `sameTab` keeps it in this one.
 */
defineProps<{
  href: string
  icon?: string[]
  external?: boolean
  sameTab?: boolean
}>()
</script>

<template>
  <NuxtLink
      :to="href"
      :external="external || undefined"
      :rel="external ? 'external noopener noreferrer' : undefined"
      :target="external && !sameTab ? '_blank' : undefined"
      class="inline-flex items-center gap-1.5 text-[var(--link)] hover:underline"
  >
    <AppIcon v-if="icon" :icon="icon" aria-hidden="true" class="shrink-0"/>
    <slot/>
  </NuxtLink>
</template>
