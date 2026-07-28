/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import Modal from '@/components/feedback/Modal.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { boards } from '@/api'
import type { Board, LanePresetName } from '@/api/boards'
import { LanePreset } from '@/api/boards'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useAsyncAction } from '@/composables/useAsyncAction'
const { t } = useI18n()
const router = useRouter()

const { config: boardList, loading, error, reload: loadBoards } = useConfigPanel<Board[]>({
    initial: [],
    fetch: () => boards.listBoards(),
})

const showCreateModal = ref(false)
const createName = ref('')
const createDescription = ref('')
const createShortKey = ref('')
const createPreset = ref<LanePresetName | ''>(LanePreset.SIMPLE)
const createValidationError = ref('')

const {
    show: showDeleteModal,
    target: deleteTarget,
    requestDelete: confirmDelete,
    confirm: handleDelete,
} = useConfirmDelete<Board>({
    onDelete: b => boards.deleteBoard(b.shortKey),
    onSuccess: () => loadBoards(),
    error,
})

const {error: createApiError, run: runCreate} = useAsyncAction(async () => {
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
}, {formatError: () => t('common.error')})

const createError = computed(() => createValidationError.value || createApiError.value)

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
            :error="error"
            :loading="loading"
        >
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <NeutralContainer
                    v-for="board in boardList"
                    :key="board.id"
                    class="cursor-pointer hover:border-[var(--accent)] transition-colors"
                    @click="router.push(`/station/boards/${board.shortKey}`)"
                >
                    <div class="flex items-start justify-between">
                        <div>
                            <div class="flex items-center gap-2 mb-1">
                                <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                                <SubHeader>{{ board.name }}</SubHeader>
                            </div>
                            <p v-if="board.description" class="text-sm text-[var(--text-muted)] line-clamp-2">{{ board.description }}</p>
                        </div>
                        <div class="flex items-center gap-1 shrink-0">
                            <IconButton :icon="['fas', 'gears']" :label="t('boards.settings')" @click.stop="router.push(`/station/boards/${board.shortKey}/settings`)" />
                            <DeleteButton @click.stop="confirmDelete(board)" />
                        </div>
                    </div>
                    <div class="mt-3 text-xs text-[var(--text-muted)]">
                        {{ board.ticketCounter }} {{ board.ticketCounter === 1 ? 'Ticket' : 'Tickets' }}
                    </div>
                </NeutralContainer>
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
                <Alert v-if="createError" variant="error">{{ createError }}</Alert>
                <div class="flex justify-end gap-2">
                    <PrimaryButton @click="handleCreate">{{ t('common.create') }}</PrimaryButton>
                </div>
            </div>
        </Modal>

        <!-- Delete Modal -->
        <Modal v-model="showDeleteModal">
            <SubHeader class="mb-4">{{ t('boards.deleteBoard') }}</SubHeader>
            <p class="mb-4">{{ t('boards.deleteBoardConfirm') }}</p>
            <div class="flex justify-end gap-2">
                <DeleteButton @click="handleDelete">{{ t('common.delete') }}</DeleteButton>
            </div>
        </Modal>
    </ViewContent>
</template>
