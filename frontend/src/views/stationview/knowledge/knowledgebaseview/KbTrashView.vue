/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {knowledgeBase} from '@/api'
import type {KbTrashEntry} from '@/api/knowledgeBase'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatBytes} from '@/util/storage'
import {formatDateTime} from '@/util/format'

/**
 * What this reader deleted and has not lost yet.
 *
 * The list is what they may manage, which is the same right that let them delete in the first place,
 * so nobody has to ask for help to undo their own mistake and nobody reads the name of an entry they
 * were never allowed to open.
 *
 * The size in the header is not decoration. What waits here is still on disk and still counts
 * against the station's room, and a station that has just cleared up and still cannot upload
 * deserves to be told why rather than left guessing.
 */
const emit = defineEmits<{
    back: []
    restored: []
}>()

const {t} = useI18n()

const entries = ref<KbTrashEntry[]>([])
const bytes = ref(0)
const notice = ref('')
const purging = ref<KbTrashEntry | null>(null)
const showEmpty = ref(false)
const busy = ref(false)

const {loading, error, reload} = useAsyncLoader(async () => {
    const view = await knowledgeBase.listTrash()
    entries.value = view.entries
    bytes.value = view.bytes
})

const isEmpty = computed(() => !loading.value && entries.value.length === 0)

function iconOf(entry: KbTrashEntry): string[] {
    return ['fas', entry.folder ? 'folder' : 'file-lines']
}

async function restore(entry: KbTrashEntry) {
    busy.value = true
    try {
        const result = await knowledgeBase.restoreTrashed(entry)
        notice.value = result.movedToRoot
            ? t('kb.trashRestoredToRoot', {name: result.name ?? entry.name})
            : t('kb.trashRestored', {name: result.name ?? entry.name})
        await reload()
        emit('restored')
    } catch {
        error.value = t('common.error')
    } finally {
        busy.value = false
    }
}

async function purge() {
    const entry = purging.value
    if (!entry) return
    busy.value = true
    try {
        await knowledgeBase.purgeTrashed(entry)
        purging.value = null
        notice.value = t('kb.trashPurged', {name: entry.name})
        await reload()
    } catch {
        error.value = t('common.error')
    } finally {
        busy.value = false
    }
}

async function empty() {
    busy.value = true
    try {
        const result = await knowledgeBase.emptyTrash()
        showEmpty.value = false
        notice.value = t('kb.trashEmptied', {count: result.cleared})
        await reload()
    } catch {
        error.value = t('common.error')
    } finally {
        busy.value = false
    }
}
</script>

<template>
    <div>
        <div class="mb-4 flex flex-wrap items-center gap-2">
            <SecondaryButton data-testid="kb-trash-back" @click="emit('back')">
                <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>
                {{ t('kb.backToBrowse') }}
            </SecondaryButton>
            <SubHeader class="flex-1">{{ t('kb.trash') }}</SubHeader>
            <MutedText size="sm" data-testid="kb-trash-size">
                {{ t('kb.trashSize', {size: formatBytes(bytes)}) }}
            </MutedText>
            <DeleteButton
                v-if="entries.length > 0"
                data-testid="kb-trash-empty"
                @click="showEmpty = true"
            >
                {{ t('kb.trashEmpty') }}
            </DeleteButton>
        </div>

        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
        <Alert v-if="notice" variant="info" class="mb-4" data-testid="kb-trash-notice">{{ notice }}</Alert>

        <MutedText tag="p" size="sm" class="mb-4">{{ t('kb.trashHint') }}</MutedText>

        <Spinner v-if="loading"/>
        <EmptyHint v-else-if="isEmpty" data-testid="kb-trash-empty-hint">{{ t('kb.trashNothing') }}</EmptyHint>
        <div v-else class="flex flex-col gap-2">
            <NeutralContainer
                v-for="entry in entries"
                :key="`${entry.folder ? 'folder' : 'file'}-${entry.id}`"
                data-testid="kb-trash-entry"
            >
                <div class="flex items-start gap-3 p-2">
                    <font-awesome-icon :icon="iconOf(entry)" class="text-xl mt-0.5 text-(--text-muted)"/>
                    <div class="flex-1 min-w-0">
                        <p class="text-sm font-medium truncate">{{ entry.name }}</p>
                        <MutedText tag="p" size="xs">
                            {{ t('kb.trashDeletedAt', {when: formatDateTime(entry.deletedAt)}) }}
                            <template v-if="entry.deletedByName">
                                {{ t('kb.trashDeletedBy', {name: entry.deletedByName}) }}
                            </template>
                            <template v-if="entry.contained > 0">
                                {{ t('kb.trashContains', {count: entry.contained}) }}
                            </template>
                            <template v-if="entry.bytes > 0">
                                {{ t('kb.trashHolds', {size: formatBytes(entry.bytes)}) }}
                            </template>
                        </MutedText>
                    </div>
                    <div class="flex items-center gap-1">
                        <IconButton
                            :disabled="busy"
                            :icon="['fas', 'rotate-left']"
                            :label="t('kb.trashRestore')"
                            data-testid="kb-trash-restore"
                            @click="restore(entry)"
                        />
                        <IconButton
                            :disabled="busy"
                            :icon="['fas', 'trash']"
                            :label="t('kb.trashPurge')"
                            class="text-error hover:bg-error/15"
                            data-testid="kb-trash-purge"
                            @click="purging = entry"
                        />
                    </div>
                </div>
            </NeutralContainer>
        </div>

        <Modal :model-value="purging !== null" @update:model-value="purging = null">
            <SubHeader class="mb-1">{{ t('kb.trashPurge') }}</SubHeader>
            <MutedText tag="p" size="sm" class="mb-3">
                {{ t('kb.trashPurgeConfirm', {name: purging?.name ?? ''}) }}
            </MutedText>
            <div class="mt-4 flex justify-end gap-2">
                <SecondaryButton data-cancel @click="purging = null">{{ t('common.cancel') }}</SecondaryButton>
                <DeleteButton :disabled="busy" data-testid="kb-trash-purge-confirm" @click="purge">
                    {{ t('common.delete') }}
                </DeleteButton>
            </div>
        </Modal>

        <Modal v-model="showEmpty">
            <SubHeader class="mb-1">{{ t('kb.trashEmpty') }}</SubHeader>
            <MutedText tag="p" size="sm" class="mb-3">
                {{ t('kb.trashEmptyConfirm', {size: formatBytes(bytes)}) }}
            </MutedText>
            <div class="mt-4 flex justify-end gap-2">
                <SecondaryButton data-cancel @click="showEmpty = false">{{ t('common.cancel') }}</SecondaryButton>
                <DeleteButton :disabled="busy" data-testid="kb-trash-empty-confirm" @click="empty">
                    {{ t('kb.trashEmpty') }}
                </DeleteButton>
            </div>
        </Modal>
    </div>
</template>
