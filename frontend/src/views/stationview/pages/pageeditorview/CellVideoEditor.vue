/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'

const props = defineProps<{
    content: string
    config: Record<string, unknown>
}>()

const emit = defineEmits<{
    'update:content': [value: string]
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()

const isYouTube = computed(() => {
    return /youtube\.com|youtu\.be/.test(props.content)
})

const youtubeEmbedUrl = computed(() => {
    const match = props.content.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/)
    if (match) return `https://www.youtube-nocookie.com/embed/${match[1]}`
    return props.content
})
</script>

<template>
    <div class="space-y-3">
        <TextInput
            :model-value="content"
            :placeholder="t('stationPages.editor.videoUrlPlaceholder')"
            @update:model-value="$emit('update:content', $event)"
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
