/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {fromCompletion} from '@/components/input/select/memberOption'
import type {MemberCompletion} from '@/api/stationMembers'

const {t} = useI18n()

const assigneeIds = defineModel<number[]>('assigneeIds', {default: () => []})

const props = defineProps<{
  members: MemberCompletion[]
}>()

const options = computed(() => props.members.map(fromCompletion))

/** The menu speaks in strings and a member id out of the database is a number, which is the one translation. */
const chosen = computed({
  get: () => assigneeIds.value.map(String),
  set: values => {
    assigneeIds.value = values.map(Number)
  },
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('procedures.assignees') }}</SubHeader>
    <MemberSelectInput
        v-model:selected="chosen"
        multiple
        :members="options"
        :placeholder="t('procedures.selectAssignee')"
    />
    <MutedText v-if="assigneeIds.length === 0" size="sm">{{ t('procedures.noAssignees') }}</MutedText>
  </NeutralContainer>
</template>
