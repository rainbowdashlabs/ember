/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {StoragePlacement} from '@/api/clusterStorageBackend'

/**
 * Every station of the association, where its files are and where the association's decision says they
 * belong.
 *
 * A row where those two differ is a station whose bytes have not been carried yet, which is a thing to do
 * rather than a thing that is wrong: deciding takes a moment and copying does not.
 */
defineProps<{
    placements: StoragePlacement[]
    movingUid: string | null
}>()

const emit = defineEmits<{
    (e: 'move', stationUid: string): void
}>()

const {t} = useI18n()
</script>

<template>
    <div class="overflow-x-auto">
        <table class="w-full text-sm">
            <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
                <th class="p-2 text-left">{{ t('clusterStorageBackend.placements.station') }}</th>
                <th class="p-2 text-left">{{ t('clusterStorageBackend.placements.actual') }}</th>
                <th class="p-2 text-left">{{ t('clusterStorageBackend.placements.expected') }}</th>
                <th class="p-2 text-right">{{ t('clusterStorageBackend.placements.action') }}</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="placement in placements" :key="placement.stationUid"
                class="border-b border-bg-light-accent dark:border-bg-dark-accent"
                data-testid="storage-placement-row">
                <td class="p-2">
                    <span class="font-medium">{{ placement.name }}</span>
                    <InfoBadge v-if="placement.homeStation" class="ml-2">
                        {{ t('clusterStorageBackend.placements.ownStore') }}
                    </InfoBadge>
                </td>
                <td class="p-2">{{ t(`clusterStorageBackend.placements.where.${placement.actual}`) }}</td>
                <td class="p-2">
                    <SuccessBadge v-if="placement.inPlace" data-testid="placement-in-place">
                        {{ t('clusterStorageBackend.placements.inPlace') }}
                    </SuccessBadge>
                    <span v-else data-testid="placement-out-of-place">
                        {{ t(`clusterStorageBackend.placements.where.${placement.expected}`) }}
                    </span>
                </td>
                <td class="p-2 text-right">
                    <PrimaryButton v-if="!placement.inPlace"
                                   :disabled="movingUid !== null"
                                   data-testid="placement-move"
                                   @click="emit('move', placement.stationUid)">
                        {{
                            movingUid === placement.stationUid
                                ? t('clusterStorageBackend.placements.moving')
                                : t('clusterStorageBackend.placements.move')
                        }}
                    </PrimaryButton>
                    <MutedText v-else>{{ t('clusterStorageBackend.placements.nothingToDo') }}</MutedText>
                </td>
            </tr>
            </tbody>
        </table>
    </div>
</template>
