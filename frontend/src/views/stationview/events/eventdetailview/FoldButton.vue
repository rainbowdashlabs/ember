/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BareButton from '@/components/button/BareButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'

/**
 * The header of a part of the page that folds: its label beside a chevron that says whether the part is
 * open. Pressing anywhere on it opens or closes the part.
 */
const open = defineModel<boolean>({required: true})
</script>

<template>
  <BareButton class="flex w-full items-center gap-2 text-left" :aria-expanded="open" @click="open = !open">
    <AppIcon :icon="['fas', open ? 'chevron-down' : 'chevron-right']" class="h-3 w-3 shrink-0 text-(--text-muted)"/>
    <slot/>
  </BareButton>
</template>
