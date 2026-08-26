/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {knowledgeBase} from '@/api'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {KbFolder} from '@/api/knowledgeBase'
import KbTagsEditor from './KbTagsEditor.vue'
import KbFolderIconField from './KbFolderIconField.vue'
import {useKbEntryEditor} from './useKbEntryEditor'

const {t} = useI18n()

const show = defineModel<boolean>('show', {required: true})

const props = defineProps<{
    folder: KbFolder | null
}>()

const emit = defineEmits<{
    saved: []
}>()

const iconFile = ref<File | null>(null)

const {
    editName,
    editDescription,
    tags,
    save,
} = useKbEntryEditor(show, () => props.folder, {
    visibilityKind: 'folders',
    getRestrictions: knowledgeBase.getFolderRestrictions,
    setRestrictions: knowledgeBase.setFolderRestrictions,
    getTags: knowledgeBase.getFolderTags,
    setTags: knowledgeBase.setFolderTags,
    update: knowledgeBase.updateFolder,
})

watch(show, (visible) => {
    if (visible) iconFile.value = null
})

async function handleSave() {
    const uploadIcon = (id: number) =>
        iconFile.value ? [knowledgeBase.uploadFolderIcon(id, iconFile.value)] : []
    if (await save(uploadIcon)) emit('saved')
}
</script>

<template>
    <Modal v-model="show">
        <SubHeader class="mb-3">{{ t('kb.editFolder') }}</SubHeader>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <TextInput v-model="editName" :placeholder="t('kb.folderName')" required/>
            <TextAreaInput v-model="editDescription" :placeholder="t('kb.description')"/>
            <KbFolderIconField v-model="iconFile"/>
            <KbTagsEditor v-model="tags"/>
            <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </form>
    </Modal>
</template>
