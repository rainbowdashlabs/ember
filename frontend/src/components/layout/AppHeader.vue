/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()

/**
 * The bar across the top of a page, carrying what the page is called.
 *
 * <p>The heading is shown and hidden rather than added and removed, because on a page the server
 * renders the two ends do not agree on whether it is there. The heading belongs to the page, and a
 * page's own setup finishes after the frame around it has been drawn, so the server sends a header
 * without one and the browser hydrates a header with one. Removing the element makes that a
 * disagreement about the shape of the document, which the browser settles by rebuilding the part it
 * disagrees about, and that rebuilding was dropping entries out of lists further down the page.
 * Kept in place, the same disagreement is only a word to fill in.
 */
defineProps<{
  title?: string
  subtitle?: string
}>()

defineEmits<{
  toggleSidebar: []
}>()
</script>

<template>
  <header class="sticky top-0 z-20 bg-(--bg) flex min-h-14 items-center border-b border-bg-light-accent dark:border-bg-dark-accent px-4 lg:pl-8 py-2 sm:py-0 gap-4">
    <IconButton
        :icon="['fas', 'bars']"
        :label="t('sidebar.openMenu')"
        data-onboarding="nav.open"
        class="text-[var(--text)] hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent lg:hidden"
        @click="$emit('toggleSidebar')"
    >
      <font-awesome-icon :icon="['fas', 'bars']" class="h-5 w-5"/>
    </IconButton>

    <div v-show="title" class="flex flex-col justify-center min-w-0">
      <span class="text-base font-semibold truncate">{{ title }}</span>
      <span v-show="subtitle" class="text-xs text-[var(--text-muted)] line-clamp-2 sm:truncate">{{ subtitle }}</span>
    </div>

    <div class="flex-1"/>

    <div class="flex items-center gap-2">
      <slot/>
    </div>
  </header>
</template>
