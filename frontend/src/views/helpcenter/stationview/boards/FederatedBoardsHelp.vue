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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import Alert from '@/components/feedback/Alert.vue'

const {t} = useI18n()
</script>

<template>
    <HelpArticle :title="t('helpCenter.federatedBoards.title')" :subtitle="t('helpCenter.federatedBoards.subtitle')">
        <HelpSection :title="t('helpCenter.federatedBoards.whatIs')">
            <p>{{ t('helpCenter.federatedBoards.whatIsText') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.federatedBoards.accessTitle')">
            <p>{{ t('helpCenter.federatedBoards.accessText') }}</p>
            <HelpList>
                <li>{{ t('helpCenter.federatedBoards.accessReadOnly') }}</li>
                <li>{{ t('helpCenter.federatedBoards.accessFull') }}</li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.federatedBoards.overviewTitle')">
            <p>{{ t('helpCenter.federatedBoards.overviewText') }}</p>
            <p>{{ t('helpCenter.federatedBoards.overviewBadge') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.federatedBoards.bookmarkTitle')">
            <p>{{ t('helpCenter.federatedBoards.bookmarkText') }}</p>
            <p>{{ t('helpCenter.federatedBoards.bookmarkRemove') }}</p>
        </HelpSection>

        <!-- Dummy: Federated boards overview -->
        <HelpSection :title="t('helpCenter.federatedBoards.exampleTitle')">
            <NeutralContainer class="space-y-4">
                <SectionHeader>{{ t('boards.federatedBoards') }}</SectionHeader>
                <MutedText size="sm">{{ t('boards.federatedBoardsDesc') }}</MutedText>
                <Alert variant="info">{{ t('boards.bookmarkHint') }}</Alert>

                <!-- Dummy: Station group 1 -->
                <div class="space-y-3">
                    <SubHeader>{{ t('helpCenter.federatedBoards.dummyStation1') }}</SubHeader>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <!-- Board card 1: Read-only -->
                        <NeutralContainer class="cursor-pointer hover:ring-2 hover:ring-primary transition-all">
                            <div class="flex items-start justify-between gap-2">
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-center gap-2 flex-wrap">
                                        <span class="font-semibold">{{ t('helpCenter.federatedBoards.dummyBoard1') }}</span>
                                        <code class="text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-1.5 py-0.5 rounded">{{ t('helpCenter.federatedBoards.dummyBoard1Key') }}</code>
                                    </div>
                                    <p class="text-sm mt-1 text-text-light-secondary dark:text-text-dark-secondary">{{ t('helpCenter.federatedBoards.dummyBoard1Desc') }}</p>
                                </div>
                                <IconButton :icon="['fas', 'star']" :label="t('boards.bookmark')" class="text-text-light-secondary dark:text-text-dark-secondary hover:text-yellow-500" />
                            </div>
                            <div class="flex items-center gap-2 mt-3">
                                <SecondaryBadge class="inline-flex items-center gap-1">
                                    <font-awesome-icon :icon="['fas', 'lock']" class="text-[0.65rem]" />
                                    {{ t('boards.readOnlyBadge') }}
                                </SecondaryBadge>
                            </div>
                        </NeutralContainer>

                        <!-- Board card 2: Full access, bookmarked -->
                        <NeutralContainer class="cursor-pointer hover:ring-2 hover:ring-primary transition-all">
                            <div class="flex items-start justify-between gap-2">
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-center gap-2 flex-wrap">
                                        <span class="font-semibold">{{ t('helpCenter.federatedBoards.dummyBoard2') }}</span>
                                        <code class="text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-1.5 py-0.5 rounded">{{ t('helpCenter.federatedBoards.dummyBoard2Key') }}</code>
                                    </div>
                                    <p class="text-sm mt-1 text-text-light-secondary dark:text-text-dark-secondary">{{ t('helpCenter.federatedBoards.dummyBoard2Desc') }}</p>
                                </div>
                                <IconButton :icon="['fas', 'star']" :label="t('boards.bookmarked')" class="text-yellow-500 hover:text-yellow-600" />
                            </div>
                            <div class="flex items-center gap-2 mt-3">
                                <SuccessBadge class="inline-flex items-center gap-1">
                                    <font-awesome-icon :icon="['fas', 'pen']" class="text-[0.65rem]" />
                                    {{ t('boards.fullAccessBadge') }}
                                </SuccessBadge>
                            </div>
                        </NeutralContainer>
                    </div>
                </div>

                <!-- Dummy: Station group 2 -->
                <div class="space-y-3">
                    <SubHeader>{{ t('helpCenter.federatedBoards.dummyStation2') }}</SubHeader>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <!-- Board card 3: Read-only -->
                        <NeutralContainer class="cursor-pointer hover:ring-2 hover:ring-primary transition-all">
                            <div class="flex items-start justify-between gap-2">
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-center gap-2 flex-wrap">
                                        <span class="font-semibold">{{ t('helpCenter.federatedBoards.dummyBoard3') }}</span>
                                        <code class="text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-1.5 py-0.5 rounded">{{ t('helpCenter.federatedBoards.dummyBoard3Key') }}</code>
                                    </div>
                                    <p class="text-sm mt-1 text-text-light-secondary dark:text-text-dark-secondary">{{ t('helpCenter.federatedBoards.dummyBoard3Desc') }}</p>
                                </div>
                                <IconButton :icon="['fas', 'star']" :label="t('boards.bookmark')" class="text-text-light-secondary dark:text-text-dark-secondary hover:text-yellow-500" />
                            </div>
                            <div class="flex items-center gap-2 mt-3">
                                <SecondaryBadge class="inline-flex items-center gap-1">
                                    <font-awesome-icon :icon="['fas', 'lock']" class="text-[0.65rem]" />
                                    {{ t('boards.readOnlyBadge') }}
                                </SecondaryBadge>
                            </div>
                        </NeutralContainer>
                    </div>
                </div>
            </NeutralContainer>
        </HelpSection>

        <HelpSection :title="t('helpCenter.federatedBoards.restrictionsTitle')">
            <p>{{ t('helpCenter.federatedBoards.restrictionsText') }}</p>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.federatedBoards.tip') }}</HelpTip>
    </HelpArticle>
</template>
