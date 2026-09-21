/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AuthImage from '@/components/display/AuthImage.vue'
import type {KbItem} from './useKbItems'

/**
 * The top of a wiki tile: the picture of the file where it has one, and the icon of what it is
 * everywhere else.
 *
 * <p>Every tile gets the same area whether it has a picture or not, so a grid mixing photographs,
 * sheets and folders keeps its rows level instead of stepping up and down with whatever each entry
 * happened to be. A picture the server cannot make answers 404, and the icon takes its place.
 */
defineProps<{
    item: KbItem
}>()
</script>

<template>
    <div class="flex aspect-[4/3] w-full items-center justify-center overflow-hidden rounded-theme bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
        <AuthImage v-if="item.picture" :src="item.picture" :alt="item.title" class="h-full w-full object-cover">
            <template #error>
                <font-awesome-icon :icon="item.icon" class="text-4xl" :class="item.iconClass"/>
            </template>
        </AuthImage>
        <AuthImage v-else-if="item.imageUrl" :src="item.imageUrl" :alt="item.title" class="h-16 w-16 rounded object-cover">
            <template #error>
                <font-awesome-icon :icon="item.icon" class="text-4xl" :class="item.iconClass"/>
            </template>
        </AuthImage>
        <font-awesome-icon v-else :icon="item.icon" class="text-4xl" :class="item.iconClass"/>
    </div>
</template>
