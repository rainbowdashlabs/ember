/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {StationFileFolder} from '@/api/media'

const moveOpen = defineModel<boolean>('moveOpen', {required: true})
const deleteOpen = defineModel<boolean>('deleteOpen', {required: true})
const moveTarget = defineModel<number | null>('moveTarget', {required: true})

defineProps<{
    selectedCount: number
    folders: StationFileFolder[]
}>()

const emit = defineEmits<{
    move: []
    delete: []
}>()

const {t} = useI18n()
</script>

<template>
    <Modal v-model="moveOpen" size="md">
        <div class="space-y-3">
            <SubHeader>{{ t('stationPages.editor.moveToFolder') }}</SubHeader>
            <p class="text-sm text-(--text-muted)">
                {{ t('stationPages.editor.selectedCount', {count: selectedCount}) }}
            </p>
            <SelectInput :model-value="moveTarget === null ? '' : String(moveTarget)"
                         class="w-full"
                         @update:model-value="(v: string | number | null | undefined) => moveTarget = v ? +v : null">
                <option value="">{{ t('stationPages.editor.rootFolder') }}</option>
                <option v-for="f in folders" :key="f.id" :value="f.id">{{ f.name }}</option>
            </SelectInput>
            <ButtonRow pair align="end">
                <SecondaryButton @click="moveOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="emit('move')">{{ t('stationPages.editor.moveSelected') }}</PrimaryButton>
            </ButtonRow>
        </div>
    </Modal>

    <ConfirmDeleteModal
        v-model="deleteOpen"
        :title="t('stationPages.editor.deleteSelected')"
        :message="t('stationPages.editor.deleteSelectedPrompt', {count: selectedCount})"
        :confirm-label="t('stationPages.editor.deleteSelected')"
        @confirm="emit('delete')"
    />
</template>
