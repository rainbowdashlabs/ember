/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SignupChecklistDialog from './SignupChecklistDialog.vue'
import {checklists} from '@/api'
import type {StationEvent} from '@/api/events'
import {StationPermission} from '@/api/types'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'
import {getActingStation} from '@/util/actingStationState'
import {formatDate} from '@/util/format'
import {showToast} from '@/util/toast'

const props = defineProps<{
  event: StationEvent
  /** The evening the sign-ups are read from, which is the one the screen is focused on. */
  effectiveDate: string | null
  memberSet: SignupMemberSet
}>()

const {t} = useI18n()
const router = useRouter()
const {hasPermission} = useSession()

/**
 * An association's calendar is a station's calendar, borrowed for as long as the screen is open. The
 * association has no lists of its own, so an entry here would either lead nowhere or drop the reader
 * out of the association and open the new list under a station they were not looking at.
 */
const insideCluster = computed(() => getActingStation() !== null)

const canCreateChecklist = computed(() => hasPermission(StationPermission.CHECKLIST_MANAGE))

/**
 * Where the menu can do anything at all: an appointment that is signed up for, an evening in view,
 * a panel that is not the association's, and at least one thing the reader may create.
 */
const shown = computed(() =>
    !!props.event.requiresRegistration
    && !!props.effectiveDate
    && !insideCluster.value
    && canCreateChecklist.value)

const dateLabel = computed(() => formatDate(props.effectiveDate))

const suggestedName = computed(() =>
    t('signupLists.namePrefill', {event: props.event.name ?? '', date: dateLabel.value}))

const showChecklist = ref(false)

const {running: creating, error, run: runCreateChecklist} = useAsyncAction(
    async (payload: {name: string; description: string; column: string}) => {
      const detail = await checklists.createChecklist({
        name: payload.name,
        description: payload.description,
        columns: [{label: payload.column}],
        restriction: {
          userTypes: [],
          groupIds: [],
          tagIds: [],
          memberIds: props.memberSet.memberIds,
          mode: 'OR',
        },
      })
      showChecklist.value = false
      showToast(t('signupLists.checklistCreated', {count: props.memberSet.count}), 'success')
      await router.push({name: 'checklist-detail', params: {id: detail.id}})
    },
    {formatError: () => t('signupLists.createError')},
)
</script>

<template>
  <ActionsMenu v-if="shown" :label="t('signupLists.menu')" data-testid="signup-lists-menu">
    <MutedText tag="div" class="px-4 py-2">
      {{ memberSet.usable ? t('signupLists.menuIntro', {date: dateLabel, count: memberSet.count}) : t('signupLists.menuNobody') }}
    </MutedText>
    <DropdownMenuItem
        v-if="canCreateChecklist"
        :icon="['fas', 'list-check']"
        :disabled="!memberSet.usable || creating"
        data-testid="signup-checklist-entry"
        @click="showChecklist = true"
    >
      {{ t('signupLists.checklist') }}
    </DropdownMenuItem>
  </ActionsMenu>

  <SignupChecklistDialog
      v-model="showChecklist"
      :creating="creating"
      :error="error"
      :member-set="memberSet"
      :date-label="dateLabel"
      :suggested-name="suggestedName"
      @submit="runCreateChecklist"
  />
</template>
