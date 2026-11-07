/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import BareButton from '@/components/button/BareButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TimeRulesEditor from '@/components/attendance/timerules/TimeRulesEditor.vue'
import TimeRulesSummary from '@/components/attendance/timerules/TimeRulesSummary.vue'
import {draftOf, inputOf, savable, type TimeRuleDraft} from '@/components/attendance/timerules/timeRuleDraft'
import {audienceLists} from '@/components/documents/generation'
import {attendance, stationMembers} from '@/api'
import type {MemberGroup, MemberWithName, TimeRule, UserTag} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {useSheetExtraTime} from './useSheetExtraTime'

/**
 * The preparation and follow-up rules this one sheet carries, copied from its template when it was
 * made. Corrected here, they change this evening only; the template and every other sheet keep theirs.
 *
 * <p>Folded away until somebody opens it, because the rules are set up once on the template and a
 * sheet is mostly a list of names. Nothing can be saved before the rules have been read: an empty
 * list saved over rules that merely failed to load would delete them.
 */
const props = defineProps<{
  sessionId: number
  canEdit: boolean
}>()

const emit = defineEmits<{
  /** The rules were saved, so what the rows are credited with may have changed. */
  changed: []
}>()

const {t} = useI18n()
const sheet = useSheetExtraTime()

const open = ref(false)
const loaded = ref(false)
const rules = ref<TimeRule[]>([])
const drafts = ref<TimeRuleDraft[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<MemberWithName[]>([])
const listsLoaded = ref(false)
const saveFailure = ref<Failure | null>(null)

const {loading, failure} = useAsyncLoader(async () => {
  rules.value = await attendance.listSessionTimeRules(props.sessionId)
  drafts.value = rules.value.map(draftOf)
  loaded.value = true
})

watch([open, () => props.canEdit], async ([isOpen, canEdit]) => {
  if (!isOpen || !canEdit || listsLoaded.value) return
  listsLoaded.value = true
  const [lists, memberList] = await Promise.all([audienceLists(), stationMembers.listMembers().catch(() => [])])
  groups.value = lists.groups
  tags.value = lists.tags
  members.value = memberList.filter(member => !member.formerAt)
})

const heading = computed(() => {
  if (loading.value || !loaded.value) return t('attendanceTimeRules.title')
  return rules.value.length === 0
      ? t('attendanceTimeRules.sheetTitleNone')
      : t('attendanceTimeRules.sheetTitle', {count: rules.value.length})
})

async function save() {
  saveFailure.value = null
  try {
    rules.value = await attendance.setSessionTimeRules(props.sessionId, {rules: drafts.value.map(inputOf)})
    drafts.value = rules.value.map(draftOf)
    emit('changed')
  } catch (e) {
    saveFailure.value = describeFailure(e, t)
    throw e
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="sheet-time-rules">
    <BareButton
        :aria-expanded="open"
        class="flex items-center gap-2 text-sm font-medium"
        data-testid="sheet-time-rules-toggle"
        @click="open = !open"
    >
      <AppIcon :icon="['fas', open ? 'chevron-down' : 'chevron-right']"/>
      <span>{{ heading }}</span>
    </BareButton>
    <FailureAlert :failure="failure ?? sheet?.failure.value ?? null"/>
    <template v-if="open && loaded">
      <FailureAlert :failure="saveFailure"/>
      <template v-if="canEdit">
        <MutedText tag="p" size="sm">{{ t('attendanceTimeRules.sheetHint') }}</MutedText>
        <TimeRulesEditor v-model="drafts" :groups="groups" :tags="tags" :members="members"/>
        <SaveButton :disabled="!savable(drafts)" :action="save">{{ t('attendanceTimeRules.save') }}</SaveButton>
      </template>
      <TimeRulesSummary v-else :rules="rules"/>
    </template>
  </NeutralContainer>
</template>
