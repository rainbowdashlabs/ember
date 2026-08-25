/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {knowledgeBase} from '@/api'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'
import {useSession} from '@/composables/useSession'
import KbRestrictionsField from './KbRestrictionsField.vue'
import KbPublicVisibilityField from './KbPublicVisibilityField.vue'
import {useKbEntrySharing} from './useKbEntrySharing'
import {useKbStationAudience} from './useKbStationAudience'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {KbEntryApi} from './useKbEntryEditor'

const {t} = useI18n()
const {isKbPublic} = useSession()

const props = defineProps<{
    entry: KbFile | KbFolder | null
    kind: 'files' | 'folders'
    /** Whether this wiki can aim an entry at named stations, which only an association's does. */
    aimsAtStations?: boolean
}>()

const show = defineModel<boolean>('show', {required: true})

const emit = defineEmits<{
    saved: []
}>()

const FILE_API: KbEntryApi = {
    visibilityKind: 'files',
    getRestrictions: knowledgeBase.getFileRestrictions,
    setRestrictions: knowledgeBase.setFileRestrictions,
    getTags: knowledgeBase.getFileTags,
    setTags: knowledgeBase.setFileTags,
    update: knowledgeBase.updateFile,
}

const FOLDER_API: KbEntryApi = {
    visibilityKind: 'folders',
    getRestrictions: knowledgeBase.getFolderRestrictions,
    setRestrictions: knowledgeBase.setFolderRestrictions,
    getTags: knowledgeBase.getFolderTags,
    setTags: knowledgeBase.setFolderTags,
    update: knowledgeBase.updateFolder,
}

const {
    restriction,
    grantLevels,
    publicVisibility,
    allGroups,
    allTags,
    save,
} = useKbEntrySharing(show, () => props.entry, props.kind === 'files' ? FILE_API : FOLDER_API)

const audience = useKbStationAudience(
    show, () => props.entry, props.aimsAtStations === true, props.kind)

async function handleSave() {
    if (props.aimsAtStations) await audience.save()
    if (await save()) emit('saved')
}
</script>

<template>
    <Modal v-model="show">
        <SubHeader class="mb-1">{{ t('kb.share') }}</SubHeader>
        <p class="mb-3 text-xs text-(--text-muted)">{{ t('kb.shareHint') }}</p>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <div v-if="aimsAtStations" class="space-y-2">
                <div class="flex items-center justify-between">
                    <span class="text-sm font-medium">{{ t('kb.everyStation') }}</span>
                    <ToggleInput v-model="audience.everyStation.value"/>
                </div>
                <div v-if="!audience.everyStation.value" class="space-y-1">
                    <FieldLabel>{{ t('kb.forStations') }}</FieldLabel>
                    <label
                        v-for="station in audience.stations.value"
                        :key="station.partnerId"
                        class="flex items-center gap-2 text-sm"
                    >
                        <input
                            type="checkbox"
                            :checked="audience.chosen.value.includes(station.partnerId)"
                            @change="audience.toggle(station.partnerId)"
                        />
                        {{ station.name }}
                    </label>
                </div>
            </div>

            <KbRestrictionsField
                :all-groups="allGroups"
                :all-tags="allTags"
                v-model="restriction"
                v-model:levels="grantLevels"
            />
            <KbPublicVisibilityField :disabled="!isKbPublic()" v-model="publicVisibility"/>
            <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </form>
    </Modal>
</template>
