/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpPerspective} from '@/components/helpcenter/HelpRoleToggle.vue'
import {StationPermission} from '@/api/generated/schema'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import BoardCard from '@/views/stationview/boards/BoardCard.vue'
import {demoBoards} from './boardshelp/fixtures'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
    {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
    {key: 'manager', label: t('helpCenter.roles.manager'), permissions: [StationPermission.BOARD_MANAGER]},
]
const activeView = ref('')
</script>

<template>
    <HelpArticle :title="t('helpCenter.boardOverview.title')" :subtitle="t('helpCenter.boardOverview.subtitle')">
        <HelpSection :title="t('helpCenter.boardOverview.whatIs')">
            <p>{{ t('helpCenter.boardOverview.whatIsText') }}</p>
            <p>{{ t('helpCenter.boardOverview.whatIsText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardOverview.howTo')">
            <p>{{ t('helpCenter.boardOverview.howToText') }}</p>
            <p>{{ t('helpCenter.boardOverview.howToText2') }}</p>
        </HelpSection>

        <HelpRoleToggle v-model="activeView" :perspectives="perspectives" />

        <template v-if="activeView === 'manager'">
            <HelpSection :title="t('helpCenter.boardOverview.managerTitle')">
                <p>{{ t('helpCenter.boardOverview.managerText') }}</p>
                <p>{{ t('helpCenter.boardOverview.managerCreate') }}</p>
                <p>{{ t('helpCenter.boardOverview.managerSettings') }}</p>
                <p>{{ t('helpCenter.boardOverview.managerDelete') }}</p>
            </HelpSection>
        </template>

        <template v-if="activeView === 'member'">
            <HelpSection :title="t('helpCenter.boardOverview.memberTitle')">
                <p>{{ t('helpCenter.boardOverview.memberText') }}</p>
                <p>{{ t('helpCenter.boardOverview.memberNoActions') }}</p>
            </HelpSection>
        </template>

        <HelpSection :title="t('helpCenter.boardOverview.exampleTitle')">
            <div v-if="activeView === 'manager'" class="flex items-center justify-end mb-6">
                <PrimaryButton>
                    <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                    {{ t('boards.createBoard') }}
                </PrimaryButton>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <BoardCard
                    v-for="board in demoBoards"
                    :key="board.id"
                    :board="board"
                    :manageable="activeView === 'manager'"
                />
            </div>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.boardOverview.tip') }}</HelpTip>
    </HelpArticle>
</template>
