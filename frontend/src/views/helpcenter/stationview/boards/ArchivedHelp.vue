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
import {contrastTextColor} from '@/theme/contrast'

const {t} = useI18n()

const dummyLabels = [
    {name: t('helpCenter.archived.dummyLabel1'), color: '#ec2929'},
    {name: t('helpCenter.archived.dummyLabel2'), color: '#3694FF'},
    {name: t('helpCenter.archived.dummyLabel3'), color: '#00C507'},
]
</script>

<template>
    <HelpArticle :title="t('helpCenter.archived.title')" :subtitle="t('helpCenter.archived.subtitle')">
        <HelpSection :title="t('helpCenter.archived.whatIs')">
            <p>{{ t('helpCenter.archived.whatIsText') }}</p>
            <p>{{ t('helpCenter.archived.whatIsText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.archived.howTo')">
            <p>{{ t('helpCenter.archived.howToText') }}</p>
            <p>{{ t('helpCenter.archived.howToClick') }}</p>
            <p>{{ t('helpCenter.archived.howToBack') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.archived.filterTitle')">
            <p>{{ t('helpCenter.archived.filterText') }}</p>
            <p>{{ t('helpCenter.archived.filterClear') }}</p>
        </HelpSection>

        <!-- Dummy: Archive view with label filter and table -->
        <HelpSection :title="t('helpCenter.archived.exampleTitle')">
            <NeutralContainer class="space-y-4">
                <div class="flex items-center gap-3">
                    <IconButton :icon="['fas', 'chevron-left']" label="Back" />
                    <SectionHeader>PLAN — {{ t('boards.archived') }}</SectionHeader>
                </div>

                <!-- Dummy: Label filter bar -->
                <div class="flex flex-wrap gap-1 items-center">
                    <span
                        v-for="label in dummyLabels"
                        :key="label.name"
                        class="text-xs px-2 py-0.5 rounded-full cursor-pointer transition-all opacity-70 hover:opacity-100"
                        :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }"
                    >{{ label.name }}</span>
                </div>

                <!-- Dummy: Archive table -->
                <table class="w-full text-sm">
                    <thead>
                        <tr class="text-left text-xs text-(--text-muted) uppercase border-b border-(--border)">
                            <th class="py-2 pr-3">ID</th>
                            <th class="py-2 pr-3 w-full">{{ t('boards.ticketTitle') }}</th>
                            <th class="py-2 pr-3">Labels</th>
                            <th class="py-2 pr-3">{{ t('boards.priority') }}</th>
                            <th class="py-2 pr-3">{{ t('boards.assignee') }}</th>
                            <th class="py-2">{{ t('boards.dueDate') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr class="border-b border-(--border) cursor-pointer hover:bg-primary/5">
                            <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">PLAN-3</td>
                            <td class="py-2 pr-3">{{ t('helpCenter.archived.dummyTitle1') }}</td>
                            <td class="py-2 pr-3">
                                <div class="flex gap-1">
                                    <span class="text-[0.6rem] px-1.5 py-0.5 rounded-full" :style="{ backgroundColor: dummyLabels[0].color, color: contrastTextColor(dummyLabels[0].color) }">{{ dummyLabels[0].name }}</span>
                                </div>
                            </td>
                            <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'angles-up']" class="text-xs text-red-500" /></td>
                            <td class="py-2 pr-3">
                                <div class="flex items-center gap-1">
                                    <UserAvatar size="sm" :name="t('helpCenter.archived.dummyName1')" />
                                    <span class="text-xs whitespace-nowrap">{{ t('helpCenter.archived.dummyName1') }}</span>
                                </div>
                            </td>
                            <td class="py-2 text-xs whitespace-nowrap">01.05.2026</td>
                        </tr>
                        <tr class="border-b border-(--border) cursor-pointer hover:bg-primary/5">
                            <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">PLAN-5</td>
                            <td class="py-2 pr-3">{{ t('helpCenter.archived.dummyTitle2') }}</td>
                            <td class="py-2 pr-3">
                                <div class="flex gap-1">
                                    <span class="text-[0.6rem] px-1.5 py-0.5 rounded-full" :style="{ backgroundColor: dummyLabels[1].color, color: contrastTextColor(dummyLabels[1].color) }">{{ dummyLabels[1].name }}</span>
                                </div>
                            </td>
                            <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'equals']" class="text-xs text-yellow-500" /></td>
                            <td class="py-2 pr-3">
                                <div class="flex items-center gap-1">
                                    <UserAvatar size="sm" :name="t('helpCenter.archived.dummyName2')" />
                                    <span class="text-xs whitespace-nowrap">{{ t('helpCenter.archived.dummyName2') }}</span>
                                </div>
                            </td>
                            <td class="py-2 text-xs whitespace-nowrap">10.04.2026</td>
                        </tr>
                        <tr class="border-b border-(--border) last:border-0 cursor-pointer hover:bg-primary/5">
                            <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">PLAN-8</td>
                            <td class="py-2 pr-3">{{ t('helpCenter.archived.dummyTitle3') }}</td>
                            <td class="py-2 pr-3">
                                <div class="flex gap-1">
                                    <span class="text-[0.6rem] px-1.5 py-0.5 rounded-full" :style="{ backgroundColor: dummyLabels[2].color, color: contrastTextColor(dummyLabels[2].color) }">{{ dummyLabels[2].name }}</span>
                                </div>
                            </td>
                            <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'angle-down']" class="text-xs text-blue-400" /></td>
                            <td class="py-2 pr-3">
                                <div class="flex items-center gap-1">
                                    <UserAvatar size="sm" :name="t('helpCenter.archived.dummyName1')" />
                                    <span class="text-xs whitespace-nowrap">{{ t('helpCenter.archived.dummyName1') }}</span>
                                </div>
                            </td>
                            <td class="py-2 text-xs whitespace-nowrap">20.03.2026</td>
                        </tr>
                    </tbody>
                </table>
            </NeutralContainer>
        </HelpSection>

        <!-- Dummy: Empty state -->
        <HelpSection :title="t('helpCenter.archived.emptyTitle')">
            <p>{{ t('helpCenter.archived.emptyText') }}</p>
            <NeutralContainer>
                <EmptyState>{{ t('boards.noTickets') }}</EmptyState>
            </NeutralContainer>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.archived.tip') }}</HelpTip>
    </HelpArticle>
</template>
