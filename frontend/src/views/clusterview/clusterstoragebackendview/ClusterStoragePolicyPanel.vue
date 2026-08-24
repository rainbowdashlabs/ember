/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import {ClusterBackendReach, type ClusterBackendReachName} from '@/api/clusterStorageBackend'

/**
 * The two settings that are the whole of what an association decides about storage: how far its own reaches,
 * and whether a station may point itself anywhere.
 *
 * Giving the storage up is beside them rather than inside the reach, because it is a different act: the
 * reach says what the storage is for and giving it up says there is none, and whoever is standing on the old
 * one keeps reaching their files either way.
 */
defineProps<{
    saving: boolean
    hasBackend: boolean
}>()

const emit = defineEmits<{
    (e: 'save'): void
    (e: 'drop'): void
}>()

const reach = defineModel<ClusterBackendReachName>('reach', {required: true})
const locked = defineModel<boolean>('locked', {required: true})

const {t} = useI18n()
</script>

<template>
    <NeutralContainer class="space-y-4">
        <SubHeader>{{ t('clusterStorageBackend.policy.title') }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t('clusterStorageBackend.policy.hint') }}</MutedText>

        <div class="space-y-1">
            <FieldLabel>{{ t('clusterStorageBackend.policy.reach') }}</FieldLabel>
            <SelectInput v-model="reach" data-testid="cluster-storage-reach">
                <option :value="ClusterBackendReach.NONE">
                    {{ t('clusterStorageBackend.policy.reaches.NONE') }}
                </option>
                <option :value="ClusterBackendReach.OWN_FILES">
                    {{ t('clusterStorageBackend.policy.reaches.OWN_FILES') }}
                </option>
                <option :value="ClusterBackendReach.EVERY_STATION">
                    {{ t('clusterStorageBackend.policy.reaches.EVERY_STATION') }}
                </option>
            </SelectInput>
        </div>

        <div class="flex items-center justify-between gap-4">
            <div>
                <FieldLabel>{{ t('clusterStorageBackend.policy.locked') }}</FieldLabel>
                <MutedText tag="p" size="sm">{{ t('clusterStorageBackend.policy.lockedHint') }}</MutedText>
            </div>
            <ToggleInput v-model="locked" data-testid="cluster-storage-lock"/>
        </div>

        <div class="flex flex-wrap items-center gap-3">
            <PrimaryButton :disabled="saving" data-testid="cluster-storage-policy-save" @click="emit('save')">
                {{ t('clusterStorageBackend.policy.save') }}
            </PrimaryButton>
            <ErrorButton v-if="hasBackend" :disabled="saving" data-testid="cluster-storage-drop"
                         @click="emit('drop')">
                {{ t('clusterStorageBackend.policy.drop') }}
            </ErrorButton>
        </div>
    </NeutralContainer>
</template>
