/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import BoardAccessSection from './BoardAccessSection.vue'
import BoardFederationSection from './BoardFederationSection.vue'
import type { RoleOption } from './BoardFederationSection.vue'
import type { PermissionGrant, MemberGroup, UserTag } from '@/api/types'
import type { FederationTargetResponse, PartnerResponse } from '@/api/generated/schema'

defineProps<{
    allRoles: PermissionGrant[]
    allGroups: MemberGroup[]
    allTags: UserTag[]
    canFederate: boolean
    federationTargets: FederationTargetResponse[]
    availablePartners: PartnerResponse[]
    hasFullMode: boolean
    roleOptions: RoleOption[]
    partnerName: (id: number) => string
}>()

const viewUserTypes = defineModel<string[]>('viewUserTypes', { required: true })
const viewGroupIds = defineModel<number[]>('viewGroupIds', { required: true })
const viewTagIds = defineModel<number[]>('viewTagIds', { required: true })
const editUserTypes = defineModel<string[]>('editUserTypes', { required: true })
const editGroupIds = defineModel<number[]>('editGroupIds', { required: true })
const editTagIds = defineModel<number[]>('editTagIds', { required: true })
const addPartnerId = defineModel<number | null>('addPartnerId', { required: true })
const federatedEditUserTypes = defineModel<string[]>('federatedEditUserTypes', { required: true })

const emit = defineEmits<{
    (e: 'addPartner'): void
    (e: 'removePartner', index: number): void
}>()

const { t } = useI18n()
</script>

<template>
    <div class="space-y-6">
        <BoardAccessSection
            v-model:selected-user-types="viewUserTypes"
            v-model:selected-group-ids="viewGroupIds"
            v-model:selected-tag-ids="viewTagIds"
            :title="t('boards.viewAccess')"
            :description="t('boards.viewOverrideHint')"
            :roles="allRoles"
            :groups="allGroups"
            :tags="allTags"
        />
        <BoardAccessSection
            v-model:selected-user-types="editUserTypes"
            v-model:selected-group-ids="editGroupIds"
            v-model:selected-tag-ids="editTagIds"
            :title="t('boards.editAccess')"
            :description="t('boards.editOverrideHint')"
            :roles="allRoles"
            :groups="allGroups"
            :tags="allTags"
        />
        <BoardFederationSection
            v-model:add-partner-id="addPartnerId"
            v-model:federated-edit-user-types="federatedEditUserTypes"
            :can-federate="canFederate"
            :targets="federationTargets"
            :available-partners="availablePartners"
            :has-full-mode="hasFullMode"
            :role-options="roleOptions"
            :partner-name="partnerName"
            @add="emit('addPartner')"
            @remove="i => emit('removePartner', i)"
        />
    </div>
</template>
