/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {renderMarkdown} from '@/util/markdown'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import CellMarkdownEditor from './CellMarkdownEditor.vue'

/**
 * Inline rendered Markdown that opens the full editor in a dialog when it is clicked.
 *
 * <p>What is typed is the cell's text at once, with nothing to press afterwards. A dialog holding a
 * draft has to keep a button to commit it, and that button was the thing being lost: the editor grows
 * with what is written, and the button sat under it waiting to be pressed. Writing straight through
 * means there is nothing to lose, and closing the dialog is simply closing it.
 *
 * <p>The page itself is still saved as it always was, so this decides only when the cell hears about
 * the typing, not when it reaches the server.
 */
const content = defineModel<string>('content', {required: true})

const {t} = useI18n()

const showModal = ref(false)

const renderedHtml = computed(() => renderMarkdown(content.value))
</script>

<template>
    <div
        class="flex-1 cursor-pointer rounded-theme border border-dashed border-transparent hover:border-(--border) p-2 transition-colors group"
        :title="t('stationPages.editor.editMarkdown')"
        @click="showModal = true"
    >
        <div v-if="content" class="markdown-content" v-html="renderedHtml"/>
        <p v-else class="text-sm text-(--text-muted) italic">{{ t('stationPages.editor.markdownEmpty') }}</p>
    </div>

    <Modal v-model="showModal" size="xl">
        <div class="space-y-3 flex flex-col h-[70dvh] min-h-0">
            <SectionHeader>{{ t('stationPages.editor.editMarkdown') }}</SectionHeader>
            <CellMarkdownEditor
                v-model:content="content"
                class="flex-1 flex flex-col min-h-0"
            />
        </div>
    </Modal>
</template>
