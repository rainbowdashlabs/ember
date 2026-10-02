/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import BoardCard from './BoardCard.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import Modal from '@/components/feedback/Modal.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { boards } from '@/api'
import {LanePreset, type Board} from '@/api/generated/schema'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useAsyncAction } from '@/composables/useAsyncAction'
const { t } = useI18n()
const router = useRouter()

const { config: boardList, loading, failure, reload: loadBoards } = useConfigPanel<Board[]>({
    initial: [],
    fetch: () => boards.listBoards(),
})

const showCreateModal = ref(false)
const createName = ref('')
const createDescription = ref('')
const createShortKey = ref('')
const createPreset = ref<LanePreset | ''>(LanePreset.SIMPLE)
const createValidationError = ref('')

const {
    show: showDeleteModal,
    requestDelete: confirmDelete,
    confirm: handleDelete,
} = useConfirmDelete<Board>({
    onDelete: b => boards.deleteBoard(b.shortKey),
    onSuccess: () => loadBoards(),
    failure,
})

const {failure: createFailure, run: runCreate} = useAsyncAction(async () => {
    const board = await boards.createBoard({
        name: createName.value.trim(),
        description: createDescription.value.trim() || undefined,
        shortKey: createShortKey.value.trim().toUpperCase(),
        preset: createPreset.value || undefined,
    })
    showCreateModal.value = false
    createName.value = ''
    createDescription.value = ''
    createShortKey.value = ''
    createPreset.value = LanePreset.SIMPLE
    await router.push(`/station/boards/${board.shortKey}`)
})

function boardPage(board: Board): string {
    return `/station/boards/${board.shortKey}`
}

function handleCreate() {
    createValidationError.value = ''
    if (!createName.value.trim() || !createShortKey.value.trim()) {
        createValidationError.value = t('common.requiredField')
        return
    }
    void runCreate()
}

</script>

<template>
    <ViewContent
        :title="t('pages.board-manage.title')"
        :subtitle="t('pages.board-manage.subtitle')"
    >
        <div class="flex items-center justify-end mb-6">
            <PrimaryButton @click="showCreateModal = true">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                {{ t('boards.createBoard') }}
            </PrimaryButton>
        </div>

        <AsyncSection
            :empty="boardList.length === 0"
            :empty-message="t('boards.noBoards')"
            :failure="failure"
            :loading="loading"
        >
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <RowLink v-for="board in boardList" :key="board.id" :to="boardPage(board)">
                    <BoardCard
                        :board="board"
                        manageable
                        @settings="router.push(`/station/boards/${board.shortKey}/settings`)"
                        @delete="confirmDelete(board)"
                    />
                </RowLink>
            </div>
        </AsyncSection>

        <Modal v-model="showCreateModal">
            <SubHeader class="mb-4">{{ t('boards.createBoard') }}</SubHeader>
            <div class="space-y-4">
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.boardName') }} *</FieldLabel>
                    <TextInput v-model="createName" />
                </div>
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.shortKey') }} *</FieldLabel>
                    <TextInput v-model="createShortKey" :placeholder="t('boards.shortKeyHint')" />
                </div>
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.boardDescription') }}</FieldLabel>
                    <TextAreaInput v-model="createDescription" :rows="3" />
                </div>
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.preset') }}</FieldLabel>
                    <SelectInput v-model="createPreset" class="w-full">
                        <option :value="LanePreset.SIMPLE">{{ t('boards.presetSimple') }}</option>
                        <option :value="LanePreset.FEEDBACK">{{ t('boards.presetFeedback') }}</option>
                        <option value="">{{ t('boards.presetNone') }}</option>
                    </SelectInput>
                </div>
                <FailureAlert :message="createValidationError" expected/>
                <FailureAlert :failure="createFailure"/>
                <div class="flex justify-end gap-2">
                    <PrimaryButton @click="handleCreate">{{ t('common.create') }}</PrimaryButton>
                </div>
            </div>
        </Modal>

        <Modal v-model="showDeleteModal">
            <SubHeader class="mb-4">{{ t('boards.deleteBoard') }}</SubHeader>
            <p class="mb-4">{{ t('boards.deleteBoardConfirm') }}</p>
            <div class="flex justify-end gap-2">
                <DeleteButton @click="handleDelete">{{ t('common.delete') }}</DeleteButton>
            </div>
        </Modal>
    </ViewContent>
</template>
