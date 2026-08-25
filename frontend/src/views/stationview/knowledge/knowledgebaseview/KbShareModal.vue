/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {knowledgeBase} from '@/api'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'
import {useSession} from '@/composables/useSession'
import KbRestrictionsField from './KbRestrictionsField.vue'
import KbPublicVisibilityField from './KbPublicVisibilityField.vue'
import {useKbEntrySharing} from './useKbEntrySharing'
import type {KbEntryApi} from './useKbEntryEditor'

const {t} = useI18n()
const {isKbPublic} = useSession()

const props = defineProps<{
    entry: KbFile | KbFolder | null
    kind: 'files' | 'folders'
}>()

const show = defineModel<boolean>('show', {required: true})

const emit = defineEmits<{
    saved: []
}>()

const FILE_API: KbEntryApi = {
    visibilityKind: 'files',
    getRestrictions: knowledgeBase.getFileRestrictions,
    setRestrictions: knowledgeBase.setFileRestrictions,
    getTags: knowledgeBase.getFileTags,
    setTags: knowledgeBase.setFileTags,
    update: knowledgeBase.updateFile,
}

const FOLDER_API: KbEntryApi = {
    visibilityKind: 'folders',
    getRestrictions: knowledgeBase.getFolderRestrictions,
    setRestrictions: knowledgeBase.setFolderRestrictions,
    getTags: knowledgeBase.getFolderTags,
    setTags: knowledgeBase.setFolderTags,
    update: knowledgeBase.updateFolder,
}

const {
    restriction,
    grantLevels,
    publicVisibility,
    allGroups,
    allTags,
    save,
} = useKbEntrySharing(show, () => props.entry, props.kind === 'files' ? FILE_API : FOLDER_API)

async function handleSave() {
    if (await save()) emit('saved')
}
</script>

<template>
    <Modal v-model="show">
        <SubHeader class="mb-1">{{ t('kb.share') }}</SubHeader>
        <p class="mb-3 text-xs text-(--text-muted)">{{ t('kb.shareHint') }}</p>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <KbRestrictionsField
                :all-groups="allGroups"
                :all-tags="allTags"
                v-model="restriction"
                v-model:levels="grantLevels"
            />
            <KbPublicVisibilityField :disabled="!isKbPublic()" v-model="publicVisibility"/>
            <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </form>
    </Modal>
</template>
