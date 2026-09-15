/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DummyAudienceRow from './DummyAudienceRow.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersConfig.audiences.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('membersConfig.audiences.hint', {name: 'Geburtsdatum'}) }}</MutedText>
    <DummyAudienceRow :role="t('membersConfig.roles.MEMBER')" :writable="false"/>
    <DummyAudienceRow :role="t('membersConfig.roles.TEAM')" :writable="true"/>
  </NeutralContainer>
</template>
