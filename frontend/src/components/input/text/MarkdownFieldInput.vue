/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {renderMarkdown} from '@/util/markdown'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'

const model = defineModel<string>()

const props = defineProps<{
    placeholder?: string
    title?: string
}>()

const {t} = useI18n()
const showModal = ref(false)
const draft = ref('')

watch(showModal, v => {
    if (v) draft.value = model.value ?? ''
})

const renderedHtml = computed(() => renderMarkdown(model.value))

function apply() {
    model.value = draft.value
    showModal.value = false
}
</script>

<template>
    <div
        class="cursor-pointer rounded-theme border border-dashed border-(--border) hover:border-primary px-2 py-1.5 transition-colors min-h-9"
        :title="title ?? t('stationPages.editor.editMarkdown')"
        role="button"
        tabindex="0"
        @click="showModal = true"
        @keydown.enter="showModal = true"
    >
        <div v-if="model" class="markdown-content markdown-content--compact text-sm" v-html="renderedHtml"/>
        <p v-else class="text-sm text-(--text-muted) italic">{{ placeholder ?? t('common.empty') }}</p>
    </div>

    <Modal v-model="showModal" size="xl">
        <div class="space-y-3 flex flex-col h-[80vh]">
            <SectionHeader>{{ title ?? t('stationPages.editor.editMarkdown') }}</SectionHeader>
            <MarkdownEditor
                v-model="draft"
                class="flex-1 flex flex-col min-h-0"
            />
            <div class="flex justify-end">
                <PrimaryButton @click="apply">{{ t('common.save') }}</PrimaryButton>
            </div>
        </div>
    </Modal>
</template>
