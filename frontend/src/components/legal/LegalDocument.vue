/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {apiUrl} from '@/util/apiUrl'

const {t} = useI18n()

const props = defineProps<{
    /** The public endpoint below `/api/v1/public` that serves the document. */
    document: 'imprint' | 'privacy-policy' | 'tos'
}>()

/**
 * Fetched during the server render rather than after mount, so the text arrives with the page.
 * These routes are public and indexed: a document that only appears once the browser has taken
 * over is a document no crawler and no link preview ever sees.
 *
 * A failure is swallowed on purpose. Rendering on the server means a backend that cannot be
 * reached would otherwise take the whole page down with it, and only when the address is opened
 * directly rather than navigated to, which is a confusing way for a public page to break.
 */
const {data, status} = await useAsyncData(
    `legal-${props.document}`,
    () => $fetch<{html: string}>(apiUrl(`/public/${props.document}`)).catch(() => ({html: ''})),
    {default: () => ({html: ''})},
)
</script>

<template>
    <div class="flex justify-center px-4 py-12">
        <NeutralContainer class="w-full max-w-3xl">
            <Spinner v-if="status === 'pending'" size="lg"/>
            <div v-else-if="data?.html" class="legal-content" v-html="data.html"/>
            <p v-else class="text-(--text-muted)">{{ t('common.error') }}</p>
        </NeutralContainer>
    </div>
</template>
