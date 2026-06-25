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
import EmptyState from '@/components/feedback/EmptyState.vue'
import ArchiveExample from './archivedhelp/ArchiveExample.vue'

const {t} = useI18n()
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

        <HelpSection :title="t('helpCenter.archived.exampleTitle')">
            <ArchiveExample />
        </HelpSection>

        <HelpSection :title="t('helpCenter.archived.emptyTitle')">
            <p>{{ t('helpCenter.archived.emptyText') }}</p>
            <NeutralContainer>
                <EmptyState>{{ t('boards.noTickets') }}</EmptyState>
            </NeutralContainer>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.archived.tip') }}</HelpTip>
    </HelpArticle>
</template>
