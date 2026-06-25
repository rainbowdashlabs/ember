/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

const {t} = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.federation') }}</SubHeader>
        <div class="space-y-2">
            <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-sm min-w-24">{{ t('helpCenter.boardSettings.partnerExample') }}</span>
                <div class="flex items-center gap-1">
                    <span class="text-xs text-(--text-muted) shrink-0">{{ t('boards.accessMode') }}:</span>
                    <SelectInput model-value="FULL">
                        <option value="READ_ONLY">{{ t('boards.shareModeReadOnly') }}</option>
                        <option value="FULL">{{ t('boards.shareModeFull') }}</option>
                    </SelectInput>
                </div>
                <div class="flex items-center gap-1">
                    <span class="text-xs text-(--text-muted) shrink-0">{{ t('boards.minViewRole') }}:</span>
                    <SelectInput model-value="USER">
                        <option value="USER">{{ t('boards.requiredRoleUser') }}</option>
                        <option value="TEAM">{{ t('boards.requiredRoleTeam') }}</option>
                        <option value="MANAGER">{{ t('boards.requiredRoleManager') }}</option>
                    </SelectInput>
                </div>
                <DeleteButton />
            </div>
        </div>
        <div class="flex gap-2 mt-3">
            <SelectInput model-value="" class="flex-1">
                <option value="">{{ t('boards.federationShare') }}...</option>
                <option value="2">{{ t('helpCenter.boardSettings.partnerExample2') }}</option>
            </SelectInput>
            <SecondaryButton disabled>
                <font-awesome-icon :icon="['fas', 'plus']" />
            </SecondaryButton>
        </div>
    </NeutralContainer>
</template>
