/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'

const content = defineModel<string>('content', {required: true})
</script>

<template>
    <div class="cell-markdown-editor flex-1 flex flex-col">
        <MarkdownEditor v-model="content"/>
    </div>
</template>

<style scoped>
.cell-markdown-editor :deep(> div) {
    display: flex;
    flex-direction: column;
    flex: 1;
}
.cell-markdown-editor :deep(.markdown-editor-content) {
    flex: 1;
    min-height: 120px;
    display: flex;
    flex-direction: column;
    overflow: auto;
}
.cell-markdown-editor :deep(.markdown-editor-content .tiptap) {
    flex: 1;
    min-height: 100px;
}
</style>
