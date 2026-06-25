/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('common.general') }}</SubHeader>
        <div class="space-y-3">
            <div>
                <FieldLabel class="mb-1">{{ t('boards.boardName') }}</FieldLabel>
                <TextInput model-value="Aufgaben Jugendfeuerwehr" />
            </div>
            <div>
                <FieldLabel class="mb-1">{{ t('boards.boardDescription') }}</FieldLabel>
                <TextAreaInput model-value="Alle Aufgaben rund um die Jugendfeuerwehr" :rows="3" />
            </div>
            <div>
                <FieldLabel class="mb-1">{{ t('boards.hideDoneAfterDays') }}</FieldLabel>
                <NumberInput :model-value="7" :min="1" :max="365" />
            </div>
            <div class="flex items-center justify-between">
                <FieldLabel>{{ t('boards.backlog') }}</FieldLabel>
                <ToggleInput :model-value="true" />
            </div>
        </div>
    </NeutralContainer>
</template>
