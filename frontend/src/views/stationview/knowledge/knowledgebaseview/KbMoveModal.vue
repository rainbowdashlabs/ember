/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import KbFolderPicker from './KbFolderPicker.vue'
import {knowledgeBase} from '@/api'
import {KbReach, type KbFolderTreeEntry, type KbReachName, type MovePreview} from '@/api/knowledgeBase'
import {kbRefusalMessage} from './kbRefusals'

/**
 * Moves one folder or one article somewhere else.
 *
 * The dialog reads what the move would change about who can see the entry before it does anything.
 * That line is the reason a move is not simply an update: a station that publishes by default puts
 * an article on the web the moment it lands in a public folder, and nobody clicked "publish".
 */
const show = defineModel<boolean>('show', {required: true})

const props = defineProps<{
    /** The folder being moved, or null when an article is. */
    folder: {id: number; name: string} | null
    /** The article being moved, or null when a folder is. */
    file: {id: number; name: string} | null
    folders: KbFolderTreeEntry[]
}>()

const emit = defineEmits<{
    moved: []
    error: [message: string]
}>()

const {t} = useI18n()

const target = ref<number | null>(null)
const preview = ref<MovePreview | null>(null)
const saving = ref(false)
const refusal = ref<string | null>(null)

const title = computed(() => props.folder?.name ?? props.file?.name ?? '')

/** A folder cannot receive itself or anything under it, so those rows stay unpickable. */
const excludeIds = computed(() => {
    if (!props.folder) return []
    const inside = [props.folder.id]
    let added = true
    while (added) {
        added = false
        for (const entry of props.folders) {
            if (entry.parentId != null && inside.includes(entry.parentId) && !inside.includes(entry.id)) {
                inside.push(entry.id)
                added = true
            }
        }
    }
    return inside
})

const reachLabels: Record<KbReachName, string> = {
    [KbReach.INTERNAL]: 'kb.moveReachInternal',
    [KbReach.NARROW]: 'kb.moveReachNarrow',
    [KbReach.FEDERATED]: 'kb.moveReachFederated',
    [KbReach.PUBLIC]: 'kb.moveReachPublic',
}

const reachChanges = computed(() => preview.value != null && preview.value.before !== preview.value.after)

const reachWidens = computed(() =>
    preview.value != null
    && (preview.value.after === KbReach.PUBLIC || preview.value.after === KbReach.FEDERATED)
    && preview.value.before !== preview.value.after)

watch(show, (visible) => {
    if (!visible) return
    target.value = null
    refusal.value = null
    preview.value = null
})

watch([show, target], async ([visible]) => {
    if (!visible) return
    try {
        preview.value = await knowledgeBase.getMovePreview(
            {folderId: props.folder?.id ?? null, fileId: props.file?.id ?? null},
            target.value,
        )
    } catch {
        preview.value = null
    }
})

async function submit() {
    saving.value = true
    refusal.value = null
    try {
        const result = props.folder
            ? await knowledgeBase.moveFolder(props.folder.id, target.value)
            : await knowledgeBase.moveFile(props.file!.id, target.value)
        if (!result.moved) {
            refusal.value = kbRefusalMessage(t, result.reason, result.name ?? title.value)
            return
        }
        show.value = false
        emit('moved')
    } catch {
        emit('error', t('common.error'))
    } finally {
        saving.value = false
    }
}
</script>

<template>
    <Modal v-model="show">
        <SubHeader class="mb-1">{{ t('kb.move') }}</SubHeader>
        <MutedText tag="p" size="sm" class="mb-3">{{ t('kb.moveHint', {name: title}) }}</MutedText>

        <KbFolderPicker v-model="target" :folders="folders" :exclude-ids="excludeIds"/>

        <Alert v-if="reachWidens" variant="info" class="mt-3">
            {{ t('kb.moveReachWidens', {reach: t(reachLabels[preview!.after])}) }}
        </Alert>
        <MutedText v-else-if="reachChanges" tag="p" size="sm" class="mt-3">
            {{ t('kb.moveReachChanges', {reach: t(reachLabels[preview!.after])}) }}
        </MutedText>
        <MutedText v-else-if="preview" tag="p" size="sm" class="mt-3">
            {{ t('kb.moveReachUnchanged', {reach: t(reachLabels[preview.after])}) }}
        </MutedText>

        <Alert v-if="refusal" variant="error" class="mt-3">{{ refusal }}</Alert>

        <div class="mt-4 flex justify-end gap-2">
            <SecondaryButton data-cancel @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="saving" data-testid="kb-move-confirm" @click="submit">
                {{ t('kb.move') }}
            </PrimaryButton>
        </div>
    </Modal>
</template>
