/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {knowledgeBase, memberGroups, userTags} from '@/api'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {KbFolder} from '@/api/knowledgeBase'
import type {MemberGroup, UserTag} from '@/api/types'
import {useSession} from '@/composables/useSession'
import KbRestrictionsField from './KbRestrictionsField.vue'
import KbPublicVisibilityField from './KbPublicVisibilityField.vue'
import KbTagsEditor from './KbTagsEditor.vue'
import KbFolderIconField from './KbFolderIconField.vue'
import {type RestrictionSelection, emptyRestriction} from '@/components/input/restriction'

const {isKbPublic} = useSession()

const {t} = useI18n()

const show = defineModel<boolean>('show', {required: true})

const props = defineProps<{
    folder: KbFolder | null
}>()

const emit = defineEmits<{
    saved: []
}>()

const editName = ref('')
const editDescription = ref('')
const restriction = ref<RestrictionSelection>(emptyRestriction())
const tags = ref<string[]>([])
const iconFile = ref<File | null>(null)
const publicVisibility = ref<string>('default')
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const error = ref('')

watch(show, async (visible) => {
    if (visible && props.folder) {
        editName.value = props.folder.name
        editDescription.value = props.folder.description
        restriction.value = emptyRestriction()
        tags.value = []
        iconFile.value = null
        publicVisibility.value = 'default'
        error.value = ''

        try {
            const [groupList, tagList] = await Promise.all([
                memberGroups.listGroups(),
                userTags.listTags(),
            ])
            allGroups.value = groupList
            allTags.value = tagList
        } catch {
            error.value = ''
        }

        try {
            const [r, folderTags, vis] = await Promise.all([
                knowledgeBase.getFolderRestrictions(props.folder.id),
                knowledgeBase.getFolderTags(props.folder.id),
                knowledgeBase.getPublicVisibility('folders', props.folder.id),
            ])
            restriction.value = {
                userTypes: r.userTypes ?? [],
                groupIds: r.groupIds,
                tagIds: r.tagIds,
                memberIds: [],
                mode: 'AND',
            }
            tags.value = folderTags.map(t => t.name)
            publicVisibility.value = vis.visible === true ? 'public' : vis.visible === false ? 'hidden' : 'default'
        } catch {
            error.value = ''
        }
    }
})

async function handleSave() {
    if (!props.folder || !editName.value.trim()) return
    try {
        const visValue = publicVisibility.value === 'public' ? true : publicVisibility.value === 'hidden' ? false : null
        const promises: Promise<unknown>[] = [
            knowledgeBase.updateFolder(props.folder.id, {
                name: editName.value.trim(),
                description: editDescription.value,
            }),
            knowledgeBase.setFolderRestrictions(props.folder.id, {
                userTypes: restriction.value.userTypes,
                groupIds: restriction.value.groupIds,
                tagIds: restriction.value.tagIds,
                memberIds: [],
            }),
            knowledgeBase.setFolderTags(props.folder.id, tags.value),
            knowledgeBase.setPublicVisibility('folders', props.folder.id, visValue),
        ]
        if (iconFile.value) {
            promises.push(knowledgeBase.uploadFolderIcon(props.folder.id, iconFile.value))
        }
        await Promise.all(promises)
        show.value = false
        emit('saved')
    } catch {
        error.value = t('common.error')
    }
}
</script>

<template>
    <Modal v-model="show">
        <SubHeader class="mb-3">{{ t('kb.editFolder') }}</SubHeader>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <TextInput v-model="editName" :placeholder="t('kb.folderName')" required/>
            <TextAreaInput v-model="editDescription" :placeholder="t('kb.description')"/>
            <KbFolderIconField v-model="iconFile"/>
            <KbRestrictionsField
                :all-groups="allGroups"
                :all-tags="allTags"
                v-model="restriction"
            />
            <KbPublicVisibilityField v-if="isKbPublic()" v-model="publicVisibility"/>
            <KbTagsEditor v-model="tags"/>
            <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </form>
    </Modal>
</template>
