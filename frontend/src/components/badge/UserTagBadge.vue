/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {contrastingTextColorForRgbString} from '@/util/contrastColor'

/**
 * Badge for displaying a user-defined member tag. Falls back to the primary tint when no
 * explicit colour is set; with a colour the text colour is derived for proper contrast.
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
