/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type { FederationTarget } from '@/api/boards'

defineProps<{
    targets: FederationTarget[]
    partnerName: (id: number) => string
}>()

const emit = defineEmits<{
    (e: 'remove', index: number): void
}>()

const { t } = useI18n()

type SelectValue = string | number | null | undefined

function applyShareMode(target: FederationTarget, value: SelectValue) {
    target.shareMode = value === 'FULL' ? 'FULL' : 'READ_ONLY'
}

function applyRequiredRole(target: FederationTarget, value: SelectValue) {
    target.requiredRole = String(value ?? 'USER')
}
</script>

<template>
    <div class="space-y-2">
        <div v-for="(target, index) in targets" :key="target.partnerId" class="flex items-center gap-2 flex-wrap">
            <span class="font-medium text-sm min-w-24">{{ partnerName(target.partnerId) }}</span>
            <div class="flex items-center gap-1">
                <span class="text-xs text-[var(--text-muted)] shrink-0">{{ t('boards.accessMode') }}:</span>
                <SelectInput :model-value="target.shareMode" @update:model-value="v => applyShareMode(target, v)">
                    <option value="READ_ONLY">{{ t('boards.shareModeReadOnly') }}</option>
                    <option value="FULL">{{ t('boards.shareModeFull') }}</option>
                </SelectInput>
            </div>
            <div class="flex items-center gap-1">
                <span class="text-xs text-[var(--text-muted)] shrink-0">{{ t('boards.minViewRole') }}:</span>
                <SelectInput :model-value="target.requiredRole" @update:model-value="v => applyRequiredRole(target, v)">
                    <option value="USER">{{ t('boards.requiredRoleUser') }}</option>
                    <option value="TEAM">{{ t('boards.requiredRoleTeam') }}</option>
                    <option value="MANAGER">{{ t('boards.requiredRoleManager') }}</option>
                </SelectInput>
            </div>
            <DeleteButton @click="emit('remove', index)" />
        </div>
    </div>
</template>
