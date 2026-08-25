/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {knowledgeBase} from '@/api'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {KbFile} from '@/api/knowledgeBase'
import KbTagsEditor from './KbTagsEditor.vue'
import {useKbEntryEditor} from './useKbEntryEditor'

const {t} = useI18n()

const props = defineProps<{
    file: KbFile | null
}>()

const show = defineModel<boolean>('show', {required: true})

const emit = defineEmits<{
    saved: []
}>()

const {
    editName,
    editDescription,
    tags,
    save,
} = useKbEntryEditor(show, () => props.file, {
    visibilityKind: 'files',
    getRestrictions: knowledgeBase.getFileRestrictions,
    setRestrictions: knowledgeBase.setFileRestrictions,
    getTags: knowledgeBase.getFileTags,
    setTags: knowledgeBase.setFileTags,
    update: knowledgeBase.updateFile,
})

async function handleSave() {
    if (await save()) emit('saved')
}
</script>

<template>
    <Modal v-model="show">
        <SubHeader class="mb-3">{{ t('kb.editFile') }}</SubHeader>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <TextInput v-model="editName" :placeholder="t('kb.fileName')" required/>
            <TextAreaInput v-model="editDescription" :placeholder="t('kb.description')"/>
            <KbTagsEditor v-model="tags"/>
            <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </form>
    </Modal>
</template>
