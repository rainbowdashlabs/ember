/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import { federatedBoards, stationMembers, memberGroups, userTags } from '@/api'
import type { PermissionGrant, MemberGroup, UserTag } from '@/api/types'

const { t } = useI18n()

const props = defineProps<{
    partnerUid: string
    boardKey: string
    modelValue: boolean
}>()

const emit = defineEmits<{
    'update:modelValue': [value: boolean]
}>()

const allRoles = ref<PermissionGrant[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])

const viewUserTypes = ref<string[]>([])
const viewGroupIds = ref<number[]>([])
const viewTagIds = ref<number[]>([])
const editUserTypes = ref<string[]>([])
const editGroupIds = ref<number[]>([])
const editTagIds = ref<number[]>([])

const error = ref('')
const saved = ref(false)

async function loadData() {
    try {
        const [roles, groups, tags, override] = await Promise.all([
            stationMembers.listAllPermissions(),
            memberGroups.listGroups(),
            userTags.listTags(),
            federatedBoards.getAccessOverride(props.partnerUid, props.boardKey),
        ])
        allRoles.value = roles
        allGroups.value = groups
        allTags.value = tags
        viewUserTypes.value = override.view.userTypes ?? []
        viewGroupIds.value = override.view.groupIds ?? []
        viewTagIds.value = override.view.tagIds ?? []
        editUserTypes.value = override.edit.userTypes ?? []
        editGroupIds.value = override.edit.groupIds ?? []
        editTagIds.value = override.edit.tagIds ?? []
    } catch {
        error.value = t('common.error')
    }
}

async function save() {
    error.value = ''
    saved.value = false
    try {
        await federatedBoards.setAccessOverride(props.partnerUid, props.boardKey, {
            viewUserTypes: viewUserTypes.value,
            viewGroupIds: viewGroupIds.value,
            viewTagIds: viewTagIds.value,
            editUserTypes: editUserTypes.value,
            editGroupIds: editGroupIds.value,
            editTagIds: editTagIds.value,
        })
        saved.value = true
        setTimeout(() => { saved.value = false }, 2000)
    } catch {
        error.value = t('common.error')
    }
}

onMounted(loadData)
</script>

<template>
    <Modal :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
        <SubHeader class="mb-4">{{ t('boards.accessOverride') }}</SubHeader>
        <p class="text-sm text-(--text-muted) mb-4">{{ t('boards.accessOverrideDesc') }}</p>

        <div class="space-y-6">
            <div>
                <FieldLabel class="mb-2">{{ t('boards.viewOverride') }}</FieldLabel>
                <p class="text-xs text-(--text-muted) mb-2">Leer = sichtbar für alle Mitglieder</p>
                <RestrictionPicker
                    :roles="allRoles"
                    :groups="allGroups"
                    :tags="allTags"
                    :selected-user-types="viewUserTypes"
                    :selected-group-ids="viewGroupIds"
                    :selected-tag-ids="viewTagIds"
                    @update:selected-user-types="viewUserTypes = $event"
                    @update:selected-group-ids="viewGroupIds = $event"
                    @update:selected-tag-ids="viewTagIds = $event"
                />
            </div>

            <div>
                <FieldLabel class="mb-2">{{ t('boards.editOverride') }}</FieldLabel>
                <p class="text-xs text-(--text-muted) mb-2">Leer = alle mit Lesezugriff können bearbeiten</p>
                <RestrictionPicker
                    :roles="allRoles"
                    :groups="allGroups"
                    :tags="allTags"
                    :selected-user-types="editUserTypes"
                    :selected-group-ids="editGroupIds"
                    :selected-tag-ids="editTagIds"
                    @update:selected-user-types="editUserTypes = $event"
                    @update:selected-group-ids="editGroupIds = $event"
                    @update:selected-tag-ids="editTagIds = $event"
                />
            </div>

            <Alert v-if="saved" variant="success">{{ t('common.saved') }}</Alert>
            <Alert v-if="error" variant="error">{{ error }}</Alert>

            <div class="flex justify-end">
                <PrimaryButton @click="save">{{ t('common.save') }}</PrimaryButton>
            </div>
        </div>
    </Modal>
</template>
