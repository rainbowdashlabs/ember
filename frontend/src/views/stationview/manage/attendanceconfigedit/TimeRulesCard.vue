/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import TimeRulesEditor from '@/components/attendance/timerules/TimeRulesEditor.vue'
import {draftOf, inputOf, savable, type TimeRuleDraft} from '@/components/attendance/timerules/timeRuleDraft'
import {audienceLists} from '@/components/documents/generation'
import {attendance, stationMembers} from '@/api'
import type {MemberGroup, MemberWithName, UserTag} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Who a template credits with preparation and follow-up time, and how much.
 *
 * <p>Every sheet made from the template copies these rules when it is made, so a change here reaches
 * the next sheet and leaves the ones already taken as they were. Nothing can be saved before the rules
 * have been read: an empty list saved over rules that merely failed to load would delete them.
 */
const props = defineProps<{
  templateId: number
}>()

const {t} = useI18n()

const rules = ref<TimeRuleDraft[]>([])
const loaded = ref(false)
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<MemberWithName[]>([])
const saveFailure = ref<Failure | null>(null)

const {loading, failure} = useAsyncLoader(async () => {
  const [stored, lists, memberList] = await Promise.all([
    attendance.listTemplateTimeRules(props.templateId),
    audienceLists(),
    stationMembers.listMembers().catch(() => []),
  ])
  rules.value = stored.map(draftOf)
  groups.value = lists.groups
  tags.value = lists.tags
  members.value = memberList.filter(member => !member.formerAt)
  loaded.value = true
})

async function save() {
  saveFailure.value = null
  try {
    const saved = await attendance.setTemplateTimeRules(props.templateId, {rules: rules.value.map(inputOf)})
    rules.value = saved.map(draftOf)
  } catch (e) {
    saveFailure.value = describeFailure(e, t)
    throw e
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="template-time-rules">
    <SectionHeader>{{ t('attendanceTimeRules.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('attendanceTimeRules.templateHint') }}</MutedText>
    <Spinner v-if="loading" size="lg"/>
    <FailureAlert v-else-if="!loaded" :failure="failure"/>
    <template v-else>
      <FailureAlert :failure="saveFailure"/>
      <TimeRulesEditor v-model="rules" :groups="groups" :tags="tags" :members="members"/>
      <SaveButton :disabled="!savable(rules)" :action="save" data-testid="time-rules-save">{{ t('attendanceTimeRules.save') }}</SaveButton>
    </template>
  </NeutralContainer>
</template>
