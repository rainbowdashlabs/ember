/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import {isYoutubeUrl, youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'

const content = defineModel<string>('content', {required: true})
defineModel<Record<string, unknown>>('config', {required: true})

const {t} = useI18n()

const isYouTube = computed(() => isYoutubeUrl(content.value))

const youtubeEmbedUrl = computed(() => toYoutubeEmbedUrl(content.value) ?? content.value)
</script>

<template>
    <div class="space-y-3">
        <TextInput
            :model-value="content"
            :placeholder="t('stationPages.editor.videoUrlPlaceholder')"
            @update:model-value="content = String($event)"
        />
        <div v-if="content" class="mt-2">
            <div v-if="isYouTube" class="relative w-full" style="padding-bottom: 56.25%">
                <iframe
                    :src="youtubeEmbedUrl"
                    class="absolute inset-0 w-full h-full rounded-theme"
                    frameborder="0"
                    allowfullscreen
                />
            </div>
            <video v-else :src="content" controls class="w-full max-h-64 rounded-theme"/>
        </div>
    </div>
</template>
