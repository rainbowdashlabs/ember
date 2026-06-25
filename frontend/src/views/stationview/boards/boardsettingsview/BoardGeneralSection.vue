/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

const name = defineModel<string>('name', { required: true })
const description = defineModel<string>('description', { required: true })
const hideDoneAfterDays = defineModel<number>('hideDoneAfterDays', { required: true })
const hasBacklog = defineModel<boolean>('hasBacklog', { required: true })

const { t } = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('common.general') }}</SubHeader>
        <div class="space-y-3">
            <div>
                <FieldLabel class="mb-1">{{ t('boards.boardName') }}</FieldLabel>
                <TextInput v-model="name" />
            </div>
            <div>
                <FieldLabel class="mb-1">{{ t('boards.boardDescription') }}</FieldLabel>
                <TextAreaInput v-model="description" :rows="3" />
            </div>
            <div>
                <FieldLabel class="mb-1">{{ t('boards.hideDoneAfterDays') }}</FieldLabel>
                <NumberInput v-model="hideDoneAfterDays" :min="1" :max="365" />
            </div>
            <div class="flex items-center justify-between">
                <FieldLabel>{{ t('boards.backlog') }}</FieldLabel>
                <ToggleInput v-model="hasBacklog" />
            </div>
        </div>
    </NeutralContainer>
</template>
