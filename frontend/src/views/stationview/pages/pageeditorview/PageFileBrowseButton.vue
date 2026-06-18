/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PageFileBrowseModal from './PageFileBrowseModal.vue'
import type {PageFile} from '@/api/pageManage'

defineProps<{
    stationUid: string
    mimePrefix?: string
    label?: string
    /** When true, the modal lets the user upload multiple files at once and emits pick-many. */
    multiple?: boolean
}>()

const emit = defineEmits<{
    pick: [{file: PageFile; url: string}]
    pickMany: [Array<{file: PageFile; url: string}>]
}>()

const {t} = useI18n()
const open = ref(false)

function onPick(payload: {file: PageFile; url: string}) {
    emit('pick', payload)
}

function onPickMany(payload: Array<{file: PageFile; url: string}>) {
    emit('pickMany', payload)
}
</script>

<template>
    <PrimaryButton @click="open = true">
        <font-awesome-icon :icon="['fas', 'folder-open']" class="mr-2"/>
        {{ label ?? t('stationPages.editor.browse') }}
    </PrimaryButton>
    <PageFileBrowseModal
        v-model:open="open"
        :station-uid="stationUid"
        :mime-prefix="mimePrefix"
        :multiple="multiple"
        @pick="onPick"
        @pick-many="onPickMany"
    />
</template>
