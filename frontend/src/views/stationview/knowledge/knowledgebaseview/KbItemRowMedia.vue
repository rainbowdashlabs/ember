/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import KbItemImage from './KbItemImage.vue'
import type {KbItem} from './useKbItems'

/**
 * The picture standing for an entry in the list, at the size one line of the list allows.
 *
 * <p>A file that carries a preview shows it and falls back to the icon of its kind where the
 * preview cannot be fetched, which is what a folder and a partner's entry show outright.
 */
defineProps<{
    item: KbItem
}>()
</script>

<template>
    <div class="w-5 flex-shrink-0 flex justify-center">
        <KbItemImage
            v-if="item.imageUrl"
            :src="item.imageUrl"
            :alt="item.title"
            :plain="item.publicImages"
            class="w-4 h-4 rounded object-cover"
        >
            <template #error>
                <font-awesome-icon :icon="item.icon" class="text-sm" :class="item.iconClass"/>
            </template>
        </KbItemImage>
        <font-awesome-icon v-else :icon="item.icon" class="text-sm" :class="item.iconClass"/>
    </div>
</template>
