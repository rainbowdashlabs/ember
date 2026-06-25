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
import DummyBacklogTable from './backloghelp/DummyBacklogTable.vue'

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

        <HelpSection :title="t('helpCenter.backlog.exampleTitle')">
            <NeutralContainer class="space-y-4">
                <div class="flex items-center gap-3">
                    <IconButton :icon="['fas', 'chevron-left']" label="Back" />
                    <SectionHeader>PLAN — {{ t('boards.backlogTitle') }}</SectionHeader>
                </div>
                <DummyBacklogTable />
            </NeutralContainer>
        </HelpSection>

        <HelpSection :title="t('helpCenter.backlog.emptyTitle')">
            <p>{{ t('helpCenter.backlog.emptyText') }}</p>
            <NeutralContainer>
                <EmptyState>{{ t('boards.noTickets') }}</EmptyState>
            </NeutralContainer>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.backlog.tip') }}</HelpTip>
    </HelpArticle>
</template>
