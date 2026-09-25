/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {PageLinkConfig} from '@/api/pageManage'

/**
 * A card linking to another page of the station.
 *
 * <p>The name and the address come with the page it sits on, worked out by the server while the page
 * was drawn. Looking them up here meant fetching the whole list of public pages per card and finding
 * nothing for a page reached only by its link, and in the editor's preview it meant finding nothing
 * at all.
 */
const props = defineProps<{
    config: PageLinkConfig
    stationUid?: string
}>()

const {t} = useI18n()

const title = computed(() => props.config.resolvedTitle || props.config.fallbackTitle || t('stationPages.cells.pageLinkUnresolved'))
const href = computed(() => props.config.resolvedHref ?? null)
</script>

<template>
    <component
        :is="href ? 'a' : 'div'"
        :href="href ?? undefined"
        class="flex items-center gap-3 rounded-theme border border-(--border) px-4 py-3 transition-colors"
        :class="href ? 'hover:border-primary hover:bg-primary/5' : 'opacity-60'">
        <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xl text-primary"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ title }}</p>
        </div>
        <font-awesome-icon v-if="href" :icon="['fas', 'arrow-right']" class="text-(--text-muted)"/>
    </component>
</template>
