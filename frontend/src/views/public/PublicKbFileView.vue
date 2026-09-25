/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {usePublicFailure} from '@/composables/usePublicFailure'
import * as publicKb from '@/api/publicKb'
import type {PublicStationInfo} from '@/api/publicKb'
import {KbFileType, type KbFile, type MarkdownHtmlResponse} from '@/api/knowledgeBase'
import KbFileHeader from '@/views/public/publickbfileview/KbFileHeader.vue'
import KbFileRenderer from '@/views/public/publickbfileview/KbFileRenderer.vue'
import {youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'
import {apiUrl} from '@/util/apiUrl'
import {socialMeta, stationLogoImage, titleWithStation, useAbsoluteUrl} from '@/util/socialMeta'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()

const {station, stationUid, stationTimezone, stationAddress, basePath} = usePublicStationAddress()
/**
 * Which article the address names, or nothing where it names none.
 *
 * <p>A reader leaving this page takes the article out of the address before the page itself is
 * taken down, so a number read without asking is briefly not a number, and the loader went and
 * fetched it. The request was refused, and the refusal landed on whichever page the reader was
 * walking into.
 */
const fileId = computed(() => {
    const asked = Number(route.params.id)
    return Number.isFinite(asked) ? asked : null
})

const absoluteUrl = useAbsoluteUrl()

function rewriteImageUrls(html: string): string {
    return html.replace(
        /src="([^"]*\/kb\/images\/([^"?]+)[^"]*)"/g,
        (_match, _url, imageId) => {
            return `src="${publicKb.kbImageUrl(stationUid.value, imageId)}"`
        },
    )
}

/**
 * Resolved here rather than inside the loader: the address comes from the runtime configuration,
 * and reading that needs the Nuxt instance, which a loader running on its own no longer has.
 */
const apiBase = apiUrl('')

/**
 * The article's own body, where it is one this page draws rather than hands to a viewer.
 *
 * <p>Over the same address on both sides: a plain browser fetch of a relative path has no origin to
 * resolve against while the server renders, which is what kept a text article empty there.
 */
async function articleBody(file: KbFile): Promise<{renderedHtml: string, textContent: string}> {
    const base = `${apiBase}/public/kb/${stationUid.value}/files/${file.id}`
    if (file.fileType === KbFileType.MARKDOWN) {
        const rendered = await $fetch<MarkdownHtmlResponse>(`${base}/html`)
        return {renderedHtml: rewriteImageUrls(rendered.html), textContent: ''}
    }
    if (file.fileType === KbFileType.TEXT) {
        return {renderedHtml: '', textContent: await $fetch<string>(`${base}/content`, {responseType: 'text'})}
    }
    return {renderedHtml: '', textContent: ''}
}

/**
 * Fetched while the server renders rather than after mounting.
 *
 * <p>An article of a public wiki is the thing a link names, so its title, its description and its
 * text have to stand in the page the server sends. Loaded once the browser had taken over, a crawler
 * indexed the station's own card and a chat client drew the same card for every article there is.
 */
const {data: article, error: loadError, status} = await useAsyncData(
    () => `public-kb-file-${stationUid.value}-${fileId.value}`,
    async () => {
        if (fileId.value == null) return null
        const [info, file] = await Promise.all([
            $fetch<PublicStationInfo>(`${apiBase}/public/kb/${stationUid.value}/info`),
            $fetch<KbFile>(`${apiBase}/public/kb/${stationUid.value}/files/${fileId.value}`),
        ])
        return {info, file, ...(await articleBody(file))}
    },
    {watch: [stationUid, fileId]},
)

const stationInfo = computed<PublicStationInfo | null>(() => article.value?.info ?? null)
const file = computed<KbFile | null>(() => article.value?.file ?? null)
const renderedHtml = computed(() => article.value?.renderedHtml ?? '')
const textContent = computed(() => article.value?.textContent ?? '')
const loading = computed(() => status.value === 'pending')
const failure = usePublicFailure(loadError, {
    message: 'publicStation.articleGone',
    guidance: 'publicStation.articleGoneGuidance',
})

const youtubeEmbedUrl = computed(() => {
    if (!file.value?.youtubeUrl) return null
    return toYoutubeEmbedUrl(file.value.youtubeUrl)
})

const contentUrl = computed(() => {
    if (!file.value) return ''
    return publicKb.fileContentUrl(stationUid.value, file.value.id)
})

function goBack() {
    if (file.value?.folderId) {
        router.push({name: 'public-kb', params: {stationUid: stationAddress.value}, query: {folderId: file.value.folderId}})
    } else {
        router.push({name: 'public-kb', params: {stationUid: stationAddress.value}})
    }
}

useHead(computed(() => {
    if (!file.value || !stationInfo.value) return {}
    const f = file.value
    const stationName = stationInfo.value.stationName
    const title = titleWithStation(f.name, stationName)
    const desc = f.description
        || t('publicStation.meta.knowledgeBaseFile', {name: f.name, station: stationName})
    return {
        title,
        meta: socialMeta({title, description: desc, imageUrl: absoluteUrl(stationLogoImage(station.value))}),
        script: [
            {
                type: 'application/ld+json',
                innerHTML: JSON.stringify({
                    '@context': 'https://schema.org',
                    '@type': 'BreadcrumbList',
                    itemListElement: [
                        {'@type': 'ListItem', position: 1, name: stationInfo.value.stationName, item: absoluteUrl(`${basePath.value}/knowledge`)},
                        {'@type': 'ListItem', position: 2, name: f.name},
                    ],
                }),
            },
        ],
    }
}))
</script>

<template>
    <ViewContent :title="file?.name || t('pages.public-kb-file.title')">
        <div class="space-y-6">
            <FailureAlert :failure="failure" class="mb-4"/>
            <Spinner v-if="loading"/>

            <template v-else-if="file">
                <KbFileHeader :file="file" :station-uid="stationUid" :timezone="stationTimezone" @back="goBack"/>
                <KbFileRenderer :file="file"
                                :content-url="contentUrl"
                                :youtube-embed-url="youtubeEmbedUrl"
                                :rendered-html="renderedHtml"
                                :text-content="textContent"/>
            </template>
        </div>
    </ViewContent>
</template>
