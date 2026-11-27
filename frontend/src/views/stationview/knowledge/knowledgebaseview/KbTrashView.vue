/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {knowledgeBase} from '@/api'
import type {TrashEntry} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {describeFailure, type Failure} from '@/util/failure'
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
 * deserves to be shown why rather than left guessing.
 */
const emit = defineEmits<{
    back: []
    restored: []
}>()

const {t} = useI18n()

const entries = ref<TrashEntry[]>([])
const bytes = ref(0)
const notice = ref('')
const busy = ref(false)

const {loading, failure: loadFailure, reload} = useAsyncLoader(async () => {
    const view = await knowledgeBase.listTrash()
    entries.value = view.entries
    bytes.value = view.bytes
})

/** What the reader's last action ran into, kept apart from the list never having arrived. */
const actionFailure = ref<Failure | null>(null)

/**
 * Fetching the bin again after something was done to it, which is not part of doing it. A restore
 * that worked and a list that then failed to come back is a stale screen, not a failed restore.
 */
async function catchUp() {
    await reload()
    if (loadFailure.value) {
        actionFailure.value = {...loadFailure.value, message: t('failure.staleAfterAction')}
        loadFailure.value = null
    }
}

const isEmpty = computed(() => !loading.value && entries.value.length === 0)

function iconOf(entry: TrashEntry): string[] {
    return ['fas', entry.folder ? 'folder' : 'file-lines']
}

async function restore(entry: TrashEntry) {
    busy.value = true
    actionFailure.value = null
    try {
        const result = await knowledgeBase.restoreTrashed(entry)
        notice.value = result.movedToRoot
            ? t('kb.trashRestoredToRoot', {name: result.name ?? entry.name})
            : t('kb.trashRestored', {name: result.name ?? entry.name})
    } catch (e) {
        actionFailure.value = describeFailure(e, t)
        busy.value = false
        return
    }
    await catchUp()
    emit('restored')
    busy.value = false
}

/** Runs one change to the bin with its buttons held. */
async function whileBusy<R>(work: () => Promise<R>): Promise<R> {
    busy.value = true
    try {
        return await work()
    } finally {
        busy.value = false
    }
}

const {show: showPurge, target: purging, requestDelete: requestPurge, confirm: purge} = useConfirmDelete<TrashEntry>({
    onDelete: entry => whileBusy(async () => {
        actionFailure.value = null
        await knowledgeBase.purgeTrashed(entry)
        notice.value = t('kb.trashPurged', {name: entry.name})
    }),
    onSuccess: () => whileBusy(catchUp),
    failure: actionFailure,
})

const {show: showEmpty, request: requestEmpty, confirm: empty} = useConfirmAction<true>({
    onConfirm: () => whileBusy(async () => {
        actionFailure.value = null
        const result = await knowledgeBase.emptyTrash()
        notice.value = t('kb.trashEmptied', {count: result.cleared})
    }),
    onSuccess: () => whileBusy(catchUp),
    failure: actionFailure,
})
</script>

<template>
    <div>
        <ButtonRow class="mb-4">
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
                @click="requestEmpty(true)"
            >
                {{ t('kb.trashEmpty') }}
            </DeleteButton>
        </ButtonRow>

        <FailureAlert :failure="actionFailure ?? loadFailure" class="mb-4"/>
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
                            @click="requestPurge(entry)"
                        />
                    </div>
                </div>
            </NeutralContainer>
        </div>

        <ConfirmDeleteModal
            v-model="showPurge"
            :title="t('kb.trashPurge')"
            :message="t('kb.trashPurgeConfirm', {name: purging?.name ?? ''})"
            :busy="busy"
            confirm-test-id="kb-trash-purge-confirm"
            @confirm="purge"
        />

        <ConfirmDeleteModal
            v-model="showEmpty"
            :title="t('kb.trashEmpty')"
            :message="t('kb.trashEmptyConfirm', {size: formatBytes(bytes)})"
            :confirm-label="t('kb.trashEmpty')"
            :busy="busy"
            confirm-test-id="kb-trash-empty-confirm"
            @confirm="empty"
        />
    </div>
</template>
