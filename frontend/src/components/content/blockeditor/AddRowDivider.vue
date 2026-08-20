/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditorFloatButton from './EditorFloatButton.vue'

defineProps<{
    hasClipboard: boolean
    clipboardType: string | null
}>()

defineEmits<{
    add: []
    paste: []
}>()

const {t} = useI18n()
</script>

<template>
    <div class="relative flex items-center justify-center py-2">
        <div class="absolute inset-x-0 top-1/2 h-px bg-(--border)"/>
        <div class="relative flex items-center gap-2">
            <EditorFloatButton
                :icon="['fas', 'plus']"
                :label="t('stationPages.editor.addRow')"
                @click="$emit('add')"
            />
            <IconButton
                v-if="hasClipboard && clipboardType === 'row'"
                :icon="['fas', 'paste']"
                :label="t('stationPages.editor.pasteRow')"
                class="rounded-full bg-(--bg) border border-(--border) text-primary hover:border-primary !p-1 text-xs shadow-sm"
                @click="$emit('paste')"
            />
        </div>
    </div>
</template>
