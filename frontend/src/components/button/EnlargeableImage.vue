/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ImageLightbox from '@/components/display/ImageLightbox.vue'

/**
 * A picture that opens large when clicked.
 *
 * The picture as its place draws it comes through the slot, cropped or scaled however that place
 * needs. `src` is what opens, which may be a larger copy than the one in the slot, and it always
 * shows whole.
 */
defineProps<{
    src: string
    alt?: string | null
    caption?: string | null
}>()

const open = ref(false)

const {t} = useI18n()
</script>

<template>
    <button type="button" class="block w-full cursor-zoom-in" :aria-label="t('common.enlargeImage')"
            :title="t('common.enlargeImage')" @click="open = true">
        <slot/>
    </button>
    <ImageLightbox v-model:open="open" :src="src" :alt="alt" :caption="caption"/>
</template>
