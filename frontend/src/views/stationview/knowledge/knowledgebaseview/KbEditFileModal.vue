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
import type {KbFile} from '@/api/knowledgeBase'
import type {MemberGroup, UserTag} from '@/api/types'
import {useSession} from '@/composables/useSession'
import KbRestrictionsField from './KbRestrictionsField.vue'
import KbPublicVisibilityField from './KbPublicVisibilityField.vue'
import KbTagsEditor from './KbTagsEditor.vue'

const {isKbPublic} = useSession()

const {t} = useI18n()

const props = defineProps<{
    file: KbFile | null
}>()

const show = defineModel<boolean>('show', {required: true})

const emit = defineEmits<{
    saved: []
}>()

const editName = ref('')
const editDescription = ref('')
const selectedUserTypes = ref<string[]>([])
const groupIds = ref<number[]>([])
const tagIds = ref<number[]>([])
const tags = ref<string[]>([])
const publicVisibility = ref<string>('default')
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const error = ref('')

watch(show, async (visible) => {
    if (visible && props.file) {
        editName.value = props.file.name
        editDescription.value = props.file.description
        selectedUserTypes.value = []
        groupIds.value = []
        tagIds.value = []
        tags.value = []
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
            const [r, fileTags, vis] = await Promise.all([
                knowledgeBase.getFileRestrictions(props.file.id),
                knowledgeBase.getFileTags(props.file.id),
                knowledgeBase.getPublicVisibility('files', props.file.id),
            ])
            selectedUserTypes.value = r.userTypes ?? []
            groupIds.value = r.groupIds
            tagIds.value = r.tagIds
            tags.value = fileTags.map(t => t.name)
            publicVisibility.value = vis.visible === true ? 'public' : vis.visible === false ? 'hidden' : 'default'
        } catch {
            error.value = ''
        }
    }
})

async function handleSave() {
    if (!props.file || !editName.value.trim()) return
    try {
        const visValue = publicVisibility.value === 'public' ? true : publicVisibility.value === 'hidden' ? false : null
        await Promise.all([
            knowledgeBase.updateFile(props.file.id, {
                name: editName.value.trim(),
                description: editDescription.value,
            }),
            knowledgeBase.setFileRestrictions(props.file.id, {
                userTypes: selectedUserTypes.value,
                groupIds: groupIds.value,
                tagIds: tagIds.value,
                memberIds: [],
            }),
            knowledgeBase.setFileTags(props.file.id, tags.value),
            knowledgeBase.setPublicVisibility('files', props.file.id, visValue),
        ])
        show.value = false
        emit('saved')
    } catch {
        error.value = t('common.error')
    }
}
</script>

<template>
    <Modal v-model="show">
        <SubHeader class="mb-3">{{ t('kb.editFile') }}</SubHeader>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <TextInput v-model="editName" :placeholder="t('kb.fileName')" required/>
            <TextAreaInput v-model="editDescription" :placeholder="t('kb.description')"/>
            <KbRestrictionsField
                :all-groups="allGroups"
                :all-tags="allTags"
                v-model:selected-user-types="selectedUserTypes"
                v-model:group-ids="groupIds"
                v-model:tag-ids="tagIds"
            />
            <KbPublicVisibilityField v-if="isKbPublic()" v-model="publicVisibility"/>
            <KbTagsEditor v-model="tags"/>
            <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </form>
    </Modal>
</template>
