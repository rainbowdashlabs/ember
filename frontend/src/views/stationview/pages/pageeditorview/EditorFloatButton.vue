/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import IconButton from '@/components/button/IconButton.vue'

defineProps<{
    icon: string | string[]
    label: string
}>()

defineEmits<{
    click: [event: MouseEvent]
    mousedown: [event: MouseEvent]
}>()
</script>

<template>
    <IconButton
        :icon="icon"
        :label="label"
        class="rounded-full bg-(--bg) border border-(--border) text-(--text-muted) hover:text-primary hover:border-primary shadow-sm !p-1 text-xs"
        @click="$emit('click', $event)"
        @mousedown="$emit('mousedown', $event)"
    />
</template>
