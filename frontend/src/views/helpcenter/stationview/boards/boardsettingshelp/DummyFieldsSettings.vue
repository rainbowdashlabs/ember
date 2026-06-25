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
import DummyFieldRow from './DummyFieldRow.vue'
import DummyFieldTypeSelect from './DummyFieldTypeSelect.vue'

const {t} = useI18n()
</script>

<template>
    <NeutralContainer>
        <SubHeader class="text-sm mb-3">{{ t('boards.fields') }}</SubHeader>
        <div class="space-y-2">
            <DummyFieldRow name="Zeitaufwand (Stunden)" type="NUMBER" up-disabled />
            <DummyFieldRow
                name="Kategorie"
                type="ENUM"
                options="Ausrüstung, Ausbildung, Organisation"
                down-disabled
            />
        </div>
        <div class="flex gap-2 mt-3">
            <TextInput model-value="" :placeholder="t('boards.addField')" class="flex-1" />
            <DummyFieldTypeSelect model-value="STRING" />
            <SecondaryButton>
                <font-awesome-icon :icon="['fas', 'plus']" />
            </SecondaryButton>
        </div>
    </NeutralContainer>
</template>
