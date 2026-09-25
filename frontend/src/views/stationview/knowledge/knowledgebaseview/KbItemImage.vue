/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import AuthImage from '@/components/display/AuthImage.vue'

/**
 * One picture of a wiki entry, fetched with the reader's session where the address asks for it and
 * as a plain image where it is open to anybody.
 *
 * <p>The public wiki has no session to fetch with, and its pages are rendered on the server, where a
 * picture pulled in afterwards would reach neither a reader without scripts nor a search engine. The
 * error slot stands in for whatever did not arrive, which is the icon of what the entry is.
 */
const props = defineProps<{
    src: string
    alt: string
    /** The address needs no credentials, so the browser fetches it itself. */
    plain?: boolean
}>()

const failed = ref(false)

watch(() => props.src, () => {
    failed.value = false
})
</script>

<template>
    <img v-if="plain && !failed" :src="src" :alt="alt" v-bind="$attrs" @error="failed = true"/>
    <AuthImage v-else-if="!plain" :src="src" :alt="alt" v-bind="$attrs">
        <template #error>
            <slot name="error"/>
        </template>
    </AuthImage>
    <slot v-else name="error"/>
</template>
