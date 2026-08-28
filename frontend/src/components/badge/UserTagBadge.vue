/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ColorBadge from './ColorBadge.vue'

/**
 * Badge for a member tag, in the colour the station gave the tag.
 */
defineProps<{
    color?: string | null
}>()
</script>

<template>
    <ColorBadge :color="color">
        <slot/>
    </ColorBadge>
</template>
