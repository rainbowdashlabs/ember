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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'

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
            <ul class="list-disc ml-4 space-y-1">
                <li>{{ t('helpCenter.boardView.ticketItem1') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem2') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem3') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem4') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem5') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem6') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem7') }}</li>
                <li>{{ t('helpCenter.boardView.ticketItem8') }}</li>
            </ul>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.timeDotsTitle')">
            <p>{{ t('helpCenter.boardView.timeDotsText') }}</p>
            <ul class="list-disc ml-4 space-y-1">
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
            </ul>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.filterTitle')">
            <p>{{ t('helpCenter.boardView.filterText') }}</p>
            <ul class="list-disc ml-4 space-y-1">
                <li>{{ t('helpCenter.boardView.filterSearch') }}</li>
                <li>{{ t('helpCenter.boardView.filterAssignee') }}</li>
                <li>{{ t('helpCenter.boardView.filterLabel') }}</li>
            </ul>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.createTicketTitle')">
            <p>{{ t('helpCenter.boardView.createTicketText') }}</p>
            <p>{{ t('helpCenter.boardView.createTicketMore') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardView.backlogTitle')">
            <p>{{ t('helpCenter.boardView.backlogText') }}</p>
            <p>{{ t('helpCenter.boardView.archiveText') }}</p>
        </HelpSection>

        <!-- Dummy: Full kanban board -->
        <HelpSection :title="t('helpCenter.boardView.exampleTitle')">
            <!-- Header bar -->
            <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
                <div class="flex items-center gap-3">
                    <SectionHeader>Dienstplanung</SectionHeader>
                    <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">PLAN</span>
                </div>
                <div class="flex items-center gap-2">
                    <TextInput :model-value="''" :placeholder="t('boards.searchTickets')" class="w-48 md:w-96" />
                    <PrimaryButton>
                        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                        {{ t('boards.createTicket') }}
                    </PrimaryButton>
                    <IconButton :icon="['fas', 'gears']" :label="t('boards.settings')" />
                </div>
            </div>

            <!-- Filter bar -->
            <div class="flex items-center mb-4 gap-3">
                <IconButton :icon="['fas', 'inbox']" :label="t('boards.showBacklog')" />
                <div class="flex items-center">
                    <div class="cursor-pointer rounded-full ring-2 ring-primary">
                        <div class="h-8 w-8 rounded-full bg-primary/15 text-primary font-bold flex items-center justify-center text-xs">
                            <font-awesome-icon :icon="['fas', 'users']" />
                        </div>
                    </div>
                    <div class="cursor-pointer rounded-full opacity-70 -ml-1.5">
                        <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Max M." size="md" />
                    </div>
                    <div class="cursor-pointer rounded-full opacity-70 -ml-1.5">
                        <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Erika F." size="md" />
                    </div>
                </div>
            </div>

            <!-- Kanban lanes -->
            <div class="flex flex-col md:flex-row gap-4 pb-4" style="min-height: 200px">
                <!-- Lane 1: Offen -->
                <div class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2" style="border-top-color: var(--primary)">
                    <div class="flex items-center justify-between mb-3">
                        <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">Offen</SubHeader>
                        <span class="text-xs text-[var(--text-muted)] bg-[var(--bg)] px-1.5 py-0.5 rounded-full">2</span>
                    </div>
                    <div class="space-y-2">
                        <!-- Ticket 1 -->
                        <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
                            <p class="text-sm font-medium leading-tight mb-1">Dienstplan Juni erstellen</p>
                            <div class="flex flex-wrap gap-1 mb-2">
                                <span class="text-[0.65rem] leading-tight px-1.5 py-0.5 rounded-full font-medium bg-blue-500 text-white">Planung</span>
                            </div>
                            <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
                                <div class="flex items-center gap-2">
                                    <font-awesome-icon :icon="['fas', 'angle-up']" class="text-orange-500 text-xs" />
                                    <span class="flex items-center gap-0.5">
                                        <font-awesome-icon :icon="['fas', 'calendar']" class="text-[0.6rem]" />
                                        15.06
                                    </span>
                                </div>
                                <div class="flex items-center gap-2">
                                    <span class="font-mono opacity-60">PLAN-7</span>
                                    <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Max M." size="sm" />
                                </div>
                            </div>
                        </div>
                        <!-- Ticket 2 -->
                        <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
                            <p class="text-sm font-medium leading-tight mb-1">Neue Mitglieder einteilen</p>
                            <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
                                <div class="flex items-center gap-2">
                                    <font-awesome-icon :icon="['fas', 'equals']" class="text-yellow-500 text-xs" />
                                </div>
                                <div class="flex items-center gap-2">
                                    <span class="font-mono opacity-60">PLAN-8</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Lane 2: In Arbeit -->
                <div class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2" style="border-top-color: #3694FF">
                    <div class="flex items-center justify-between mb-3">
                        <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">In Arbeit</SubHeader>
                        <span class="text-xs text-[var(--text-muted)] bg-[var(--bg)] px-1.5 py-0.5 rounded-full">1</span>
                    </div>
                    <div class="space-y-2">
                        <!-- Ticket 3 -->
                        <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
                            <p class="text-sm font-medium leading-tight mb-1">Fahrzeugcheck organisieren</p>
                            <div class="flex flex-wrap gap-1 mb-2">
                                <span class="text-[0.65rem] leading-tight px-1.5 py-0.5 rounded-full font-medium bg-green-600 text-white">Wartung</span>
                                <span class="text-[0.65rem] leading-tight px-1.5 py-0.5 rounded-full font-medium bg-orange-500 text-white">Dringend</span>
                            </div>
                            <!-- Checklist progress -->
                            <div class="flex items-center gap-2 mb-2">
                                <div class="flex-1 h-1.5 bg-[var(--bg-muted)] rounded-full overflow-hidden">
                                    <div class="h-full rounded-full bg-primary" style="width: 66%" />
                                </div>
                                <span class="text-xs text-[var(--text-muted)] whitespace-nowrap">2/3</span>
                            </div>
                            <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
                                <div class="flex items-center gap-2">
                                    <div class="flex gap-0.5">
                                        <span class="w-1.5 h-1.5 rounded-full bg-green-500" />
                                        <span class="w-1.5 h-1.5 rounded-full bg-green-500" />
                                    </div>
                                    <font-awesome-icon :icon="['fas', 'angles-up']" class="text-red-500 text-xs" />
                                    <span class="flex items-center gap-0.5 text-red-500 font-medium">
                                        <font-awesome-icon :icon="['fas', 'calendar']" class="text-[0.6rem]" />
                                        03.06
                                    </span>
                                    <span class="flex items-center gap-0.5">
                                        <font-awesome-icon :icon="['fas', 'paperclip']" class="text-[0.6rem]" />
                                        2
                                    </span>
                                </div>
                                <div class="flex items-center gap-2">
                                    <span class="font-mono opacity-60">PLAN-5</span>
                                    <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Erika F." size="sm" />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Lane 3: Erledigt -->
                <div class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2" style="border-top-color: #00C507">
                    <div class="flex items-center justify-between mb-3">
                        <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">Erledigt</SubHeader>
                        <span class="text-xs text-[var(--text-muted)] bg-[var(--bg)] px-1.5 py-0.5 rounded-full">1</span>
                    </div>
                    <div class="space-y-2">
                        <!-- Ticket 4 -->
                        <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
                            <p class="text-sm font-medium leading-tight mb-1">Übungsplan Mai abschließen</p>
                            <div class="flex items-center gap-2 mb-2">
                                <div class="flex-1 h-1.5 bg-[var(--bg-muted)] rounded-full overflow-hidden">
                                    <div class="h-full rounded-full bg-green-500" style="width: 100%" />
                                </div>
                                <span class="text-xs text-[var(--text-muted)] whitespace-nowrap">4/4</span>
                            </div>
                            <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
                                <div class="flex items-center gap-2">
                                    <font-awesome-icon :icon="['fas', 'angle-down']" class="text-blue-400 text-xs" />
                                </div>
                                <div class="flex items-center gap-2">
                                    <span class="font-mono opacity-60">PLAN-3</span>
                                    <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Max M." size="sm" />
                                </div>
                            </div>
                        </div>
                    </div>
                    <!-- Archive link -->
                    <div class="mt-2">
                        <SecondaryButton class="w-full text-xs">
                            3 {{ t('boards.archived') }}
                        </SecondaryButton>
                    </div>
                </div>
            </div>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.boardView.tip') }}</HelpTip>
    </HelpArticle>
</template>
