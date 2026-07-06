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
import { federatedBoards, memberGroups, userTags } from '@/api'
import type { MemberGroup, UserTag } from '@/api/types'
import { type RestrictionSelection, emptyRestriction } from '@/components/input/restriction'

const { t } = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
    partnerUid: string
    boardKey: string
}>()

const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])

const viewRestriction = ref<RestrictionSelection>(emptyRestriction())
const editRestriction = ref<RestrictionSelection>(emptyRestriction())

const error = ref('')

async function loadData() {
    try {
        const [groups, tags, override] = await Promise.all([
            memberGroups.listGroups(),
            userTags.listTags(),
            federatedBoards.getAccessOverride(props.partnerUid, props.boardKey),
        ])
        allGroups.value = groups
        allTags.value = tags
        viewRestriction.value = {
            userTypes: override.view.userTypes ?? [],
            groupIds: override.view.groupIds ?? [],
            tagIds: override.view.tagIds ?? [],
            memberIds: [],
            mode: 'AND',
        }
        editRestriction.value = {
            userTypes: override.edit.userTypes ?? [],
            groupIds: override.edit.groupIds ?? [],
            tagIds: override.edit.tagIds ?? [],
            memberIds: [],
            mode: 'AND',
        }
    } catch {
        error.value = t('common.error')
    }
}

async function save() {
    error.value = ''
    try {
        await federatedBoards.setAccessOverride(props.partnerUid, props.boardKey, {
            viewUserTypes: viewRestriction.value.userTypes,
            viewGroupIds: viewRestriction.value.groupIds,
            viewTagIds: viewRestriction.value.tagIds,
            editUserTypes: editRestriction.value.userTypes,
            editGroupIds: editRestriction.value.groupIds,
            editTagIds: editRestriction.value.tagIds,
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
                    :groups="allGroups"
                    :tags="allTags"
                    v-model="viewRestriction"
                />
            </div>

            <div>
                <FieldLabel class="mb-2">{{ t('boards.editOverride') }}</FieldLabel>
                <p class="text-xs text-(--text-muted) mb-2">Leer = alle mit Lesezugriff können bearbeiten</p>
                <RestrictionsField
                    :groups="allGroups"
                    :tags="allTags"
                    v-model="editRestriction"
                />
            </div>

            <Alert v-if="error" variant="error">{{ error }}</Alert>

            <div class="flex justify-end">
                <SaveButton :action="save"/>
            </div>
        </div>
    </Modal>
</template>
