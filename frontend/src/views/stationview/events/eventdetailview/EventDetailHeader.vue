/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import {useNewsRoutes} from '@/composables/useNewsRoutes'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import {isRecurringEvent, type StationEvent} from '@/api/events'
import {formatDate} from '@/util/format'
import {computed} from 'vue'

const props = defineProps<{
  event: StationEvent
  canManageEvents: boolean
  /** Whether the reader may write news here, which is what the announcement entry hangs on. */
  canWriteNews: boolean
  /** The one occurrence the page is showing, which is the one an announcement is about. */
  effectiveDate: string | null
  /** What the event is called a kind of, shown beside its name. Absent where it has no category. */
  categoryName?: string
}>()

const emit = defineEmits<{
  (e: 'cancel'): void
}>()

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()
const newsRoutes = useNewsRoutes()

/** How long the series runs, where it was given an end. A series without one says nothing. */
const repeatEnd = computed(() => {
  if (!isRecurringEvent(props.event.eventType)) return ''
  if (props.event.repeatUntil) return t('events.repeatUntilLabel', {date: formatDate(props.event.repeatUntil)})
  if (props.event.repeatCount) return t('events.repeatCountLabel', {count: props.event.repeatCount})
  return ''
})

const hasMenu = computed(() => props.canWriteNews || (props.canManageEvents && !props.event.cancelled))

function goBack() {
  router.push({name: props.canManageEvents ? 'events' : 'events-upcoming'})
}

function goEdit() {
  router.push({name: eventRoutes.edit, params: {id: props.event.id}})
}

/**
 * Opens the news editor on a draft written from this appointment.
 *
 * <p>The appointment and the evening travel in the address, so the editor reads both from the
 * server rather than trusting a handover, and so a reload does not lose the draft. Which occurrence
 * matters: announcing a weekly Tuesday without saying which one is exactly the mistake that gets
 * made when the date is retyped by hand.
 */
function announce() {
  const query: Record<string, string> = {event: String(props.event.id)}
  if (props.effectiveDate) query.date = props.effectiveDate
  router.push({name: newsRoutes.create, query})
}
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-3">
    <div class="flex items-center gap-3">
      <SubHeader>{{ event.name }}</SubHeader>
      <SecondaryBadge v-if="event.categoryId && props.categoryName" data-testid="event-category">
        {{ props.categoryName }}
      </SecondaryBadge>
      <SecondaryBadge v-if="isRecurringEvent(event.eventType)">
        <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>{{ t('events.typeRecurring') }}
      </SecondaryBadge>
      <SecondaryBadge v-else>{{ t('events.typeOneTime') }}</SecondaryBadge>
      <SecondaryBadge v-if="repeatEnd" data-testid="event-repeat-end">{{ repeatEnd }}</SecondaryBadge>
      <ErrorBadge v-if="event.cancelled">{{ t('events.cancelled') }}</ErrorBadge>
    </div>
    <!--
      The appointment is opened to be changed, so editing stays a button of its own. Announcing it
      and calling it off are both occasional, and calling it off comes last and coloured because a
      full width row under a harmless one reads as harmless otherwise.
    -->
    <div class="flex items-center gap-2">
      <SecondaryButton @click="goBack"><font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>{{ t('common.back') }}</SecondaryButton>
      <PrimaryButton v-if="canManageEvents" @click="goEdit"><font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>{{ t('events.editEvent') }}</PrimaryButton>
      <ActionsMenu v-if="hasMenu" :label="t('common.actions')" test-id="event-actions">
        <DropdownMenuItem v-if="canWriteNews" :icon="['fas', 'bullhorn']" data-testid="event-announce"
                          @click="announce">
          {{ t('events.announceAsNews') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="canManageEvents && !event.cancelled" :icon="['fas', 'ban']" destructive
                          @click="emit('cancel')">
          {{ t('events.cancelEvent') }}
        </DropdownMenuItem>
      </ActionsMenu>
    </div>
  </div>
</template>
