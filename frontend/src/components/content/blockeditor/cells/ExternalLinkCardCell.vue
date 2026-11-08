/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {ExternalLinkCardConfig} from '@/api/generated/schema'
import {markdownSnippet} from '@/util/markdown'

/**
 * A card that is one link as a whole. The description is shown as plain text, since links written
 * into it would sit inside the card's own link.
 */
const props = defineProps<{
    config: ExternalLinkCardConfig
}>()

const description = computed(() => markdownSnippet(props.config.description, 300))
</script>

<template>
    <a v-if="config.imageUrl && config.imageDisplay === 'ICON'" :href="config.url || '#'"
       target="_blank" rel="noopener noreferrer"
       class="flex items-stretch rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors overflow-hidden">
        <img :src="config.imageUrl" :alt="config.title ?? ''" class="w-20 h-20 object-cover shrink-0"/>
        <div class="p-3 space-y-1 min-w-0 flex-1">
            <p class="font-semibold truncate">{{ config.title || config.url }}</p>
            <p v-if="description" class="text-sm text-(--text-muted) line-clamp-2">{{ description }}</p>
            <p v-if="config.url" class="text-xs text-primary truncate">{{ config.url }} ↗</p>
        </div>
    </a>
    <a v-else :href="config.url || '#'" target="_blank" rel="noopener noreferrer"
       class="block rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors overflow-hidden">
        <img v-if="config.imageUrl" :src="config.imageUrl" :alt="config.title ?? ''" class="w-full h-32 object-cover"/>
        <div class="p-3 space-y-1">
            <p class="font-semibold">{{ config.title || config.url }}</p>
            <p v-if="description" class="text-sm text-(--text-muted)">{{ description }}</p>
            <p v-if="config.url" class="text-xs text-primary truncate">{{ config.url }} ↗</p>
        </div>
    </a>
</template>
