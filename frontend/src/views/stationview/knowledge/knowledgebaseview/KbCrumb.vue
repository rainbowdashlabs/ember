/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * One step of the knowledge base's breadcrumb, which opens its folder by pointer or keyboard.
 *
 * <p>`current` marks the step the reader stands on. A folder somebody else shared cannot be opened
 * again from where the reader already is, so there the current step is `inert` as well and opens
 * nothing; this station's own steps open even when current, which reloads the folder.
 */
const props = defineProps<{
    current?: boolean
    inert?: boolean
}>()

const emit = defineEmits<{
    open: []
}>()

function open() {
    if (!props.inert) emit('open')
}
</script>

<template>
    <span
        class="hover:text-[var(--color-primary)] transition-colors"
        :class="[current ? 'font-semibold text-[var(--color-primary)]' : '', inert ? '' : 'cursor-pointer']"
        role="button"
        tabindex="0"
        :aria-current="current ? 'location' : undefined"
        :aria-disabled="inert"
        @click="open"
        @keydown.enter.prevent="open"
        @keydown.space.prevent="open"
    >
        <slot/>
    </span>
</template>
