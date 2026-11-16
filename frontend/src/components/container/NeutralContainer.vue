/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {inject, provide} from 'vue'
import BaseContainer from './BaseContainer.vue'
import {SHEET_SECTION} from './sheet'

/**
 * The plain card most content sits in.
 *
 * <p>As a sheet it holds a whole form, and the cards directly inside it become its sections: they lose
 * their own box and are divided from each other by a line, so a form built from several cards reads as
 * one. The same cards keep their box everywhere else.
 */
const props = defineProps<{
  clickable?: boolean
  /** Holds a whole form, with the cards directly inside it as its sections. */
  sheet?: boolean
}>()

const isSection = inject(SHEET_SECTION, false)
provide(SHEET_SECTION, props.sheet ?? false)
</script>

<template>
  <div v-if="isSection" :class="clickable ? 'cursor-pointer hover:border-primary transition-colors' : ''">
    <slot/>
  </div>
  <BaseContainer
      v-else
      :class="[
        clickable ? 'cursor-pointer hover:border-primary transition-colors' : '',
        sheet ? 'divide-y divide-(--border) [&>*:not(:first-child)]:pt-5 [&>*:not(:last-child)]:pb-5' : '',
      ]"
      class="border-bg-light-accent bg-bg-light-accent/20 dark:border-bg-dark-accent dark:bg-bg-dark-accent/20">
    <slot/>
  </BaseContainer>
</template>
