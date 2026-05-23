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
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {knowledgeBase, stationMembers, memberGroups, userTags} from '@/api'
import type {KbFile} from '@/api/knowledgeBase'
import type {Role, MemberGroup, UserTag} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
    show: boolean
    file: KbFile | null
}>()

const emit = defineEmits<{
    'update:show': [value: boolean]
    saved: []
}>()

const editName = ref('')
const editDescription = ref('')
const roleIds = ref<Set<number>>(new Set())
const groupIds = ref<Set<number>>(new Set())
const tagIds = ref<Set<number>>(new Set())
const tags = ref<string[]>([])
const newTag = ref('')
const allRoles = ref<Role[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const error = ref('')

function toggleRole(roleId: number) {
    const next = new Set(roleIds.value)
    if (next.has(roleId)) next.delete(roleId)
    else next.add(roleId)
    roleIds.value = next
}

function toggleGroup(groupId: number) {
    const next = new Set(groupIds.value)
    if (next.has(groupId)) next.delete(groupId)
    else next.add(groupId)
    groupIds.value = next
}

function toggleTag(tagId: number) {
    const next = new Set(tagIds.value)
    if (next.has(tagId)) next.delete(tagId)
    else next.add(tagId)
    tagIds.value = next
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
    if (visible && props.file) {
        editName.value = props.file.name
        editDescription.value = props.file.description
        roleIds.value = new Set()
        groupIds.value = new Set()
        tagIds.value = new Set()
        tags.value = []
        newTag.value = ''
        error.value = ''

        try {
            const [roleList, groupList, tagList] = await Promise.all([
                stationMembers.listAllRoles(),
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
            const [r, fileTags] = await Promise.all([
                knowledgeBase.getFileRestrictions(props.file.id),
                knowledgeBase.getFileTags(props.file.id),
            ])
            roleIds.value = new Set(r.roleIds)
            groupIds.value = new Set(r.groupIds)
            tagIds.value = new Set(r.tagIds)
            tags.value = fileTags.map(t => t.name)
        } catch {
            // ignore
        }
    }
})

async function handleSave() {
    if (!props.file || !editName.value.trim()) return
    try {
        await Promise.all([
            knowledgeBase.updateFile(props.file.id, {
                name: editName.value.trim(),
                description: editDescription.value,
            }),
            knowledgeBase.setFileRestrictions(props.file.id, {
                roleIds: [...roleIds.value],
                groupIds: [...groupIds.value],
                tagIds: [...tagIds.value],
                memberIds: [],
            }),
            knowledgeBase.setFileTags(props.file.id, tags.value),
        ])
        emit('update:show', false)
        emit('saved')
    } catch {
        error.value = t('common.error')
    }
}
</script>

<template>
    <Modal :model-value="show" @update:model-value="emit('update:show', $event)">
        <h3 class="text-lg font-semibold mb-3">{{ t('kb.editFile') }}</h3>
        <form @submit.prevent="handleSave" class="flex flex-col gap-3">
            <TextInput v-model="editName" :placeholder="t('kb.fileName')" required/>
            <TextAreaInput v-model="editDescription" :placeholder="t('kb.description')"/>

            <!-- Restrictions -->
            <div class="space-y-3 border-t border-bg-light-accent dark:border-bg-dark-accent pt-3">
                <h3 class="text-sm font-semibold">{{ t('kb.restrictions') }}</h3>
                <div class="space-y-2">
                    <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionRoles') }}</label>
                    <div class="flex flex-wrap gap-2">
                        <SelectionToggleButton
                            v-for="role in allRoles" :key="role.id"
                            :selected="roleIds.has(role.id)"
                            @toggle="toggleRole(role.id)"
                        >
                            {{ role.role }}
                        </SelectionToggleButton>
                    </div>
                </div>
                <div class="space-y-2">
                    <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionGroups') }}</label>
                    <div class="flex flex-wrap gap-2">
                        <SelectionToggleButton
                            v-for="group in allGroups" :key="group.id"
                            :selected="groupIds.has(group.id)"
                            @toggle="toggleGroup(group.id)"
                        >
                            {{ group.name }}
                        </SelectionToggleButton>
                        <span v-if="allGroups.length === 0" class="text-xs text-[var(--text-muted)]">&#8211;</span>
                    </div>
                </div>
                <div class="space-y-2">
                    <label class="text-xs text-[var(--text-muted)]">{{ t('kb.restrictionTags') }}</label>
                    <div class="flex flex-wrap gap-2">
                        <SelectionToggleButton
                            v-for="tag in allTags" :key="tag.id"
                            :selected="tagIds.has(tag.id)"
                            @toggle="toggleTag(tag.id)"
                        >
                            {{ tag.name }}
                        </SelectionToggleButton>
                        <span v-if="allTags.length === 0" class="text-xs text-[var(--text-muted)]">&#8211;</span>
                    </div>
                </div>
                <p v-if="roleIds.size === 0 && groupIds.size === 0 && tagIds.size === 0"
                   class="text-xs text-[var(--text-muted)] italic">{{ t('kb.restrictionsHint') }}</p>
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
