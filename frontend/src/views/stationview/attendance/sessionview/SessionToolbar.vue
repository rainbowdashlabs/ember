/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

const {t} = useI18n()

const props = defineProps<{
  checkMode: boolean
  uncheckedCount: number
  readonly?: boolean
}>()

defineEmits<{
  back: []
  export: []
  sync: []
  startCheckMode: []
  remove: []
}>()

/**
 * Checking the attendance is what the reader came for while anybody is still unchecked, so it is
 * the one action that stays a button of its own.
 *
 * <p>It is not always there: it goes once every entry has been decided, and it is not offered
 * while the check is already running. The export takes its place then, because a finished list is
 * kept in order to be handed on. A reader without edit rights has no action here at all, so their
 * toolbar is the back button and nothing else, exactly as it was before.
 */
const canCheck = computed(() => !props.checkMode && props.uncheckedCount > 0)
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="$emit('back')">
      {{ t('attendanceSession.back') }}
    </SecondaryButton>
    <div v-if="!readonly" class="flex items-center gap-2">
      <PrimaryButton v-if="canCheck" :icon="['fas', 'clipboard-user']" @click="$emit('startCheckMode')">
        {{ t('attendanceSession.checkMode') }} ({{ uncheckedCount }})
      </PrimaryButton>
      <PrimaryButton v-else :icon="['fas', 'download']" @click="$emit('export')">
        {{ t('attendanceSession.export') }}
      </PrimaryButton>
      <ActionsMenu :label="t('common.actions')" test-id="session-actions">
        <DropdownMenuItem v-if="canCheck" :icon="['fas', 'download']" @click="$emit('export')">
          {{ t('attendanceSession.export') }}
        </DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'clipboard-check']" @click="$emit('sync')">
          {{ t('attendanceSession.sync') }}
        </DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'trash']" data-testid="delete-session" destructive
                          @click="$emit('remove')">
          {{ t('attendanceSession.delete') }}
        </DropdownMenuItem>
      </ActionsMenu>
    </div>
  </div>
</template>
