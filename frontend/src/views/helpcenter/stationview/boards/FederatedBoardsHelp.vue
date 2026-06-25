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
import DummyBoardsOverview from '@/views/helpcenter/stationview/boards/federatedboardshelp/DummyBoardsOverview.vue'

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

        <HelpSection :title="t('helpCenter.federatedBoards.exampleTitle')">
            <DummyBoardsOverview />
        </HelpSection>

        <HelpSection :title="t('helpCenter.federatedBoards.restrictionsTitle')">
            <p>{{ t('helpCenter.federatedBoards.restrictionsText') }}</p>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.federatedBoards.tip') }}</HelpTip>
    </HelpArticle>
</template>
