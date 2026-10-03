/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import type {Content} from '@tiptap/vue-3'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import {useBlockEditorOptions} from '@/composables/useBlockEditorOptions'

/**
 * The text of a markdown cell. The stylesheet lets the editor shrink inside the dialog's flex layout,
 * so the written area scrolls on its own instead of growing and pushing the dialog open.
 *
 * <p>Where the surrounding editor names tokens and tools, such as a letter's placeholders and their
 * picker, the text shows the tokens as chips and the tools stand above it, inserting at the cursor.
 */
const content = defineModel<string>('content', {required: true})

const options = useBlockEditorOptions()
const editor = ref<InstanceType<typeof MarkdownEditor> | null>(null)

function insert(inserted: Content) {
    editor.value?.insert(inserted)
}
</script>

<template>
    <div class="cell-markdown-editor flex-1 flex flex-col gap-3">
        <component :is="options.markdownTools" v-if="options.markdownTools" :insert="insert"/>
        <MarkdownEditor ref="editor" v-model="content" :tokens="options.tokens"/>
    </div>
</template>

<style scoped>
.cell-markdown-editor :deep(> div:last-child) {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
}
.cell-markdown-editor :deep(.markdown-editor-content) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: auto;
}
.cell-markdown-editor :deep(.markdown-editor-content .tiptap) {
    flex: 1;
    min-height: 100px;
}
</style>
