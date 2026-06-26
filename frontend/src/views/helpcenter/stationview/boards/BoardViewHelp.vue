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
import HelpList from '@/components/helpcenter/HelpList.vue'
import DummyKanbanBoard from '@/views/helpcenter/stationview/boards/boardviewhelp/DummyKanbanBoard.vue'

const {t} = useI18n()
</script>

<template>
    <HelpArticle :title="t('helpCenter.boardView.title')" :subtitle="t('helpCenter.boardView.subtitle')">
        <HelpSection :title="t('helpCenter.boardView.whatIs')">
            <p>{{ t('helpCenter.boardView.whatIsText') }}</p>
            <p>{{ t('helpCenter.boardView.whatIsText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.lanesTitle')">
            <p>{{ t('helpCenter.boardView.lanesText') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.dragTitle')">
            <p>{{ t('helpCenter.boardView.dragText') }}</p>
            <p>{{ t('helpCenter.boardView.dragText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.ticketTitle')">
            <p>{{ t('helpCenter.boardView.ticketText') }}</p>
            <HelpList>
                <li>{{ t('helpCenter.boardView.ticketItem1') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem2') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem3') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem4') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem5') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem6') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem7') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem8') }}</li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.timeDotsTitle')">
            <p>{{ t('helpCenter.boardView.timeDotsText') }}</p>
            <HelpList>
                <li>
                    <span class="inline-flex gap-0.5 mr-1 align-middle"><span class="w-1.5 h-1.5 rounded-full bg-green-500 inline-block" /><span class="w-1.5 h-1.5 rounded-full bg-green-500 inline-block" /></span>
                    {{ t('helpCenter.boardView.timeDots1') }}
                </li>
                <li>
                    <span class="inline-flex gap-0.5 mr-1 align-middle"><span class="w-1.5 h-1.5 rounded-full bg-orange-500 inline-block" /><span class="w-1.5 h-1.5 rounded-full bg-orange-500 inline-block" /><span class="w-1.5 h-1.5 rounded-full bg-orange-500 inline-block" /></span>
                    {{ t('helpCenter.boardView.timeDots2') }}
                </li>
                <li>
                    <span class="inline-flex gap-0.5 mr-1 align-middle"><span class="w-1.5 h-1.5 rounded-full bg-red-500 inline-block" /><span class="w-1.5 h-1.5 rounded-full bg-red-500 inline-block" /><span class="w-1.5 h-1.5 rounded-full bg-red-500 inline-block" /><span class="w-1.5 h-1.5 rounded-full bg-red-500 inline-block" /></span>
                    {{ t('helpCenter.boardView.timeDots3') }}
                </li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.prioritiesTitle')">
            <p>{{ t('helpCenter.boardView.prioritiesText') }}</p>
            <HelpList>
                <li class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'angles-up']" class="text-red-500 text-xs w-4 text-center"/>
                    {{ t('boards.priorityHighest') }}
                </li>
                <li class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'angle-up']" class="text-orange-500 text-xs w-4 text-center"/>
                    {{ t('boards.priorityHigh') }}
                </li>
                <li class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'equals']" class="text-yellow-500 text-xs w-4 text-center"/>
                    {{ t('boards.priorityMedium') }}
                </li>
                <li class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'angle-down']" class="text-blue-400 text-xs w-4 text-center"/>
                    {{ t('boards.priorityLow') }}
                </li>
                <li class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'angles-down']" class="text-gray-400 text-xs w-4 text-center"/>
                    {{ t('boards.priorityLowest') }}
                </li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.labelsTitle')">
            <p>{{ t('helpCenter.boardView.labelsText') }}</p>
            <div class="flex flex-wrap gap-1.5">
                <BaseBadge bg-class="bg-blue-500" class="text-white">Planung</BaseBadge>
                <BaseBadge bg-class="bg-green-600" class="text-white">Wartung</BaseBadge>
                <BaseBadge bg-class="bg-orange-500" class="text-white">Dringend</BaseBadge>
                <BaseBadge bg-class="bg-purple-500" class="text-white">Ausbildung</BaseBadge>
                <BaseBadge bg-class="bg-rose-500" class="text-white">Einsatz</BaseBadge>
            </div>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.checklistProgressTitle')">
            <p>{{ t('helpCenter.boardView.checklistProgressText') }}</p>
            <div class="space-y-2 max-w-sm">
                <div>
                    <div class="text-xs text-(--text-muted) mb-1">{{ t('helpCenter.boardView.checklistProgressPartial') }}</div>
                    <div class="flex items-center gap-2">
                        <div class="flex-1 h-1.5 bg-[var(--bg-muted)] rounded-full overflow-hidden">
                            <div class="h-full rounded-full bg-primary" style="width: 66%"/>
                        </div>
                        <span class="text-xs text-[var(--text-muted)] whitespace-nowrap">2/3</span>
                    </div>
                </div>
                <div>
                    <div class="text-xs text-(--text-muted) mb-1">{{ t('helpCenter.boardView.checklistProgressDone') }}</div>
                    <div class="flex items-center gap-2">
                        <div class="flex-1 h-1.5 bg-[var(--bg-muted)] rounded-full overflow-hidden">
                            <div class="h-full rounded-full bg-green-500" style="width: 100%"/>
                        </div>
                        <span class="text-xs text-[var(--text-muted)] whitespace-nowrap">4/4</span>
                    </div>
                </div>
            </div>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.filterTitle')">
            <p>{{ t('helpCenter.boardView.filterText') }}</p>
            <HelpList>
                <li>{{ t('helpCenter.boardView.filterSearch') }}</li>
                <li>{{ t('helpCenter.boardView.filterAssignee') }}</li>
                <li>{{ t('helpCenter.boardView.filterLabel') }}</li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.createTicketTitle')">
            <p>{{ t('helpCenter.boardView.createTicketText') }}</p>
            <p>{{ t('helpCenter.boardView.createTicketMore') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.backlogTitle')">
            <p>{{ t('helpCenter.boardView.backlogText') }}</p>
            <p>{{ t('helpCenter.boardView.archiveText') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.exampleTitle')">
            <DummyKanbanBoard />
        </HelpSection>

        <HelpTip>{{ t('helpCenter.boardView.tip') }}</HelpTip>
    </HelpArticle>
</template>
