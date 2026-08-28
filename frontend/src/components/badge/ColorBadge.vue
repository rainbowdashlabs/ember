/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {contrastingTextColorForRgbString} from '@/util/contrastColor'

/**
 * A badge in a colour somebody chose.
 *
 * <p>The text colour is read back from what the browser actually painted, so a station that picks a
 * pale yellow gets dark letters and one that picks navy gets light ones. Without a colour the badge
 * falls back to the primary tint.
 */
const props = defineProps<{
    color?: string | null
}>()

const el = ref<HTMLElement | null>(null)
const textColor = ref<string>('')

function updateTextColor() {
    if (!el.value) return
    const bg = getComputedStyle(el.value).backgroundColor
    const next = contrastingTextColorForRgbString(bg)
    if (next) textColor.value = next
}

onMounted(updateTextColor)
watch(() => props.color, () => requestAnimationFrame(updateTextColor))
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
