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
import FieldTypePicker from '@/components/input/FieldTypePicker.vue'
import BoardFieldRow from '@/views/stationview/boards/boardsettingsview/BoardFieldRow.vue'
import {FieldTypes, OfferedFieldTypes} from '@/api/fieldTypes'
import type {BoardFieldDraft} from '@/api/boards'

const {t} = useI18n()

const examples: BoardFieldDraft[] = [
    {name: 'Zeitaufwand (Stunden)', fieldType: FieldTypes.NUMBER, required: false, options: [], laneId: null},
    {
        name: 'Kategorie',
        fieldType: FieldTypes.CHOICE,
        required: true,
        options: ['Ausrüstung', 'Ausbildung', 'Organisation'],
        laneId: null,
    },
]
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.fields') }}</SubHeader>
        <div class="space-y-2">
            <BoardFieldRow
                v-for="(field, index) in examples"
                :key="field.name"
                :field="field"
                :first="index === 0"
                :last="index === examples.length - 1"
                :lanes="[]"
            />
        </div>
        <div class="flex gap-2 mt-3">
            <TextInput model-value="" :placeholder="t('boards.addField')" class="flex-1" />
            <FieldTypePicker :model-value="FieldTypes.TEXT" :types="OfferedFieldTypes.BOARD" class="w-48" />
            <SecondaryButton>
                <font-awesome-icon :icon="['fas', 'plus']" />
            </SecondaryButton>
        </div>
    </NeutralContainer>
</template>
