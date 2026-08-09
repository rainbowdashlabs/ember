/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import KbEditFolderModal from './KbEditFolderModal.vue'
import KbEditFileModal from './KbEditFileModal.vue'
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
    <KbEditFolderModal
        v-model:show="showFolder"
        :folder="folder"
        @saved="emit('saved')"
    />

    <KbEditFileModal
        v-model:show="showFile"
        :file="file"
        @saved="emit('saved')"
    />
</template>
