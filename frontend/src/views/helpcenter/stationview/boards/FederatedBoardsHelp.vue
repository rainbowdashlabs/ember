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
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import FederatedBoardStationGroup from '@/views/stationview/federation/federatedboardsview/FederatedBoardStationGroup.vue'
import {demoIsBookmarked, demoPartnerStations} from '@/views/helpcenter/stationview/boards/federatedboardshelp/fixtures'

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
            <MutedText size="sm" class="mb-4">{{ t('boards.federatedBoardsDesc') }}</MutedText>
            <Alert variant="info" class="mb-4">{{ t('boards.bookmarkHint') }}</Alert>
            <div class="space-y-6">
                <FederatedBoardStationGroup
                    v-for="station in demoPartnerStations"
                    :key="station.stationName"
                    :station-name="station.stationName"
                    :station-boards="station.boards"
                    :is-bookmarked="demoIsBookmarked"
                />
            </div>
        </HelpSection>

        <HelpSection :title="t('helpCenter.federatedBoards.restrictionsTitle')">
            <p>{{ t('helpCenter.federatedBoards.restrictionsText') }}</p>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.federatedBoards.tip') }}</HelpTip>
    </HelpArticle>
</template>
