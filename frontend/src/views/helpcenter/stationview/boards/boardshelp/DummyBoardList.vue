/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DummyBoardCard from './DummyBoardCard.vue'

defineProps<{
    isManager: boolean
}>()

const {t} = useI18n()
</script>

<template>
    <div class="flex items-center justify-between mb-4">
        <SectionHeader>{{ t('boards.manageTitle') }}</SectionHeader>
        <PrimaryButton v-if="isManager">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('boards.createBoard') }}
        </PrimaryButton>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <DummyBoardCard
            prefix="PLAN"
            :name="t('helpCenter.boardOverview.dummyBoardName1')"
            :description="t('helpCenter.boardOverview.dummyBoardDesc1')"
            ticket-count="12 Tickets"
            :show-manager-actions="isManager"
        />
        <DummyBoardCard
            prefix="AUS"
            :name="t('helpCenter.boardOverview.dummyBoardName2')"
            :description="t('helpCenter.boardOverview.dummyBoardDesc2')"
            ticket-count="5 Tickets"
            :show-manager-actions="isManager"
        />
        <DummyBoardCard
            prefix="EVT"
            :name="t('helpCenter.boardOverview.dummyBoardName3')"
            :description="t('helpCenter.boardOverview.dummyBoardDesc3')"
            ticket-count="1 Ticket"
            :show-manager-actions="isManager"
        />
    </div>
</template>
