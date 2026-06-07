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
import {StationPermission} from '@/api/types'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

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

        <!-- Dummy: Board list -->
        <HelpSection :title="t('helpCenter.boardOverview.exampleTitle')">
            <div class="flex items-center justify-between mb-4">
                <SectionHeader>{{ t('boards.manageTitle') }}</SectionHeader>
                <PrimaryButton v-if="activeView === 'manager'">
                    <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                    {{ t('boards.createBoard') }}
                </PrimaryButton>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <!-- Board 1 -->
                <NeutralContainer class="cursor-pointer hover:border-[var(--accent)] transition-colors">
                    <div class="flex items-start justify-between">
                        <div>
                            <div class="flex items-center gap-2 mb-1">
                                <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">PLAN</span>
                                <SubHeader>{{ t('helpCenter.boardOverview.dummyBoardName1') }}</SubHeader>
                            </div>
                            <p class="text-sm text-[var(--text-muted)] line-clamp-2">{{ t('helpCenter.boardOverview.dummyBoardDesc1') }}</p>
                        </div>
                        <div v-if="activeView === 'manager'" class="flex items-center gap-1 shrink-0">
                            <IconButton :icon="['fas', 'gears']" label="Settings" />
                            <DeleteButton />
                        </div>
                    </div>
                    <div class="mt-3 text-xs text-[var(--text-muted)]">12 Tickets</div>
                </NeutralContainer>

                <!-- Board 2 -->
                <NeutralContainer class="cursor-pointer hover:border-[var(--accent)] transition-colors">
                    <div class="flex items-start justify-between">
                        <div>
                            <div class="flex items-center gap-2 mb-1">
                                <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">AUS</span>
                                <SubHeader>{{ t('helpCenter.boardOverview.dummyBoardName2') }}</SubHeader>
                            </div>
                            <p class="text-sm text-[var(--text-muted)] line-clamp-2">{{ t('helpCenter.boardOverview.dummyBoardDesc2') }}</p>
                        </div>
                        <div v-if="activeView === 'manager'" class="flex items-center gap-1 shrink-0">
                            <IconButton :icon="['fas', 'gears']" label="Settings" />
                            <DeleteButton />
                        </div>
                    </div>
                    <div class="mt-3 text-xs text-[var(--text-muted)]">5 Tickets</div>
                </NeutralContainer>

                <!-- Board 3 -->
                <NeutralContainer class="cursor-pointer hover:border-[var(--accent)] transition-colors">
                    <div class="flex items-start justify-between">
                        <div>
                            <div class="flex items-center gap-2 mb-1">
                                <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">EVT</span>
                                <SubHeader>{{ t('helpCenter.boardOverview.dummyBoardName3') }}</SubHeader>
                            </div>
                            <p class="text-sm text-[var(--text-muted)] line-clamp-2">{{ t('helpCenter.boardOverview.dummyBoardDesc3') }}</p>
                        </div>
                        <div v-if="activeView === 'manager'" class="flex items-center gap-1 shrink-0">
                            <IconButton :icon="['fas', 'gears']" label="Settings" />
                            <DeleteButton />
                        </div>
                    </div>
                    <div class="mt-3 text-xs text-[var(--text-muted)]">1 Ticket</div>
                </NeutralContainer>
            </div>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.boardOverview.tip') }}</HelpTip>
    </HelpArticle>
</template>
