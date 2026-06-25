/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SubHeader from '@/components/typography/SubHeader.vue'
import FederatedBoardCard from './FederatedBoardCard.vue'
import type {DiscoveredBoard} from '@/api/federatedBoards'

defineProps<{
    stationName: string
    stationBoards: DiscoveredBoard[]
    isBookmarked: (partnerStationUid: string, remoteBoardUid: string) => boolean
}>()

const emit = defineEmits<{
    'toggle-bookmark': [board: DiscoveredBoard]
    'navigate': [board: DiscoveredBoard]
}>()
</script>

<template>
    <div>
        <SubHeader class="mb-3">{{ stationName }}</SubHeader>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <FederatedBoardCard
                v-for="board in stationBoards"
                :key="`${board.partnerStationUid}:${board.remoteBoardUid}`"
                :board="board"
                :bookmarked="isBookmarked(board.partnerStationUid, board.remoteBoardUid)"
                @toggle-bookmark="emit('toggle-bookmark', $event)"
                @navigate="emit('navigate', $event)"
            />
        </div>
    </div>
</template>
