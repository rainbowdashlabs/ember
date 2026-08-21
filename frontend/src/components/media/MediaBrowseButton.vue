/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MediaBrowseModal from './MediaBrowseModal.vue'
import type {StationFile} from '@/api/media'

defineProps<{
    stationUid: string
    mimePrefix?: string
    label?: string
    /** When true, the modal lets the user upload multiple files at once and emits pick-many. */
    multiple?: boolean
}>()

const emit = defineEmits<{
    pick: [{file: StationFile; url: string}]
    pickMany: [Array<{file: StationFile; url: string}>]
}>()

const {t} = useI18n()
const open = ref(false)

function onPick(payload: {file: StationFile; url: string}) {
    emit('pick', payload)
}

function onPickMany(payload: Array<{file: StationFile; url: string}>) {
    emit('pickMany', payload)
}
</script>

<template>
    <PrimaryButton @click="open = true">
        <font-awesome-icon :icon="['fas', 'folder-open']" class="mr-2"/>
        {{ label ?? t('stationPages.editor.browse') }}
    </PrimaryButton>
    <MediaBrowseModal
        v-model:open="open"
        :station-uid="stationUid"
        :mime-prefix="mimePrefix"
        :multiple="multiple"
        @pick="onPick"
        @pick-many="onPickMany"
    />
</template>
