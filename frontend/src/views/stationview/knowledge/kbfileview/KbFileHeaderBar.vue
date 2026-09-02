/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import {KbFileType, type KbFile} from '@/api/knowledgeBase'

const props = defineProps<{
    file: KbFile
    isFederated: boolean
    isKbPublic: boolean
    shareCopied: boolean
    canEdit: boolean
    /** Moving changes who is responsible for the article, so it asks for more than editing does. */
    canMove: boolean
    editing: boolean
}>()

defineEmits<{
    back: []
    copyShareLink: []
    copyToStation: []
    openEditMetadata: []
    openMove: []
    openShare: []
    toggleEdit: []
    openVersions: []
    openPresentation: []
    downloadOriginal: []
    downloadPdf: []
}>()

const {t} = useI18n()

const isTextFile = computed(() => props.file.fileType === KbFileType.MARKDOWN
    || props.file.fileType === KbFileType.TEXT)
const canWriteText = computed(() => props.canEdit && isTextFile.value)
const canPresent = computed(() => props.file.fileType === KbFileType.PDF
    || (props.file.fileType === KbFileType.PRESENTATION && props.file.conversionStatus === 'SUCCESS'))
const hasVersions = computed(() => props.file.fileType === KbFileType.MARKDOWN)
const hasOriginal = computed(() => props.file.fileType === KbFileType.PRESENTATION)
const canShareLink = computed(() => props.isKbPublic && !props.isFederated)

/**
 * Which one action stays a button of its own, which here changes with the file rather than being
 * fixed: a file from a partner station is read in order to be taken over, a text file of one's own
 * is opened in order to be written in, and a presentation or a PDF is opened in order to be shown.
 * A file that is none of those, an image say, offers its download instead, and one that is not even
 * downloadable leaves the menu standing alone.
 */
const primary = computed(() => {
    if (props.isFederated) return 'copy'
    if (canWriteText.value) return 'edit'
    if (canPresent.value) return 'present'
    if (isTextFile.value) return 'pdf'
    return 'none'
})

const hasMenu = computed(() => canShareLink.value
    || (isTextFile.value && primary.value !== 'pdf')
    || (!props.isFederated && (props.canEdit || props.canMove || hasVersions.value || hasOriginal.value
        || (canPresent.value && primary.value !== 'present'))))
</script>

<template>
    <div class="flex flex-wrap items-center gap-2 mb-4" data-testid="kb-file-header">
        <SecondaryButton @click="$emit('back')">
            <font-awesome-icon :icon="['fas', 'chevron-left']"/>
            {{ t('kb.backToBrowse') }}
        </SecondaryButton>

        <PageHeader class="flex-1 !mb-0">{{ file.name }}</PageHeader>

        <!--
          The share link keeps its own button although it is an action like the others: the whole
          answer it gives is the tick it turns into, and a menu that has closed itself cannot show
          one.
        -->
        <IconButton
            v-if="canShareLink"
            :icon="['fas', shareCopied ? 'check' : 'share-nodes']"
            :label="t('kb.shareLink')"
            :class="shareCopied ? '!text-green-500' : '!text-[var(--text-muted)]'"
            @click="$emit('copyShareLink')"
        />

        <PrimaryButton v-if="primary === 'copy'" @click="$emit('copyToStation')">
            <font-awesome-icon :icon="['fas', 'copy']"/>
            {{ t('federation.copyToStation') }}
        </PrimaryButton>
        <PrimaryButton v-else-if="primary === 'edit'" @click="$emit('toggleEdit')">
            <font-awesome-icon :icon="['fas', editing ? 'eye' : 'pen']"/>
            {{ editing ? t('kb.preview') : t('kb.edit') }}
        </PrimaryButton>
        <PrimaryButton v-else-if="primary === 'present'" @click="$emit('openPresentation')">
            <font-awesome-icon :icon="['fas', 'display']"/>
            {{ t('kb.present') }}
        </PrimaryButton>
        <PrimaryButton v-else-if="primary === 'pdf'" @click="$emit('downloadPdf')">
            <font-awesome-icon :icon="['fas', 'file-pdf']"/>
            {{ t('kb.downloadPdf') }}
        </PrimaryButton>

        <ActionsMenu v-if="hasMenu" :label="t('common.actions')" test-id="kb-file-actions">
            <DropdownMenuItem v-if="isTextFile && primary !== 'pdf'" :icon="['fas', 'file-pdf']"
                              @click="$emit('downloadPdf')">
                {{ t('kb.downloadPdf') }}
            </DropdownMenuItem>
            <template v-if="!isFederated">
                <DropdownMenuItem v-if="canPresent && primary !== 'present'" :icon="['fas', 'display']"
                                  @click="$emit('openPresentation')">
                    {{ t('kb.present') }}
                </DropdownMenuItem>
                <DropdownMenuItem v-if="hasOriginal" :icon="['fas', 'download']"
                                  @click="$emit('downloadOriginal')">
                    {{ t('kb.downloadOriginal') }}
                </DropdownMenuItem>
                <DropdownMenuItem v-if="hasVersions" :icon="['fas', 'clock-rotate-left']"
                                  @click="$emit('openVersions')">
                    {{ t('kb.versions') }}
                </DropdownMenuItem>
                <DropdownMenuItem v-if="canEdit" :icon="['fas', 'eye']" @click="$emit('openShare')">
                    {{ t('kb.share') }}
                </DropdownMenuItem>
                <DropdownMenuItem v-if="canEdit" :icon="['fas', 'gear']" @click="$emit('openEditMetadata')">
                    {{ t('kb.editMetadata') }}
                </DropdownMenuItem>
                <DropdownMenuItem
                    v-if="canMove"
                    :icon="['fas', 'arrow-right-arrow-left']"
                    data-testid="kb-file-move"
                    @click="$emit('openMove')"
                >
                    {{ t('kb.move') }}
                </DropdownMenuItem>
            </template>
        </ActionsMenu>
    </div>
</template>
