/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import BoardSearchBox from './BoardSearchBox.vue'
import type { BoardLabel, BoardTicket } from '@/api/boards'
import { useSession } from '@/composables/useSession'

const props = defineProps<{
    boardKey: string
    shortKey: string
    labelsForTicket: (ticketId: number) => BoardLabel[]
    laneName: (laneId: number) => string
}>()

const emit = defineEmits<{
    create: []
    'open-ticket': [ticket: BoardTicket]
}>()

const { t } = useI18n()
const router = useRouter()
const { canManageBoards } = useSession()

function openSettings() {
    router.push(`/station/boards/${props.shortKey}/settings`)
}
</script>

<template>
    <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div class="flex items-center gap-3">
            <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ shortKey }}</span>
        </div>
        <div class="flex items-center gap-2">
            <BoardSearchBox
                :board-key="boardKey"
                :short-key="shortKey"
                :labels-for-ticket="labelsForTicket"
                :lane-name="laneName"
                @select="ticket => emit('open-ticket', ticket)"
            />
            <PrimaryButton @click="emit('create')">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                {{ t('boards.createTicket') }}
            </PrimaryButton>
            <IconButton
                v-if="canManageBoards()"
                :icon="['fas', 'gears']"
                :label="t('boards.settings')"
                @click="openSettings"
            />
        </div>
    </div>
</template>
