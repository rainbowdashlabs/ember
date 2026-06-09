/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import * as publicKb from '@/api/publicKb'
import type {PublicStationInfo} from '@/api/publicKb'
import type {KbFile} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()

const stationUid = computed(() => route.params.stationUid as string)
const fileId = computed(() => Number(route.params.id))

const loading = ref(true)
const error = ref('')
const stationInfo = ref<PublicStationInfo | null>(null)
const file = ref<KbFile | null>(null)

// Markdown
const renderedHtml = ref('')

// Text
const textContent = ref('')

function extractYoutubeId(url: string): string | null {
    const patterns = [
        /(?:youtube\.com\/watch\?v=)([a-zA-Z0-9_-]{11})/,
        /(?:youtu\.be\/)([a-zA-Z0-9_-]{11})/,
        /(?:youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/,
    ]
    for (const pattern of patterns) {
        const match = url.match(pattern)
        if (match) return match[1]
    }
    return null
}

const youtubeEmbedUrl = computed(() => {
    if (!file.value?.youtubeUrl) return null
    const id = extractYoutubeId(file.value.youtubeUrl)
    return id ? `https://www.youtube-nocookie.com/embed/${id}` : null
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

async function loadData() {
    loading.value = true
    error.value = ''
    try {
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
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

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

onMounted(() => {
    loadData()
})
</script>

<template>
    <ViewContent>
        <div class="space-y-6">
            <!-- Station name header -->


            <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
            <Spinner v-if="loading"/>

            <template v-else-if="file">
                <!-- Header -->
                <div class="flex flex-wrap items-center gap-2 mb-4">
                    <SecondaryButton @click="goBack">
                        <font-awesome-icon :icon="['fas', 'chevron-left']"/>
                        {{ t('publicKb.backToBrowse') }}
                    </SecondaryButton>

                    <SectionHeader class="text-xl font-bold flex-1">{{ file.name }}</SectionHeader>
                </div>

                <!-- Description -->
                <MutedText tag="p" size="sm" v-if="file.description">
                    {{ file.description }}
                </MutedText>

                <!-- Last edited -->
                <p v-if="file.updatedAt" class="text-xs text-[var(--text-muted)] mb-4">
                    {{ new Date(file.updatedAt).toLocaleString('de-DE') }}
                </p>

                <!-- MARKDOWN -->
                <template v-if="file.fileType === KbFileType.MARKDOWN">
                    <NeutralContainer>
                        <div v-if="renderedHtml" class="markdown-content" v-html="renderedHtml"/>
                        <p v-else class="text-[var(--text-muted)]">{{ t('publicKb.noContent') }}</p>
                    </NeutralContainer>
                </template>

                <!-- TEXT -->
                <template v-else-if="file.fileType === KbFileType.TEXT">
                    <NeutralContainer>
                        <pre v-if="textContent" class="whitespace-pre-wrap text-sm">{{ textContent }}</pre>
                        <p v-else class="text-[var(--text-muted)]">{{ t('publicKb.noContent') }}</p>
                    </NeutralContainer>
                </template>

                <!-- PDF -->
                <template v-else-if="file.fileType === KbFileType.PDF">
                    <NeutralContainer class="p-0">
                        <iframe
                            :src="contentUrl"
                            class="w-full min-h-[80vh] rounded"
                            :title="file.name"
                        />
                    </NeutralContainer>
                </template>

                <!-- IMAGE -->
                <template v-else-if="file.fileType === KbFileType.IMAGE">
                    <NeutralContainer class="flex justify-center">
                        <img
                            :src="contentUrl"
                            :alt="file.name"
                            class="max-w-full max-h-[80vh] rounded"
                        />
                    </NeutralContainer>
                </template>

                <!-- YOUTUBE -->
                <template v-else-if="file.fileType === KbFileType.YOUTUBE">
                    <NeutralContainer v-if="youtubeEmbedUrl" class="p-0">
                        <div class="relative pb-[56.25%] h-0">
                            <iframe
                                :src="youtubeEmbedUrl"
                                class="absolute top-0 left-0 w-full h-full rounded"
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                                allowfullscreen
                                :title="file.name"
                            />
                        </div>
                    </NeutralContainer>
                    <Alert v-else variant="error">{{ t('publicKb.noContent') }}</Alert>
                </template>

                <!-- LINK -->
                <template v-else-if="file.fileType === KbFileType.LINK">
                    <NeutralContainer class="space-y-4">
                        <div class="flex items-center gap-2">
                            <font-awesome-icon :icon="['fas', 'link']" class="text-[var(--secondary)]"/>
                            <a
                                :href="file.linkUrl ?? ''"
                                target="_blank"
                                rel="noopener noreferrer"
                                class="text-[var(--primary)] hover:underline break-all"
                            >
                                {{ file.linkUrl }}
                            </a>
                        </div>
                        <a :href="file.linkUrl ?? ''" target="_blank" rel="noopener noreferrer" class="inline-block">
                            <PrimaryButton>
                                <font-awesome-icon :icon="['fas', 'arrow-right']"/>
                                {{ t('publicKb.openLink') }}
                            </PrimaryButton>
                        </a>
                    </NeutralContainer>
                </template>

                <!-- PRESENTATION -->
                <template v-else-if="file.fileType === KbFileType.PRESENTATION && file.conversionStatus === 'SUCCESS'">
                    <NeutralContainer class="p-0">
                        <iframe :src="contentUrl" class="w-full min-h-[80vh] rounded" :title="file.name"/>
                    </NeutralContainer>
                </template>

                <!-- OTHER -->
                <template v-else>
                    <NeutralContainer class="text-center py-8">
                        <font-awesome-icon :icon="['fas', 'file']" class="text-4xl text-[var(--text-muted)] mb-4"/>
                        <p class="mb-4">{{ file.name }}</p>
                        <a :href="contentUrl" download class="inline-block">
                            <PrimaryButton>
                                <font-awesome-icon :icon="['fas', 'download']"/>
                                {{ t('publicKb.download') }}
                            </PrimaryButton>
                        </a>
                    </NeutralContainer>
                </template>
            </template>
        </div>
    </ViewContent>
</template>
