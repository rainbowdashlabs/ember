/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import KbShareModal from './KbShareModal.vue'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'

const emit = defineEmits<{
    saved: []
}>()

const showFolder = ref(false)
const folder = ref<KbFolder | null>(null)
const showFile = ref(false)
const file = ref<KbFile | null>(null)

function openFolder(target: KbFolder) {
    folder.value = target
    showFolder.value = true
}

function openFile(target: KbFile) {
    file.value = target
    showFile.value = true
}

defineExpose({openFolder, openFile})
</script>

<template>
    <KbShareModal
        v-model:show="showFolder"
        :entry="folder"
        kind="folders"
        @saved="emit('saved')"
    />

    <KbShareModal
        v-model:show="showFile"
        :entry="file"
        kind="files"
        @saved="emit('saved')"
    />
</template>
