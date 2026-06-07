/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'

const {t} = useI18n()
</script>

<template>
    <HelpArticle :title="t('helpCenter.backlog.title')" :subtitle="t('helpCenter.backlog.subtitle')">
        <HelpSection :title="t('helpCenter.backlog.whatIs')">
            <p>{{ t('helpCenter.backlog.whatIsText') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.backlog.howTo')">
            <p>{{ t('helpCenter.backlog.howToText') }}</p>
            <p>{{ t('helpCenter.backlog.howToClick') }}</p>
            <p>{{ t('helpCenter.backlog.howToBack') }}</p>
        </HelpSection>

        <!-- Dummy: Back button + header -->
        <HelpSection :title="t('helpCenter.backlog.exampleTitle')">
            <NeutralContainer class="space-y-4">
                <div class="flex items-center gap-3">
                    <IconButton :icon="['fas', 'chevron-left']" label="Back" />
                    <SectionHeader>PLAN — {{ t('boards.backlogTitle') }}</SectionHeader>
                </div>

                <!-- Dummy: Backlog table -->
                <table class="w-full text-sm">
                    <thead>
                        <tr class="text-left text-xs text-(--text-muted) uppercase border-b border-(--border)">
                            <th class="py-2 pr-3">ID</th>
                            <th class="py-2 pr-3 w-full">{{ t('boards.ticketTitle') }}</th>
                            <th class="py-2 pr-3">{{ t('boards.priority') }}</th>
                            <th class="py-2 pr-3">{{ t('boards.assignee') }}</th>
                            <th class="py-2">{{ t('boards.dueDate') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr class="border-b border-(--border) cursor-pointer hover:bg-primary/5">
                            <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">PLAN-7</td>
                            <td class="py-2 pr-3">{{ t('helpCenter.backlog.dummyTitle1') }}</td>
                            <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'angles-up']" class="text-xs text-red-500" /></td>
                            <td class="py-2 pr-3">
                                <div class="flex items-center gap-1">
                                    <UserAvatar size="sm" :name="t('helpCenter.backlog.dummyName1')" />
                                    <span class="text-xs whitespace-nowrap">{{ t('helpCenter.backlog.dummyName1') }}</span>
                                </div>
                            </td>
                            <td class="py-2 text-xs whitespace-nowrap">15.07.2026</td>
                        </tr>
                        <tr class="border-b border-(--border) cursor-pointer hover:bg-primary/5">
                            <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">PLAN-12</td>
                            <td class="py-2 pr-3">{{ t('helpCenter.backlog.dummyTitle2') }}</td>
                            <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'equals']" class="text-xs text-yellow-500" /></td>
                            <td class="py-2 pr-3">
                                <div class="flex items-center gap-1">
                                    <UserAvatar size="sm" :name="t('helpCenter.backlog.dummyName2')" />
                                    <span class="text-xs whitespace-nowrap">{{ t('helpCenter.backlog.dummyName2') }}</span>
                                </div>
                            </td>
                            <td class="py-2 text-xs whitespace-nowrap">22.07.2026</td>
                        </tr>
                        <tr class="border-b border-(--border) last:border-0 cursor-pointer hover:bg-primary/5">
                            <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">PLAN-15</td>
                            <td class="py-2 pr-3">{{ t('helpCenter.backlog.dummyTitle3') }}</td>
                            <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'angle-down']" class="text-xs text-blue-400" /></td>
                            <td class="py-2 pr-3"></td>
                            <td class="py-2 text-xs whitespace-nowrap"></td>
                        </tr>
                    </tbody>
                </table>
            </NeutralContainer>
        </HelpSection>

        <!-- Dummy: Empty state -->
        <HelpSection :title="t('helpCenter.backlog.emptyTitle')">
            <p>{{ t('helpCenter.backlog.emptyText') }}</p>
            <NeutralContainer>
                <EmptyState>{{ t('boards.noTickets') }}</EmptyState>
            </NeutralContainer>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.backlog.tip') }}</HelpTip>
    </HelpArticle>
</template>
