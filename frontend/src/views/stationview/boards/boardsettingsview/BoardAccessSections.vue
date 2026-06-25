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
import type { FederationTarget } from '@/api/boards'
import type { PartnerResponse } from '@/api/federation'

defineProps<{
    allRoles: PermissionGrant[]
    allGroups: MemberGroup[]
    allTags: UserTag[]
    canFederate: boolean
    federationTargets: FederationTarget[]
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
            :title="t('boards.viewAccess')"
            description="Leer = sichtbar für alle Mitglieder"
            :roles="allRoles"
            :groups="allGroups"
            :tags="allTags"
            v-model:selected-user-types="viewUserTypes"
            v-model:selected-group-ids="viewGroupIds"
            v-model:selected-tag-ids="viewTagIds"
        />
        <BoardAccessSection
            :title="t('boards.editAccess')"
            description="Leer = alle mit Lesezugriff können bearbeiten"
            :roles="allRoles"
            :groups="allGroups"
            :tags="allTags"
            v-model:selected-user-types="editUserTypes"
            v-model:selected-group-ids="editGroupIds"
            v-model:selected-tag-ids="editTagIds"
        />
        <BoardFederationSection
            :can-federate="canFederate"
            :targets="federationTargets"
            :available-partners="availablePartners"
            :has-full-mode="hasFullMode"
            v-model:add-partner-id="addPartnerId"
            v-model:federated-edit-user-types="federatedEditUserTypes"
            :role-options="roleOptions"
            :partner-name="partnerName"
            @add="emit('addPartner')"
            @remove="i => emit('removePartner', i)"
        />
    </div>
</template>
