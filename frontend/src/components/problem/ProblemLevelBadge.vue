/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'

/** The severity a problem carries, in the colours the error log scans by. */
const props = defineProps<{level: string}>()

const LEVEL_CLASSES: Record<string, string> = {
    ERROR: 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400',
    REFUSAL: 'bg-sky-100 dark:bg-sky-900/30 text-sky-700 dark:text-sky-400',
}

const levelClass = computed(() => LEVEL_CLASSES[props.level]
    ?? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400')
</script>

<template>
  <span :class="levelClass" class="text-xs font-mono px-1.5 py-0.5 rounded">{{ level }}</span>
</template>
