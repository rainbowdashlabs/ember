/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import {KbFileType, type KbFile} from '@/api/knowledgeBase'

defineProps<{
    file: KbFile
    isFederated: boolean
    isKbPublic: boolean
    shareCopied: boolean
    canEdit: boolean
    editing: boolean
}>()

defineEmits<{
    back: []
    copyShareLink: []
    copyToStation: []
    openEditMetadata: []
    toggleEdit: []
    openVersions: []
    openPresentation: []
    downloadOriginal: []
}>()

const {t} = useI18n()
</script>

<template>
    <div class="flex flex-wrap items-center gap-2 mb-4">
        <SecondaryButton @click="$emit('back')">
            <font-awesome-icon :icon="['fas', 'chevron-left']"/>
            {{ t('kb.backToBrowse') }}
        </SecondaryButton>

        <PageHeader class="flex-1 !mb-0">{{ file.name }}</PageHeader>

        <IconButton
            v-if="isKbPublic"
            :icon="['fas', shareCopied ? 'check' : 'share-nodes']"
            :label="t('kb.shareLink')"
            :class="shareCopied ? '!text-green-500' : '!text-[var(--text-muted)]'"
            @click="$emit('copyShareLink')"
        />

        <PrimaryButton v-if="isFederated" @click="$emit('copyToStation')">
            <font-awesome-icon :icon="['fas', 'copy']"/>
            {{ t('federation.copyToStation') }}
        </PrimaryButton>
        <template v-else>
            <SecondaryButton v-if="canEdit" @click="$emit('openEditMetadata')">
                <font-awesome-icon :icon="['fas', 'gear']"/>
                {{ t('kb.editMetadata') }}
            </SecondaryButton>
            <PrimaryButton
                v-if="canEdit && (file.fileType === KbFileType.MARKDOWN || file.fileType === KbFileType.TEXT)"
                @click="$emit('toggleEdit')"
            >
                <font-awesome-icon :icon="['fas', editing ? 'eye' : 'pen']"/>
                {{ editing ? t('kb.preview') : t('kb.edit') }}
            </PrimaryButton>
            <SecondaryButton
                v-if="file.fileType === KbFileType.MARKDOWN"
                @click="$emit('openVersions')"
            >
                <font-awesome-icon :icon="['fas', 'clock-rotate-left']"/>
                {{ t('kb.versions') }}
            </SecondaryButton>
            <SecondaryButton
                v-if="(file.fileType === KbFileType.PDF || (file.fileType === KbFileType.PRESENTATION && file.conversionStatus === 'SUCCESS'))"
                @click="$emit('openPresentation')"
            >
                <font-awesome-icon :icon="['fas', 'display']"/>
                {{ t('kb.present') }}
            </SecondaryButton>
            <SecondaryButton
                v-if="file.fileType === KbFileType.PRESENTATION"
                @click="$emit('downloadOriginal')"
            >
                <font-awesome-icon :icon="['fas', 'download']"/>
                {{ t('kb.downloadOriginal') }}
            </SecondaryButton>
        </template>
    </div>
</template>
