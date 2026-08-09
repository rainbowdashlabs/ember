/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import * as publicKb from '@/api/publicKb'
import type {KbArticleConfig} from '@/api/pageManage'
import MutedText from '@/components/typography/MutedText.vue'

const props = defineProps<{
    config: KbArticleConfig
    stationUid?: string
}>()

const resolvedTitle = ref<string | null>(null)
const resolvedHref = ref<string | null>(null)

async function resolve() {
    if (!props.stationUid || !props.config.articleId) return
    try {
        const file = await publicKb.getFile(props.stationUid, props.config.articleId)
        resolvedTitle.value = file.name ?? null
        resolvedHref.value = `/public/station/${props.stationUid}/knowledge/file/${file.id}`
    } catch { /* fall back */ }
}

onMounted(resolve)
watch(() => [props.stationUid, props.config.articleId], resolve, {immediate: false})

const href = computed(() => resolvedHref.value || (props.stationUid ? `/public/station/${props.stationUid}/knowledge` : '#'))
const title = computed(() => resolvedTitle.value || 'Wiki-Artikel')
</script>

<template>
    <a :href="href" class="flex items-center gap-3 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-4 py-3">
        <font-awesome-icon :icon="['fas', 'book']" class="text-xl text-primary"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ title }}</p>
            <MutedText tag="p" class="truncate">Wissensdatenbank</MutedText>
        </div>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)"/>
    </a>
</template>
