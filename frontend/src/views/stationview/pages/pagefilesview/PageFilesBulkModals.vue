/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import type {PageFileFolder} from '@/api/pageManage'

const moveOpen = defineModel<boolean>('moveOpen', {required: true})
const deleteOpen = defineModel<boolean>('deleteOpen', {required: true})
const moveTarget = defineModel<number | null>('moveTarget', {required: true})

defineProps<{
    selectedCount: number
    folders: PageFileFolder[]
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
            <SectionHeader>{{ t('stationPages.editor.moveToFolder') }}</SectionHeader>
            <p class="text-sm text-(--text-muted)">
                {{ t('stationPages.editor.selectedCount', {count: selectedCount}) }}
            </p>
            <SelectInput :model-value="moveTarget === null ? '' : String(moveTarget)"
                         class="w-full"
                         @update:model-value="(v: string) => moveTarget = v ? +v : null">
                <option value="">{{ t('stationPages.editor.rootFolder') }}</option>
                <option v-for="f in folders" :key="f.id" :value="f.id">{{ f.name }}</option>
            </SelectInput>
            <div class="flex justify-end gap-2">
                <SecondaryButton @click="moveOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="emit('move')">{{ t('stationPages.editor.moveSelected') }}</PrimaryButton>
            </div>
        </div>
    </Modal>

    <Modal v-model="deleteOpen" size="md">
        <div class="space-y-3">
            <SectionHeader>{{ t('stationPages.editor.deleteSelected') }}</SectionHeader>
            <p class="text-sm">
                {{ t('stationPages.editor.deleteSelectedPrompt', {count: selectedCount}) }}
            </p>
            <div class="flex justify-end gap-2">
                <SecondaryButton @click="deleteOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                <ErrorButton @click="emit('delete')">{{ t('stationPages.editor.deleteSelected') }}</ErrorButton>
            </div>
        </div>
    </Modal>
</template>
