/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {apiUrl} from '@/util/apiUrl'
import {FailureKind, type Failure} from '@/util/failure'

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

/**
 * What stands where the document should.
 *
 * <p>Whoever reads an imprint or a privacy policy is very often not a member of anything here, so the
 * general "that did not work" left them with no idea whether they had the wrong address, whether the
 * document had been withdrawn, or whether something was broken. It is always the last of those, and
 * there is nobody they could usefully tell, so it says so and asks nothing of them.
 */
const unavailable = computed<Failure>(() => ({
    kind: FailureKind.SERVER_FAULT,
    message: t('legal.unavailable'),
    guidance: t('legal.unavailableGuidance'),
    reportable: false,
}))
</script>

<template>
    <div class="flex justify-center px-4 py-12">
        <NeutralContainer class="w-full max-w-3xl">
            <Spinner v-if="status === 'pending'" size="lg"/>
            <div v-else-if="data?.html" class="legal-content" v-html="data.html"/>
            <FailureAlert v-else :failure="unavailable"/>
        </NeutralContainer>
    </div>
</template>
