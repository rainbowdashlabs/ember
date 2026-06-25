/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import type {PageFile} from '@/api/pageManage'

const file = defineModel<PageFile | null>({required: true})

const emit = defineEmits<{
    save: [fileId: number, altText: string, description: string]
}>()

const {t} = useI18n()

const alt = ref('')
const description = ref('')

watch(file, (next) => {
    alt.value = next?.defaultAltText ?? ''
    description.value = next?.defaultDescription ?? ''
}, {immediate: true})

function commit() {
    if (!file.value) return
    emit('save', file.value.id, alt.value, description.value)
    file.value = null
}
</script>

<template>
    <Modal v-if="file" :model-value="!!file" size="md"
           @update:model-value="(v: boolean) => { if (!v) file = null }">
        <div class="space-y-3">
            <SectionHeader>{{ t('stationPages.editor.editFileMeta') }}</SectionHeader>
            <TextInput v-model="alt" :placeholder="t('stationPages.editor.altText')"/>
            <TextInput v-model="description" :placeholder="t('stationPages.editor.imageDescription')"/>
            <div class="flex justify-end gap-2">
                <SecondaryButton @click="file = null">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="commit">{{ t('common.save') }}</PrimaryButton>
            </div>
        </div>
    </Modal>
</template>
