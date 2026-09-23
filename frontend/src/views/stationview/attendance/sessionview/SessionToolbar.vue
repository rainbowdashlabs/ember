/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

const {t} = useI18n()

const props = defineProps<{
  checkMode: boolean
  uncheckedCount: number
  readonly?: boolean
  locked?: boolean
  canManage?: boolean
  /** The appointment this sheet was taken for, which the menu then leads back to. */
  eventId?: number | null
}>()

defineEmits<{
  back: []
  export: []
  sync: []
  startCheckMode: []
  remove: []
  unlock: []
  lock: []
  openEvent: []
}>()

/**
 * Checking the attendance is what the reader came for while anybody is still unchecked, so it is
 * the one action that stays a button of its own.
 *
 * <p>It is not always there: it goes once every entry has been decided, it is not offered while
 * the check is already running, and a closed sheet takes no more answers at all. The export takes
 * its place then, because a finished list is kept in order to be handed on.
 */
const canCheck = computed(() =>
    !props.locked && !props.readonly && !props.checkMode && props.uncheckedCount > 0)

/**
 * The actions a sheet in this state still has, beyond the one that is a button.
 *
 * <p>A reader who may only look, and a closed sheet, keep the way back to the appointment and
 * nothing else, which is why the menu outlives the editing actions inside it.
 */
const editable = computed(() => !props.locked && !props.readonly)

const hasMenu = computed(() => editable.value || props.eventId != null)
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="$emit('back')">
      {{ t('attendanceSession.back') }}
    </SecondaryButton>
    <ButtonRow align="end">
      <PrimaryButton v-if="canCheck" :icon="['fas', 'clipboard-user']" @click="$emit('startCheckMode')">
        {{ t('attendanceSession.checkMode') }} ({{ uncheckedCount }})
      </PrimaryButton>
      <PrimaryButton v-else-if="locked || !readonly" :icon="['fas', 'download']" @click="$emit('export')">
        {{ t('attendanceSession.export') }}
      </PrimaryButton>
      <SecondaryButton
          v-if="locked && canManage"
          :icon="['fas', 'lock-open']"
          data-testid="unlock-session"
          @click="$emit('unlock')"
      >
        {{ t('attendanceSession.reopen') }}
      </SecondaryButton>
      <ActionsMenu v-if="hasMenu" :label="t('common.actions')" test-id="session-actions">
        <DropdownMenuItem v-if="eventId" :icon="['fas', 'calendar-days']" data-testid="session-event"
                          @click="$emit('openEvent')">
          {{ t('attendanceSession.openEvent') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="editable && canCheck" :icon="['fas', 'download']" @click="$emit('export')">
          {{ t('attendanceSession.export') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="editable" :icon="['fas', 'clipboard-check']" @click="$emit('sync')">
          {{ t('attendanceSession.sync') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="editable && canManage" :icon="['fas', 'lock']" data-testid="lock-session"
                          @click="$emit('lock')">
          {{ t('attendanceSession.close') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="editable" :icon="['fas', 'trash']" data-testid="delete-session" destructive
                          @click="$emit('remove')">
          {{ t('attendanceSession.delete') }}
        </DropdownMenuItem>
      </ActionsMenu>
    </ButtonRow>
  </div>
</template>
