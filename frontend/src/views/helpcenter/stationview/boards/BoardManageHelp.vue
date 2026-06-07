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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

const {t} = useI18n()
</script>

<template>
    <HelpArticle :title="t('helpCenter.boardManage.title')" :subtitle="t('helpCenter.boardManage.subtitle')">
        <HelpSection :title="t('helpCenter.boardManage.whatIs')">
            <p>{{ t('helpCenter.boardManage.whatIsText') }}</p>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardManage.createTitle')">
            <p>{{ t('helpCenter.boardManage.createText') }}</p>
            <HelpList>
                <li>{{ t('helpCenter.boardManage.createName') }}</li>
                <li>{{ t('helpCenter.boardManage.createShortKey') }}</li>
                <li>{{ t('helpCenter.boardManage.createDesc') }}</li>
                <li>{{ t('helpCenter.boardManage.createPreset') }}</li>
            </HelpList>
        </HelpSection>

        <!-- Dummy: Create modal -->
        <HelpSection :title="t('helpCenter.boardManage.createModalTitle')">
            <NeutralContainer class="space-y-4 max-w-md">
                <SubHeader>{{ t('boards.createBoard') }}</SubHeader>
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.boardName') }} *</FieldLabel>
                    <TextInput :model-value="'Dienstplanung'" />
                </div>
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.shortKey') }} *</FieldLabel>
                    <TextInput :model-value="'PLAN'" :placeholder="t('boards.shortKeyHint')" />
                </div>
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.boardDescription') }}</FieldLabel>
                    <TextAreaInput :model-value="'Aufgaben rund um den Dienstplan.'" :rows="3" />
                </div>
                <div>
                    <FieldLabel class="mb-1">{{ t('boards.preset') }}</FieldLabel>
                    <SelectInput :model-value="'SIMPLE'" class="w-full">
                        <option value="SIMPLE">{{ t('boards.presetSimple') }}</option>
                        <option value="FEEDBACK">{{ t('boards.presetFeedback') }}</option>
                        <option value="">{{ t('boards.presetNone') }}</option>
                    </SelectInput>
                </div>
                <div class="flex justify-end gap-2">
                    <PrimaryButton>{{ t('common.create') }}</PrimaryButton>
                </div>
            </NeutralContainer>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardManage.deleteTitle')">
            <p>{{ t('helpCenter.boardManage.deleteText') }}</p>
            <p>{{ t('helpCenter.boardManage.deleteWarning') }}</p>

            <!-- Dummy: Delete confirmation -->
            <NeutralContainer class="space-y-4 max-w-md mt-3">
                <SubHeader>{{ t('boards.deleteBoard') }}</SubHeader>
                <p class="text-sm">{{ t('boards.deleteBoardConfirm') }}</p>
                <div class="flex justify-end gap-2">
                    <DeleteButton>{{ t('common.delete') }}</DeleteButton>
                </div>
            </NeutralContainer>
        </HelpSection>

        <HelpSection :title="t('helpCenter.boardManage.settingsTitle')">
            <p>{{ t('helpCenter.boardManage.settingsText') }}</p>
        </HelpSection>

        <HelpTip>{{ t('helpCenter.boardManage.tip') }}</HelpTip>
    </HelpArticle>
</template>
