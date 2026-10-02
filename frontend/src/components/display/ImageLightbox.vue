/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {DialogContent, DialogOverlay, DialogPortal, DialogRoot, DialogTitle} from 'reka-ui'
import IconButton from '@/components/button/IconButton.vue'
import {useDialogOverlay} from '@/components/feedback/dialogLayers'

/**
 * A picture shown as large as the screen allows, over everything else, whole and uncropped.
 *
 * The picture is drawn at a fixed share of the screen's height rather than at its own size, so a
 * small one grows too: a picture that opens no larger than it already was has not been enlarged.
 *
 * It is a dialog: it closes on its close button, on a press beside the picture and on Escape, holds
 * the focus while it is open and gives it back afterwards. The default slot takes the picture's
 * place for a file that is shown some other way, a document or a table.
 *
 * Where it is one of several, `position` says which, and the arrows and the arrow keys ask for the
 * one before or after it. `downloadable` offers the file itself as well.
 */
const props = defineProps<{
    src?: string | null
    alt?: string | null
    caption?: string | null
    position?: { index: number, count: number } | null
    downloadable?: boolean
}>()

const emit = defineEmits<{
    previous: []
    next: []
    download: []
}>()

const open = defineModel<boolean>('open', {required: true})

const {t} = useI18n()

const {overlay, layer, keepOpenUnlessOverlay} = useDialogOverlay(open)

const hasPrevious = computed(() => !!props.position && props.position.index > 0)
const hasNext = computed(() => !!props.position && props.position.index < props.position.count - 1)

function onKeydown(event: KeyboardEvent) {
    if (event.key === 'ArrowLeft' && hasPrevious.value) emit('previous')
    else if (event.key === 'ArrowRight' && hasNext.value) emit('next')
}
</script>

<template>
    <DialogRoot v-model:open="open">
        <DialogPortal>
            <DialogOverlay ref="overlay" :style="{zIndex: layer}"
                           class="fixed inset-0 flex flex-col items-center justify-center bg-black/85 p-4 data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out">
                <DialogContent :aria-describedby="undefined" class="flex max-w-full flex-col items-center gap-3 outline-none"
                               @keydown="onKeydown" @pointer-down-outside="keepOpenUnlessOverlay">
                    <DialogTitle class="sr-only">{{ alt || t('common.enlargeImage') }}</DialogTitle>
                    <div class="fixed top-3 right-3 flex items-center gap-1 text-white">
                        <span v-if="position" class="px-2 text-sm">{{ position.index + 1 }}/{{ position.count }}</span>
                        <IconButton v-if="downloadable" :icon="['fas', 'download']" :label="t('common.download')" @click="emit('download')"/>
                        <IconButton :icon="['fas', 'xmark']" :label="t('common.close')" @click="open = false"/>
                    </div>
                    <IconButton v-if="hasPrevious" :icon="['fas', 'chevron-left']" :label="t('common.previous')"
                                class="fixed left-2 top-1/2 -translate-y-1/2 rounded-full bg-black/40 p-3 text-2xl text-white hover:bg-black/60"
                                @click="emit('previous')"/>
                    <IconButton v-if="hasNext" :icon="['fas', 'chevron-right']" :label="t('common.next')"
                                class="fixed right-2 top-1/2 -translate-y-1/2 rounded-full bg-black/40 p-3 text-2xl text-white hover:bg-black/60"
                                @click="emit('next')"/>
                    <slot>
                        <img v-if="src" :src="src" :alt="alt ?? ''" class="h-[80vh] w-auto max-w-full object-contain rounded"/>
                    </slot>
                    <p v-if="caption" class="max-w-prose text-center text-sm text-white/80">{{ caption }}</p>
                </DialogContent>
            </DialogOverlay>
        </DialogPortal>
    </DialogRoot>
</template>
