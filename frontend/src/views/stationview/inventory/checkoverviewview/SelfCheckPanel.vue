/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {selfChecks} from '@/api'
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import type {SelfCheckTask} from '@/api/selfChecks'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDate} from '@/util/format'
import {memberName} from './memberHelpers'

/**
 * The tasks this station has out, so whoever handed them out can chase the ones nobody answered.
 *
 * <p>A member is asked rather than made to answer: the task counts towards their outstanding work
 * and shows on their dashboard, and it never stands in the doorway after they sign in.
 */
const props = defineProps<{
  members: MemberCheckSummary[]
  reviewRoute: string
}>()

const {t} = useI18n()
const router = useRouter()

const {config: tasks, loading, error, reload} = useConfigPanel<SelfCheckTask[]>({
  initial: [],
  fetch: () => selfChecks.listTasks(false),
})

const askMemberId = ref('')
const dueOn = ref('')

const askable = computed(() =>
  props.members.filter(member => !tasks.value.some(task => task.memberId === member.memberId)),
)

const {running: asking, error: askError, run: ask} = useAsyncAction(async () => {
  if (!askMemberId.value) return
  await selfChecks.handOut([Number(askMemberId.value)], dueOn.value || null)
  askMemberId.value = ''
  dueOn.value = ''
  await reload()
})

function open(task: SelfCheckTask) {
  router.push({name: props.reviewRoute, params: {id: task.id}})
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="self-check-panel">
    <SubHeader>{{ t('selfCheck.panel.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('selfCheck.panel.hint') }}</MutedText>

    <Alert v-if="error || askError" variant="error">{{ error || askError }}</Alert>

    <div class="flex flex-col gap-2 sm:flex-row sm:items-end">
      <div class="flex-1 space-y-1">
        <FieldLabel>{{ t('selfCheck.panel.member') }}</FieldLabel>
        <SelectInput v-model="askMemberId" class="w-full" data-testid="self-check-ask-member">
          <option value="" disabled>{{ t('selfCheck.panel.pickMember') }}</option>
          <option v-for="member in askable" :key="member.memberId" :value="String(member.memberId)">
            {{ memberName(member) }}
          </option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('selfCheck.panel.dueOn') }}</FieldLabel>
        <DateInput v-model="dueOn" data-testid="self-check-ask-due"/>
      </div>
      <PrimaryButton :disabled="asking || !askMemberId" data-testid="self-check-ask" @click="ask">
        {{ t('selfCheck.panel.ask') }}
      </PrimaryButton>
    </div>

    <MutedText v-if="!loading && tasks.length === 0" size="sm" tag="p" data-testid="self-check-none">
      {{ t('selfCheck.panel.none') }}
    </MutedText>

    <div v-for="task in tasks" :key="task.id" class="flex items-center gap-2 text-sm" :data-testid="`self-check-task-${task.id}`">
      <div class="flex-1 min-w-0">
        <div class="truncate">{{ task.memberName }}</div>
        <MutedText size="xs" tag="p">
          {{ t(`selfCheck.state.${task.state}`) }}
          <template v-if="task.dueOn"> - {{ t('selfCheck.dueOn', {date: formatDate(task.dueOn)}) }}</template>
        </MutedText>
      </div>
      <SecondaryButton
          v-if="task.state === 'SUBMITTED'"
          class="text-xs px-3 py-1.5"
          :data-testid="`self-check-review-${task.id}`"
          @click="open(task)"
      >
        {{ t('selfCheck.panel.review') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
