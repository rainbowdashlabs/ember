/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onBeforeUnmount, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

/**
 * A picture shown as large as the screen allows, over everything else, whole and uncropped.
 *
 * The picture is drawn at a fixed share of the screen's height rather than at its own size, so a
 * small one grows too: a picture that opens no larger than it already was has not been enlarged.
 *
 * Closes on its close button, on a click beside the picture and on Escape. The key listener only
 * exists while the picture is open, so a page full of closed ones listens for nothing.
 */
const props = defineProps<{
    src: string
    alt?: string | null
    caption?: string | null
}>()

const open = defineModel<boolean>('open', {required: true})

const {t} = useI18n()

function close() {
    open.value = false
}

function closeOnEscape(event: KeyboardEvent) {
    if (event.key === 'Escape') close()
}

watch(open, isOpen => {
    if (isOpen) window.addEventListener('keydown', closeOnEscape)
    else window.removeEventListener('keydown', closeOnEscape)
})

onBeforeUnmount(() => window.removeEventListener('keydown', closeOnEscape))
</script>

<template>
    <Teleport to="body">
        <div v-if="open"
             class="fixed inset-0 z-[100] flex flex-col items-center justify-center gap-3 bg-black/85 p-4"
             role="dialog" aria-modal="true" :aria-label="props.alt || t('common.enlargeImage')"
             @click.self="close">
            <IconButton :icon="['fas', 'xmark']" :label="t('common.close')"
                        class="absolute top-3 right-3 text-white" @click="close"/>
            <img :src="src" :alt="alt ?? ''" class="h-[80vh] w-auto max-w-full object-contain rounded"/>
            <p v-if="caption" class="max-w-prose text-center text-sm text-white/80">{{ caption }}</p>
        </div>
    </Teleport>
</template>
