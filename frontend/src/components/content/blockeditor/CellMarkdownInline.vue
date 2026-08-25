/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {renderMarkdown} from '@/util/markdown'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import CellMarkdownEditor from './CellMarkdownEditor.vue'

/**
 * Inline rendered Markdown preview that opens the full-size Markdown editor in a modal on click.
 * Owns the modal/draft state so the parent only needs to provide the current content and react to
 * updates.
 */
const content = defineModel<string>('content', {required: true})

const {t} = useI18n()

const showModal = ref(false)
const draft = ref('')

const renderedHtml = computed(() => renderMarkdown(content.value))

function openEditor() {
    draft.value = content.value
    showModal.value = true
}

function apply() {
    content.value = draft.value
    showModal.value = false
}
</script>

<template>
    <div
        class="flex-1 cursor-pointer rounded-theme border border-dashed border-transparent hover:border-(--border) p-2 transition-colors group"
        :title="t('stationPages.editor.editMarkdown')"
        @click="openEditor"
    >
        <div v-if="content" class="markdown-content" v-html="renderedHtml"/>
        <p v-else class="text-sm text-(--text-muted) italic">{{ t('stationPages.editor.markdownEmpty') }}</p>
    </div>

    <Modal v-model="showModal" size="xl">
        <div class="space-y-3 flex flex-col h-[80vh]">
            <SectionHeader>{{ t('stationPages.editor.editMarkdown') }}</SectionHeader>
            <CellMarkdownEditor
                v-model:content="draft"
                class="flex-1 flex flex-col min-h-0"
            />
            <div class="flex justify-end">
                <PrimaryButton @click="apply">{{ t('common.save') }}</PrimaryButton>
            </div>
        </div>
    </Modal>
</template>
