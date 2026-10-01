/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {knowledgeBase} from '@/api'
import type {DeleteImpact, KbFileSummary, KbFolder} from '@/api/generated/schema'

/**
 * Asking before a delete, and saying what it really costs.
 *
 * A public page can carry a wiki article, and it names the article by number in its settings without
 * anything in the database holding the two together. Deleting the article leaves that page showing a
 * stand-in title, silently, so the one moment anybody could notice is this dialog.
 */
const showFolder = defineModel<boolean>('showFolder', {required: true})
const showFile = defineModel<boolean>('showFile', {required: true})

const props = defineProps<{
    folder: KbFolder | null
    file: KbFileSummary | null
}>()

const emit = defineEmits<{
    confirmFolder: []
    confirmFile: []
}>()

const {t} = useI18n()

const impact = ref<DeleteImpact | null>(null)

/**
 * The stronger sentence is kept for a page the public reads: everybody else's page can be put right
 * by whoever notices, and that one is read by people who never will.
 */
const embeddedMessage = computed(() => {
    const pages = impact.value?.embeddedOn ?? []
    if (pages.length === 0) return ''
    const key = impact.value?.onPublicPage ? 'kb.deleteEmbeddedOnPublic' : 'kb.deleteEmbeddedOn'
    return t(key, {pages: pages.join(', ')})
})

async function loadImpact(selection: {folderIds: number[]; fileIds: number[]}) {
    impact.value = null
    try {
        impact.value = await knowledgeBase.getDeleteImpact(selection)
    } catch {
        impact.value = null
    }
}

watch(showFolder, open => {
    if (open && props.folder) loadImpact({folderIds: [props.folder.id], fileIds: []})
})

watch(showFile, open => {
    if (open && props.file) loadImpact({folderIds: [], fileIds: [props.file.id]})
})
</script>

<template>
    <ConfirmDeleteModal
        v-model="showFolder"
        :title="t('kb.deleteFolder')"
        :message="t('kb.deleteFolderConfirm')"
        @confirm="emit('confirmFolder')"
    >
        <MutedText v-if="impact && impact.files > 0" tag="p" size="sm">
            {{ t('kb.bulkDeleteHint', {folders: impact.folders, files: impact.files}) }}
        </MutedText>
        <Alert v-if="impact && impact.embeddedOn.length > 0" variant="info" data-testid="kb-delete-embedded">
            {{ embeddedMessage }}
        </Alert>
        <MutedText tag="p" size="sm">{{ t('kb.deleteRecoverable') }}</MutedText>
    </ConfirmDeleteModal>

    <ConfirmDeleteModal
        v-model="showFile"
        :title="t('kb.deleteFile')"
        :message="t('kb.deleteFileConfirm')"
        @confirm="emit('confirmFile')"
    >
        <Alert v-if="impact && impact.embeddedOn.length > 0" variant="info" data-testid="kb-delete-embedded">
            {{ embeddedMessage }}
        </Alert>
        <MutedText tag="p" size="sm">{{ t('kb.deleteRecoverable') }}</MutedText>
    </ConfirmDeleteModal>
</template>
