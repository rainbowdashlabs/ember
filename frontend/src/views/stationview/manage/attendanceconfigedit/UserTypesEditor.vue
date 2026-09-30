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
import UserTypeCheckboxes from '@/components/input/toggle/UserTypeCheckboxes.vue'
import type {StationUserTypeName} from '@/api/types'

/**
 * The user types a template enters besides the members of its groups. Every change is handed on at
 * once, the way a change to the groups is.
 */
const userTypes = defineModel<StationUserTypeName[]>({required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="attendance-template-user-types">
    <SectionHeader>{{ t('attendanceConfig.userTypes') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('attendanceConfig.userTypesHint') }}</MutedText>
    <UserTypeCheckboxes v-model="userTypes" test-id-prefix="attendance-template-type"/>
  </NeutralContainer>
</template>
