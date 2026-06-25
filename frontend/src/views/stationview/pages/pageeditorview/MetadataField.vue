/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
    label: string
}>()
</script>

<template>
    <div>
        <FormLabel>{{ label }}</FormLabel>
        <slot/>
    </div>
</template>
