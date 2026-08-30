/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import MemberTypeBadge from './MemberTypeBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useMemberRowExtras} from './memberRowExtras'
import {formatDate} from '@/util/format'
import type {ProfileField} from '@/api/profileFields'
import type {StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  member: StationMember
  visibleColumns: ProfileField[]
  memberGroups: string[]
  memberTags: string[]
  isFieldApplicable: (field: ProfileField) => boolean
  getFieldValue: (fieldId: number) => unknown
  expanded?: boolean
  exportMode?: boolean
  selected?: boolean
  canEdit?: boolean
}>()

const emit = defineEmits<{
  click: []
  toggleSelect: []
  navigateDetail: [event: Event]
  navigateEdit: [event: Event]
  resendSetup: [event: Event]
}>()

const extras = useMemberRowExtras()
const rowNote = computed(() => extras.note(props.member.id))
const blockedReason = computed(() => extras.blockedReason(props.member.id))

/**
 * Whether sending the setup mail again could reach anybody at all.
 *
 * <p>A member entered without an address carries one that was made up for them, ending in
 * {@code .local}, and nothing can be delivered to it. Where a guardian has a real address the mail
 * goes to them instead, which is why the offer stands beside an address that looks dead.
 */
const canBeWrittenTo = computed(() => props.member.mailReaches !== 'NOBODY')

const pendingTitle = computed(() => {
  const base = t('membersList.accountPending')
  if (!props.member.setupMailExpiresAt) return base
  return `${base} ${t('membersList.accountPendingExpires', {date: formatDate(props.member.setupMailExpiresAt)})}`
})

/** What pressing the paper plane will do, said in full, because who receives it is not obvious. */
const resendTitle = computed(() => {
  const who = props.member.mailReaches === 'GUARDIANS'
      ? t('membersList.setupMailToGuardians')
      : t('membersList.setupMailToMember')
  return `${pendingTitle.value} ${who}`
})

/** Why there is no button here, which the hourglass alone never said. */
const waitingTitle = computed(() => `${pendingTitle.value} ${t('membersList.setupMailToNobody')}`)
</script>

<template>
  <TRow
      :class="{
        'bg-bg-light-accent/30 dark:bg-bg-dark-accent/30': !exportMode && expanded,
        'bg-primary/5': exportMode && selected,
        'cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 transition-colors': true,
      }"
      data-testid="member-row"
      @click="emit('click')"
  >
    <td v-if="exportMode" class="px-2 py-2.5" @click.stop>
      <CheckboxInput :model-value="selected ?? false" @update:model-value="emit('toggleSelect')"/>
    </td>
    <Td v-if="!exportMode" @click.stop>
      <MutedText v-if="blockedReason" size="sm" :title="blockedReason">
        <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3"/>
      </MutedText>
      <template v-else>
        <IconButton :icon="['fas', 'eye']" :label="t('membersList.detail')"
                    class="text-primary hover:bg-primary/15"
                    @click="emit('navigateDetail', $event)"/>
        <EditButton v-if="canEdit" @click="emit('navigateEdit', $event)"/>
      </template>
    </Td>
    <Td>
      <div class="flex items-center gap-2">
        <MemberName :identity="member.identity" size="sm" class="font-medium"/>
        <MutedText v-if="rowNote" data-testid="member-note" size="sm">{{ rowNote }}</MutedText>
        <ErrorBadge v-if="member.profileComplete === false" class="ml-1.5 text-[10px]">{{ t('membersList.incomplete') }}</ErrorBadge>
        <IconButton
            v-if="member.accountSetupPending && canEdit && canBeWrittenTo"
            :icon="['fas', 'paper-plane']"
            :title="resendTitle"
            :label="t('membersList.accountPendingResend')"
            class="ml-auto text-warning hover:bg-warning/15"
            @click.stop="emit('resendSetup', $event)"
        />
        <font-awesome-icon
            v-else-if="member.accountSetupPending"
            :icon="['fas', 'hourglass-half']"
            :title="waitingTitle"
            class="ml-auto text-warning w-3.5 h-3.5"
        />
      </div>
    </Td>
    <Td>
      <MemberTypeBadge :user-type="member.userType"/>
    </Td>
    <Td class="text-(--text-muted) text-xs">
      {{ member.email || '–' }}
    </Td>
    <Td v-if="extras.stationLocalColumns" class="text-(--text-muted) text-xs">
      {{ memberGroups.join(', ') || '–' }}
    </Td>
    <Td v-if="extras.stationLocalColumns" class="text-(--text-muted) text-xs">
      {{ memberTags.join(', ') || '–' }}
    </Td>
    <Td
        v-for="field in visibleColumns"
        :key="field.id"
        :class="isFieldApplicable(field) ? 'text-(--text-muted)' : 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40'"
    >
      <template v-if="isFieldApplicable(field)">
        <FieldValueDisplay :value="getFieldValue(field.id)" :field-type="field.fieldType"/>
      </template>
    </Td>
  </TRow>
</template>
