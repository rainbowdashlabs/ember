/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ColorInput from '@/components/input/ColorInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'

defineProps<{
    color: string
    label: string
    upDisabled?: boolean
    downDisabled?: boolean
}>()
</script>

<template>
    <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
        <ColorInput :model-value="color" />
        <TextInput :model-value="label" class="flex-1" />
        <IconButton :icon="['fas', 'chevron-up']" label="Move up" :disabled="upDisabled" />
        <IconButton :icon="['fas', 'chevron-down']" label="Move down" :disabled="downDisabled" />
        <IconButton :icon="['fas', 'xmark']" label="Remove" />
    </div>
</template>
