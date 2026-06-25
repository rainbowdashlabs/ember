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
import SaveButton from '@/components/button/SaveButton.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import { federatedBoards, stationMembers, memberGroups, userTags } from '@/api'
import type { PermissionGrant, MemberGroup, UserTag } from '@/api/types'

const { t } = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
    partnerUid: string
    boardKey: string
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
    try {
        await federatedBoards.setAccessOverride(props.partnerUid, props.boardKey, {
            viewUserTypes: viewUserTypes.value,
            viewGroupIds: viewGroupIds.value,
            viewTagIds: viewTagIds.value,
            editUserTypes: editUserTypes.value,
            editGroupIds: editGroupIds.value,
            editTagIds: editTagIds.value,
        })
    } catch (e) {
        error.value = t('common.error')
        throw e
    }
}

onMounted(loadData)
</script>

<template>
    <Modal v-model="modelValue">
        <SubHeader class="mb-4">{{ t('boards.accessOverride') }}</SubHeader>
        <p class="text-sm text-(--text-muted) mb-4">{{ t('boards.accessOverrideDesc') }}</p>

        <div class="space-y-6">
            <div>
                <FieldLabel class="mb-2">{{ t('boards.viewOverride') }}</FieldLabel>
                <p class="text-xs text-(--text-muted) mb-2">Leer = sichtbar für alle Mitglieder</p>
                <RestrictionsField
                    :roles="allRoles"
                    :groups="allGroups"
                    :tags="allTags"
                    v-model:selected-user-types="viewUserTypes"
                    v-model:selected-group-ids="viewGroupIds"
                    v-model:selected-tag-ids="viewTagIds"
                />
            </div>

            <div>
                <FieldLabel class="mb-2">{{ t('boards.editOverride') }}</FieldLabel>
                <p class="text-xs text-(--text-muted) mb-2">Leer = alle mit Lesezugriff können bearbeiten</p>
                <RestrictionsField
                    :roles="allRoles"
                    :groups="allGroups"
                    :tags="allTags"
                    v-model:selected-user-types="editUserTypes"
                    v-model:selected-group-ids="editGroupIds"
                    v-model:selected-tag-ids="editTagIds"
                />
            </div>

            <Alert v-if="error" variant="error">{{ error }}</Alert>

            <div class="flex justify-end">
                <SaveButton :action="save"/>
            </div>
        </div>
    </Modal>
</template>
