/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import KbFolderPicker from './KbFolderPicker.vue'
import KbTagsEditor from './KbTagsEditor.vue'
import {knowledgeBase} from '@/api'
import type {KbFolderTreeEntry} from '@/api/knowledgeBase'
import {kbBulkMessage} from './kbRefusals'

/**
 * The two things a marked selection can be sent through: a move into one folder, and a change of
 * tags across all of it.
 *
 * Tagging adds and removes rather than replacing. The editor that sets the whole list of one entry
 * would, used on twenty, strip every tag those twenty already carried in order to add one.
 */
const props = defineProps<{
    folderIds: number[]
    fileIds: number[]
    folders: KbFolderTreeEntry[]
}>()

const emit = defineEmits<{
    done: [message: string]
    error: [message: string]
}>()

const {t} = useI18n()

const showMove = ref(false)
const showTags = ref(false)
const target = ref<number | null>(null)
const addTags = ref<string[]>([])
const removeTags = ref<string[]>([])
const saving = ref(false)

function openMove() {
    target.value = null
    showMove.value = true
}

function openTags() {
    addTags.value = []
    removeTags.value = []
    showTags.value = true
}

const selection = () => ({folderIds: props.folderIds, fileIds: props.fileIds})

async function submitMove() {
    saving.value = true
    try {
        const outcome = await knowledgeBase.bulkMove(selection(), target.value)
        showMove.value = false
        emit('done', kbBulkMessage(t, outcome, 'kb.bulkMoved'))
    } catch {
        emit('error', t('kb.bulkTargetRefused'))
    } finally {
        saving.value = false
    }
}

async function submitTags() {
    saving.value = true
    try {
        const outcome = await knowledgeBase.bulkTags(selection(), {
            addTags: addTags.value,
            removeTags: removeTags.value,
        })
        showTags.value = false
        emit('done', kbBulkMessage(t, outcome, 'kb.bulkTagged'))
    } catch {
        emit('error', t('common.error'))
    } finally {
        saving.value = false
    }
}

defineExpose({openMove, openTags})
</script>

<template>
    <Modal v-model="showMove">
        <SubHeader class="mb-1">{{ t('kb.move') }}</SubHeader>
        <MutedText tag="p" size="sm" class="mb-3">
            {{ t('kb.bulkMoveHint', {count: folderIds.length + fileIds.length}) }}
        </MutedText>
        <KbFolderPicker v-model="target" :folders="folders"/>
        <div class="mt-4 flex justify-end gap-2">
            <SecondaryButton data-cancel @click="showMove = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="saving" data-testid="kb-bulk-move-confirm" @click="submitMove">
                {{ t('kb.move') }}
            </PrimaryButton>
        </div>
    </Modal>

    <Modal v-model="showTags">
        <SubHeader class="mb-1">{{ t('kb.tagSelected') }}</SubHeader>
        <MutedText tag="p" size="sm" class="mb-3">
            {{ t('kb.bulkTagHint', {count: folderIds.length + fileIds.length}) }}
        </MutedText>
        <KbTagsEditor v-model="addTags" :label="t('kb.bulkTagsAdd')"/>
        <KbTagsEditor v-model="removeTags" :label="t('kb.bulkTagsRemove')"/>
        <div class="mt-4 flex justify-end gap-2">
            <SecondaryButton data-cancel @click="showTags = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="saving" data-testid="kb-bulk-tags-confirm" @click="submitTags">
                {{ t('common.save') }}
            </PrimaryButton>
        </div>
    </Modal>
</template>
