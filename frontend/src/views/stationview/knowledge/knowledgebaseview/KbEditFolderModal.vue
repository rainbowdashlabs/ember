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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {knowledgeBase, stationMembers, memberGroups, userTags} from '@/api'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {KbFolder} from '@/api/knowledgeBase'
import type {PermissionGrant, MemberGroup, UserTag} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {isKbPublic} = useSession()

const {t} = useI18n()

const props = defineProps<{
    show: boolean
    folder: KbFolder | null
}>()

const emit = defineEmits<{
    'update:show': [value: boolean]
    saved: []
}>()

const editName = ref('')
const editDescription = ref('')
const roleIds = ref<number[]>([])
const groupIds = ref<number[]>([])
const tagIds = ref<number[]>([])
const tags = ref<string[]>([])
const newTag = ref('')
const iconFile = ref<File | null>(null)
const publicVisibility = ref<string>('default')
const allRoles = ref<PermissionGrant[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const error = ref('')

function onIconSelect(event: Event) {
    const input = event.target as HTMLInputElement
    iconFile.value = input.files?.[0] ?? null
}

function addTag() {
    if (newTag.value.trim()) {
        tags.value.push(newTag.value.trim())
        newTag.value = ''
    }
}

function removeTag(tag: string) {
    tags.value = tags.value.filter(t => t !== tag)
}

watch(() => props.show, async (visible) => {
    if (visible && props.folder) {
        editName.value = props.folder.name
        editDescription.value = props.folder.description
        roleIds.value = []
        groupIds.value = []
        tagIds.value = []
        tags.value = []
        newTag.value = ''
        iconFile.value = null
        publicVisibility.value = 'default'
        error.value = ''

        try {
            const [roleList, groupList, tagList] = await Promise.all([
                stationMembers.listAllPermissions(),
                memberGroups.listGroups(),
                userTags.listTags(),
            ])
            allRoles.value = roleList
            allGroups.value = groupList
            allTags.value = tagList
        } catch {
            // ignore
        }

        try {
            const [r, folderTags, vis] = await Promise.all([
                knowledgeBase.getFolderRestrictions(props.folder.id),
                knowledgeBase.getFolderTags(props.folder.id),
                knowledgeBase.getPublicVisibility('folders', props.folder.id),
            ])
            roleIds.value = r.roleIds
            groupIds.value = r.groupIds
            tagIds.value = r.tagIds
            tags.value = folderTags.map(t => t.name)
            publicVisibility.value = vis.visible === true ? 'public' : vis.visible === false ? 'hidden' : 'default'
        } catch {
            // ignore
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
                roleIds: roleIds.value,
                groupIds: groupIds.value,
                tagIds: tagIds.value,
                memberIds: [],
            }),
            knowledgeBase.setFolderTags(props.folder.id, tags.value),
            knowledgeBase.setPublicVisibility('folders', props.folder.id, visValue),
        ]
        if (iconFile.value) {
            promises.push(knowledgeBase.uploadFolderIcon(props.folder.id, iconFile.value))
        }
        await Promise.all(promises)
        emit('update:show', false)
        emit('saved')
    } catch {
        error.value = t('common.error')
    }
}
</script>

<template>
    <Modal :model-value="show" @update:model-value="emit('update:show', $event)">
        <SubHeader class="mb-3">{{ t('kb.editFolder') }}</SubHeader>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <TextInput v-model="editName" :placeholder="t('kb.folderName')" required/>
            <TextAreaInput v-model="editDescription" :placeholder="t('kb.description')"/>

            <!-- Folder icon -->
            <div class="space-y-2">
                <label class="text-xs text-[var(--text-muted)]">{{ t('kb.folderIcon') }}</label>
                <input
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    class="block w-full text-sm text-[var(--text)] file:mr-4 file:py-1 file:px-3 file:rounded file:border-0 file:text-xs file:font-semibold file:bg-[var(--primary)] file:text-[var(--color-primary-text)] cursor-pointer"
                    @change="onIconSelect"
                />
            </div>

            <!-- Restrictions -->
            <div class="space-y-3 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                <SubHeader class="text-sm">{{ t('kb.restrictions') }}</SubHeader>
                <RestrictionPicker
                    :roles="allRoles"
                    :groups="allGroups"
                    :tags="allTags"
                    :selected-role-ids="roleIds"
                    :selected-group-ids="groupIds"
                    :selected-tag-ids="tagIds"
                    @update:selected-role-ids="roleIds = $event"
                    @update:selected-group-ids="groupIds = $event"
                    @update:selected-tag-ids="tagIds = $event"
                />
            </div>

            <!-- Public visibility override -->
            <div v-if="isKbPublic()" class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                <SubHeader class="text-sm">{{ t('kb.publicVisibility') }}</SubHeader>
                <SelectInput v-model="publicVisibility">
                    <option value="default">{{ t('kb.publicVisibilityDefault') }}</option>
                    <option value="public">{{ t('kb.publicVisibilityPublic') }}</option>
                    <option value="hidden">{{ t('kb.publicVisibilityHidden') }}</option>
                </SelectInput>
            </div>

            <!-- Tags -->
            <div class="space-y-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                <label class="text-sm font-semibold">{{ t('kb.tags') }}</label>
                <div class="flex flex-wrap gap-1.5">
                    <span v-for="tag in tags" :key="tag"
                          class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-[var(--bg-accent)] text-[var(--text)]">
                        {{ tag }}
                        <IconButton
                            :icon="['fas', 'xmark']"
                            :label="t('common.remove')"
                            class="!p-0 !text-[10px]"
                            @click="removeTag(tag)"
                        />
                    </span>
                </div>
                <form class="flex gap-2" @submit.prevent="addTag">
                    <TextInput v-model="newTag" :placeholder="t('kb.tagPlaceholder')" class="flex-1"/>
                    <SecondaryButton type="submit" :disabled="!newTag.trim()">
                        <font-awesome-icon :icon="['fas', 'plus']"/>
                    </SecondaryButton>
                </form>
            </div>

            <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </form>
    </Modal>
</template>
