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
import DummyGeneralSettings from './boardsettingshelp/DummyGeneralSettings.vue'
import DummyLanesSettings from './boardsettingshelp/DummyLanesSettings.vue'
import DummyFieldsSettings from './boardsettingshelp/DummyFieldsSettings.vue'
import DummyFederationSettings from './boardsettingshelp/DummyFederationSettings.vue'

const {t} = useI18n()
</script>

<template>
    <HelpArticle :title="t('helpCenter.boardSettings.title')" :subtitle="t('helpCenter.boardSettings.subtitle')">
        <HelpSection :title="t('helpCenter.boardSettings.whatIs')">
            <p>{{ t('helpCenter.boardSettings.whatIsText') }}</p>
            <p>{{ t('helpCenter.boardSettings.whatIsText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.generalTitle')">
            <p>{{ t('helpCenter.boardSettings.generalText') }}</p>
            <DummyGeneralSettings />
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.archiveTitle')">
            <p>{{ t('helpCenter.boardSettings.archiveText') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.lanesTitle')">
            <p>{{ t('helpCenter.boardSettings.lanesText') }}</p>
            <p>{{ t('helpCenter.boardSettings.lanesText2') }}</p>
            <DummyLanesSettings />
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.laneMoveTitle')">
            <p>{{ t('helpCenter.boardSettings.laneMoveText') }}</p>
            <p>{{ t('helpCenter.boardSettings.laneMoveText2') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.fieldsTitle')">
            <p>{{ t('helpCenter.boardSettings.fieldsText') }}</p>
            <DummyFieldsSettings />
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.fieldTypesTitle')">
            <p>{{ t('helpCenter.boardSettings.fieldTypesText') }}</p>
            <HelpList>
                <li><strong>{{ t('boards.fieldTypeString') }}:</strong> {{ t('helpCenter.boardSettings.typeStringDesc') }}</li>
                <li><strong>{{ t('boards.fieldTypeNumber') }}:</strong> {{ t('helpCenter.boardSettings.typeNumberDesc') }}</li>
                <li><strong>{{ t('boards.fieldTypeBoolean') }}:</strong> {{ t('helpCenter.boardSettings.typeBooleanDesc') }}</li>
                <li><strong>{{ t('boards.fieldTypeEnum') }}:</strong> {{ t('helpCenter.boardSettings.typeEnumDesc') }}</li>
                <li><strong>{{ t('boards.fieldTypeDate') }}:</strong> {{ t('helpCenter.boardSettings.typeDateDesc') }}</li>
                <li><strong>{{ t('boards.fieldTypeLaneAssignee') }}:</strong> {{ t('helpCenter.boardSettings.typeLaneAssigneeDesc') }}</li>
            </HelpList>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardSettings.federationTitle')">
            <p>{{ t('helpCenter.boardSettings.federationText') }}</p>
            <p>{{ t('helpCenter.boardSettings.federationText2') }}</p>
            <DummyFederationSettings />
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
