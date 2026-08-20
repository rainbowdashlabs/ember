/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import * as publicPages from '@/api/publicPages'
import type {PageLinkConfig} from '@/api/pageManage'

const props = defineProps<{
    config: PageLinkConfig
    stationUid?: string
}>()

const resolvedTitle = ref<string | null>(null)
const resolvedHref = ref<string | null>(null)

async function resolve() {
    if (!props.stationUid || !props.config.pageUid) return
    try {
        const pages = await publicPages.listPublicPages(props.stationUid)
        const match = pages.find(p => p.publicUid === props.config.pageUid)
        if (match) {
            resolvedTitle.value = match.title
            resolvedHref.value = `/public/station/${props.stationUid}/page/${match.path}`
        }
    } catch { /* fall back */ }
}

onMounted(resolve)
watch(() => [props.stationUid, props.config.pageUid], resolve, {immediate: false})

const title = computed(() => resolvedTitle.value || 'Seite')
const href = computed(() => resolvedHref.value || '#')
</script>

<template>
    <a :href="href" class="flex items-center gap-3 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-4 py-3">
        <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xl text-primary"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ title }}</p>
        </div>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)"/>
    </a>
</template>
