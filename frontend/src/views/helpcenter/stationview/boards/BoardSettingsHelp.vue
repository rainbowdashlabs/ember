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
import BoardGeneralSection from '@/views/stationview/boards/boardsettingsview/BoardGeneralSection.vue'
import BoardLanesSection from '@/views/stationview/boards/boardsettingsview/BoardLanesSection.vue'
import BoardFieldsSection from '@/views/stationview/boards/boardsettingsview/BoardFieldsSection.vue'
import BoardFederationSection from '@/views/stationview/boards/boardsettingsview/BoardFederationSection.vue'
import {FieldType} from '@/api/generated/schema'
import {
    demoAvailablePartners,
    demoFederationTargets,
    demoFieldDrafts,
    demoGeneral,
    demoLaneDrafts,
    demoPartnerName,
    demoRoleOptions,
} from './boardsettingshelp/fixtures'

const {t} = useI18n()

const lanes = demoLaneDrafts()
const fields = demoFieldDrafts()
const federationTargets = demoFederationTargets()
</script>

<template>
    <HelpArticle :title="t('helpCenter.boardSettings.title')" :subtitle="t('helpCenter.boardSettings.subtitle')">
        <HelpSection :title="t('helpCenter.boardSettings.whatIs')">
            <p>{{ t('helpCenter.boardSettings.whatIsText') }}</p>
            <p>{{ t('helpCenter.boardSettings.whatIsText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.generalTitle')">
            <p>{{ t('helpCenter.boardSettings.generalText') }}</p>
            <BoardGeneralSection
                :name="demoGeneral.name"
                :description="demoGeneral.description"
                :hide-done-after-days="demoGeneral.hideDoneAfterDays"
                :has-backlog="demoGeneral.hasBacklog"
            />
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.archiveTitle')">
            <p>{{ t('helpCenter.boardSettings.archiveText') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.lanesTitle')">
            <p>{{ t('helpCenter.boardSettings.lanesText') }}</p>
            <p>{{ t('helpCenter.boardSettings.lanesText2') }}</p>
            <BoardLanesSection :lanes="lanes" new-lane-name="" />
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.laneMoveTitle')">
            <p>{{ t('helpCenter.boardSettings.laneMoveText') }}</p>
            <p>{{ t('helpCenter.boardSettings.laneMoveText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.fieldsTitle')">
            <p>{{ t('helpCenter.boardSettings.fieldsText') }}</p>
            <BoardFieldsSection
                :fields="fields"
                :lanes="lanes"
                new-field-name=""
                :new-field-type="FieldType.TEXT"
            />
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.fieldTypesTitle')">
            <p>{{ t('helpCenter.boardSettings.fieldTypesText') }}</p>
            <HelpList>
                <li><strong>{{ t('fieldTypes.label.TEXT') }}:</strong> {{ t('helpCenter.boardSettings.typeStringDesc') }}</li>
                <li><strong>{{ t('fieldTypes.label.NUMBER') }}:</strong> {{ t('helpCenter.boardSettings.typeNumberDesc') }}</li>
                <li><strong>{{ t('fieldTypes.label.BOOLEAN') }}:</strong> {{ t('helpCenter.boardSettings.typeBooleanDesc') }}</li>
                <li><strong>{{ t('fieldTypes.label.CHOICE') }}:</strong> {{ t('helpCenter.boardSettings.typeEnumDesc') }}</li>
                <li><strong>{{ t('fieldTypes.label.DATE') }}:</strong> {{ t('helpCenter.boardSettings.typeDateDesc') }}</li>
                <li><strong>{{ t('fieldTypes.label.LANE_ASSIGNEE') }}:</strong> {{ t('helpCenter.boardSettings.typeLaneAssigneeDesc') }}</li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.federationTitle')">
            <p>{{ t('helpCenter.boardSettings.federationText') }}</p>
            <p>{{ t('helpCenter.boardSettings.federationText2') }}</p>
            <BoardFederationSection
                can-federate
                :targets="federationTargets"
                :available-partners="demoAvailablePartners"
                has-full-mode
                :add-partner-id="null"
                :federated-edit-user-types="[]"
                :role-options="demoRoleOptions"
                :partner-name="demoPartnerName"
            />
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.shareModesTitle')">
            <p>{{ t('helpCenter.boardSettings.shareModesText') }}</p>
            <HelpList>
                <li><strong>{{ t('boards.shareModeReadOnly') }}:</strong> {{ t('helpCenter.boardSettings.readOnlyDesc') }}</li>
                <li><strong>{{ t('boards.shareModeFull') }}:</strong> {{ t('helpCenter.boardSettings.fullDesc') }}</li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.autoSaveTitle')">
            <p>{{ t('helpCenter.boardSettings.autoSaveText') }}</p>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.boardSettings.tip') }}</HelpTip>
        <HelpTip>{{ t('helpCenter.boardSettings.tip2') }}</HelpTip>
    </HelpArticle>
</template>
