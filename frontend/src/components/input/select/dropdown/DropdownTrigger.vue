/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {BORDERED_INPUT_CLASSES} from '../../inputClasses'

/**
 * The button a dropdown opens from where it fills its field: it looks like the field's other inputs,
 * reads the current choice from its slot and points down, or up while the list is open.
 */
defineProps<{
  open?: boolean
  disabled?: boolean
}>()
</script>

<template>
  <button type="button" aria-haspopup="listbox" :disabled="disabled"
          :class="[BORDERED_INPUT_CLASSES, 'flex w-full min-w-0 items-center gap-2 text-left']">
    <span class="min-w-0 flex-1 truncate"><slot/></span>
    <font-awesome-icon :icon="['fas', 'chevron-down']"
                       :class="['h-3 w-3 shrink-0 text-(--text-muted) transition-transform duration-150', open ? 'rotate-180' : '']"/>
  </button>
</template>
