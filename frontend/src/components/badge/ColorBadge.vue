/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useContrastingText} from '@/composables/useContrastingText'

/**
 * A badge in a colour somebody chose.
 *
 * <p>The text colour is read back from what the browser actually painted, so a station that picks a
 * pale yellow gets dark letters and one that picks navy gets light ones. Without a colour the badge
 * falls back to the primary tint, which is the theme's to decide and changes with it.
 */
const props = defineProps<{
    color?: string | null
}>()

const el = ref<HTMLElement | null>(null)
const textColor = useContrastingText(el, () => props.color)
</script>

<template>
    <span
        ref="el"
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[0.8rem] font-semibold"
        :class="!color ? 'bg-primary/70' : ''"
        :style="{
            ...(color ? {backgroundColor: color} : {}),
            ...(textColor ? {color: textColor} : {}),
        }"
    >
        <slot/>
    </span>
</template>
