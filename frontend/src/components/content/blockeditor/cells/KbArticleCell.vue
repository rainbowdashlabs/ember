/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import * as publicKb from '@/api/publicKb'
import type {KbArticleConfig} from '@/api/generated/schema'
import MutedText from '@/components/typography/MutedText.vue'

const props = defineProps<{
    config: KbArticleConfig
    stationUid?: string
}>()

const {t} = useI18n()

const resolvedTitle = ref<string | null>(null)
const resolvedHref = ref<string | null>(null)

/** Reads the article's title and address. One that cannot be read leaves the link on the knowledge base itself. */
async function resolve() {
    const stationUid = props.stationUid
    if (!stationUid || !props.config.articleId) return
    const file = await publicKb.getFile(stationUid, props.config.articleId).catch(() => null)
    if (!file) return
    resolvedTitle.value = file.name ?? null
    resolvedHref.value = `/public/station/${stationUid}/knowledge/file/${file.id}`
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
            <MutedText tag="p" class="truncate">{{ t('publicStation.knowledgeBase') }}</MutedText>
        </div>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)"/>
    </a>
</template>
