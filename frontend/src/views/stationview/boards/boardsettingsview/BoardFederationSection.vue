/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import BoardFederationTargetList from './BoardFederationTargetList.vue'
import type { FederationTarget } from '@/api/boards'
import type { PartnerResponse } from '@/api/federation'

export interface RoleOption {
    value: string
    label: string
}

const props = defineProps<{
    canFederate: boolean
    targets: FederationTarget[]
    availablePartners: PartnerResponse[]
    hasFullMode: boolean
    addPartnerId: number | null
    federatedEditUserTypes: string[]
    roleOptions: RoleOption[]
    partnerName: (id: number) => string
}>()

const emit = defineEmits<{
    (e: 'update:addPartnerId', value: number | null): void
    (e: 'update:federatedEditUserTypes', value: string[]): void
    (e: 'add'): void
    (e: 'remove', index: number): void
}>()

const { t } = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.federation') }}</SubHeader>
        <template v-if="canFederate">
            <p class="text-xs text-[var(--text-muted)] mb-3">{{ t('boards.federationShareDesc') }}</p>
            <BoardFederationTargetList :targets="targets" :partner-name="partnerName" @remove="index => emit('remove', index)" />
            <div v-if="availablePartners.length > 0" class="flex gap-2 mt-3">
                <SelectInput :model-value="String(addPartnerId ?? '')" class="flex-1" @update:model-value="v => emit('update:addPartnerId', v ? Number(v) : null)">
                    <option value="">{{ t('boards.federationShare') }}...</option>
                    <option v-for="p in availablePartners" :key="p.partner.id" :value="String(p.partner.id)">{{ p.partnerStationName }}</option>
                </SelectInput>
                <SecondaryButton :disabled="addPartnerId == null" @click="emit('add')">
                    <font-awesome-icon :icon="['fas', 'plus']" />
                </SecondaryButton>
            </div>
            <div v-if="hasFullMode" class="mt-4">
                <FieldLabel class="mb-1">{{ t('boards.federatedEditRoles') }}</FieldLabel>
                <p class="text-xs text-[var(--text-muted)] mb-2">{{ t('boards.federatedEditRolesDesc') }}</p>
                <MultiSelectDropdown
                    :options="roleOptions"
                    :model-value="federatedEditUserTypes"
                    @update:model-value="emit('update:federatedEditUserTypes', $event)"
                />
            </div>
        </template>
        <template v-else>
            <p class="text-xs text-[var(--text-muted)] italic">{{ t('boards.federationNoPermission') }}</p>
            <div v-if="targets.length > 0" class="space-y-1 mt-2">
                <div v-for="target in targets" :key="target.partnerId" class="text-sm text-[var(--text-muted)]">
                    {{ partnerName(target.partnerId) }} — {{ target.shareMode }}
                </div>
            </div>
        </template>
    </NeutralContainer>
</template>
