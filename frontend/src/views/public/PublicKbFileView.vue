/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, inject} from 'vue'
import {useRouter, useRoute} from 'vue-router'
import type {Ref} from 'vue'
import type {PublicStationInfo as DiscoveryStationInfo} from '@/api/discovery'
import Spinner from '@/components/feedback/Spinner.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import * as publicKb from '@/api/publicKb'
import type {PublicStationInfo} from '@/api/publicKb'
import type {KbFile} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import KbFileHeader from '@/views/public/publickbfileview/KbFileHeader.vue'
import KbFileRenderer from '@/views/public/publickbfileview/KbFileRenderer.vue'
import {youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const router = useRouter()
const route = useRoute()

const publicStation = inject<Ref<DiscoveryStationInfo | null>>('publicStation')
const stationUid = computed(() => publicStation?.value?.stationUid ?? route.params.stationUid as string)
const fileId = computed(() => Number(route.params.id))

const stationInfo = ref<PublicStationInfo | null>(null)
const file = ref<KbFile | null>(null)

const renderedHtml = ref('')
const textContent = ref('')

const youtubeEmbedUrl = computed(() => {
    if (!file.value?.youtubeUrl) return null
    return toYoutubeEmbedUrl(file.value.youtubeUrl)
})

const contentUrl = computed(() => {
    if (!file.value) return ''
    return publicKb.fileContentUrl(stationUid.value, file.value.id)
})

function rewriteImageUrls(html: string): string {
    return html.replace(
        /src="([^"]*\/kb\/images\/([^"?]+)[^"]*)"/g,
        (_match, _url, imageId) => {
            return `src="${publicKb.kbImageUrl(stationUid.value, imageId)}"`
        },
    )
}

const {loading, error} = useAsyncLoader(async () => {
    const [info, fileRes] = await Promise.all([
        publicKb.getStationInfo(stationUid.value),
        publicKb.getFile(stationUid.value, fileId.value),
    ])
    stationInfo.value = info
    file.value = fileRes

    if (fileRes.fileType === KbFileType.MARKDOWN) {
        const md = await publicKb.getMarkdownHtml(stationUid.value, fileRes.id)
        renderedHtml.value = rewriteImageUrls(md.html)
    } else if (fileRes.fileType === KbFileType.TEXT) {
        const res = await fetch(publicKb.fileContentUrl(stationUid.value, fileRes.id))
        textContent.value = await res.text()
    }
})

function goBack() {
    if (file.value?.folderId) {
        router.push({name: 'public-kb', params: {stationUid: stationUid.value}, query: {folderId: file.value.folderId}})
    } else {
        router.push({name: 'public-kb', params: {stationUid: stationUid.value}})
    }
}

useHead(computed(() => {
    if (!file.value || !stationInfo.value) return {}
    const f = file.value
    const desc = f.description || `${f.name} — ${stationInfo.value.stationName}`
    return {
        title: `${f.name} — ${stationInfo.value.stationName}`,
        meta: [
            {name: 'description', content: desc},
        ],
        script: [
            {
                type: 'application/ld+json',
                children: JSON.stringify({
                    '@context': 'https://schema.org',
                    '@type': 'BreadcrumbList',
                    itemListElement: [
                        {'@type': 'ListItem', position: 1, name: stationInfo.value.stationName, item: `/public/station/${stationUid.value}/knowledge`},
                        {'@type': 'ListItem', position: 2, name: f.name},
                    ],
                }),
            },
        ],
    }
}))
</script>

<template>
    <ViewContent>
        <div class="space-y-6">
            <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
            <Spinner v-if="loading"/>

            <template v-else-if="file">
                <KbFileHeader :file="file" @back="goBack"/>
                <KbFileRenderer :file="file"
                                :content-url="contentUrl"
                                :youtube-embed-url="youtubeEmbedUrl"
                                :rendered-html="renderedHtml"
                                :text-content="textContent"/>
            </template>
        </div>
    </ViewContent>
</template>
