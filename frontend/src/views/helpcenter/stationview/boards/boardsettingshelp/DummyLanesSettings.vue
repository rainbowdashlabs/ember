/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DummyLaneRow from './DummyLaneRow.vue'

const {t} = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.lanes') }}</SubHeader>
        <div class="space-y-2">
            <DummyLaneRow color="#3b82f6" label="Zu erledigen" up-disabled />
            <DummyLaneRow color="#f59e0b" label="In Arbeit" />
            <DummyLaneRow color="#22c55e" label="Fertig" down-disabled />
        </div>
        <div class="flex gap-2 mt-3">
            <TextInput model-value="" :placeholder="t('boards.addLane')" class="flex-1" />
            <SecondaryButton>
                <font-awesome-icon :icon="['fas', 'plus']" />
            </SecondaryButton>
        </div>
    </NeutralContainer>
</template>
