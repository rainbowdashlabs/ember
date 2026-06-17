/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {SpacerConfig} from '@/api/pageManage'

defineProps<{
    config: SpacerConfig
}>()
</script>

<template>
    <div :style="{height: `${config.heightPx ?? 32}px`}"/>
</template>
