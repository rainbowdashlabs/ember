/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import type {StationFileFolder, StationFileTag} from '@/api/media'

const folderOpen = defineModel<boolean>('folderOpen', {required: true})
const folderName = defineModel<string>('folderName', {required: true})
const folderParent = defineModel<number | null>('folderParent', {required: true})
const tagOpen = defineModel<boolean>('tagOpen', {required: true})
const tagName = defineModel<string>('tagName', {required: true})
const tagColor = defineModel<string>('tagColor', {required: true})

defineProps<{
    folders: StationFileFolder[]
    editingFolder: StationFileFolder | null
    editingTag: StationFileTag | null
}>()

const emit = defineEmits<{
    saveFolder: []
    saveTag: []
}>()

const {t} = useI18n()
</script>

<template>
    <Modal v-model="folderOpen" size="md">
        <div class="space-y-3">
            <SubHeader>
                {{ editingFolder ? t('stationPages.editor.editFolder') : t('stationPages.editor.newFolder') }}
            </SubHeader>
            <TextInput v-model="folderName" :placeholder="t('stationPages.editor.folderName')"/>
            <SelectInput :model-value="folderParent === null ? '' : String(folderParent)"
                         class="w-full"
                         @update:model-value="(v: string | number | null | undefined) => folderParent = v ? +v : null">
                <option value="">{{ t('stationPages.editor.rootFolder') }}</option>
                <option v-for="f in folders" :key="f.id"
                        :value="f.id" :disabled="editingFolder?.id === f.id">
                    {{ f.name }}
                </option>
            </SelectInput>
            <div class="flex justify-end gap-2">
                <SecondaryButton @click="folderOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="emit('saveFolder')">{{ t('common.save') }}</PrimaryButton>
            </div>
        </div>
    </Modal>

    <Modal v-model="tagOpen" size="md">
        <div class="space-y-3">
            <SubHeader>
                {{ editingTag ? t('stationPages.editor.editTag') : t('stationPages.editor.newTag') }}
            </SubHeader>
            <TextInput v-model="tagName" :placeholder="t('stationPages.editor.tagName')"/>
            <div class="flex items-center gap-2">
                <ColorInput v-model="tagColor"/>
                <span class="text-xs text-(--text-muted)">{{ tagColor }}</span>
            </div>
            <div class="flex justify-end gap-2">
                <SecondaryButton @click="tagOpen = false">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="emit('saveTag')">{{ t('common.save') }}</PrimaryButton>
            </div>
        </div>
    </Modal>
</template>
