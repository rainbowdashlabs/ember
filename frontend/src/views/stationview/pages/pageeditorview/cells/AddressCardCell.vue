/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AddressCardConfig} from '@/api/pageManage'

defineProps<{
    config: AddressCardConfig
}>()
</script>

<template>
    <div class="rounded-theme border border-(--border) p-4 flex gap-3 items-start">
        <font-awesome-icon :icon="['fas', 'location-dot']" class="text-2xl text-primary mt-1"/>
        <div class="flex-1 min-w-0 space-y-1">
            <p v-if="config.label" class="font-semibold">{{ config.label }}</p>
            <p v-if="config.addressLine" class="text-sm">{{ config.addressLine }}</p>
            <p v-if="config.postalCode || config.city" class="text-sm">{{ [config.postalCode, config.city].filter(Boolean).join(' ') }}</p>
            <p v-if="config.country" class="text-sm text-(--text-muted)">{{ config.country }}</p>
            <a v-if="config.mapUrl" :href="config.mapUrl" target="_blank" rel="noopener noreferrer" class="inline-block text-xs text-primary hover:underline mt-1">In Karten öffnen ↗</a>
        </div>
    </div>
</template>
