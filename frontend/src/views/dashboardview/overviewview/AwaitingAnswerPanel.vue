/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import {eventCourses, events} from '@/api'
import type {AwaitingAnswer, CourseAwaiting, EventCategory} from '@/api/generated/schema'

/**
 * The events whose registration is running out and which nobody in the household has answered.
 *
 * <p>One row per event, naming who still owes an answer, because a household usually answers the same way
 * for everyone and reading the event once is how somebody decides that.
 *
 * <p>The row leads to the event, where both answers are offered: signing up, or signing off, which
 * stops the reminders as well.
 *
 * <p>Only events open to the person and still open for answers appear, which is the same question the
 * warning notification asks, so a reader who acts on the notification finds the row gone. Courses
 * appear the same way, once each and not once per part, and lead to the course.
 */
const {t} = useI18n()

const awaiting = ref<AwaitingAnswer[]>([])
const courses = ref<CourseAwaiting[]>([])
const categories = ref<EventCategory[]>([])

/** The event the row is about. */
function eventPage(entry: AwaitingAnswer) {
  return {name: 'event-detail', params: {id: entry.eventId}}
}

/** The category the event was put in, absent where it was put in none. */
function categoryOf(entry: AwaitingAnswer): EventCategory | undefined {
  return entry.categoryId != null ? categories.value.find(cat => cat.id === entry.categoryId) : undefined
}

/** Whole days until registration closes, floored, so "today" reads as zero rather than as one. */
function daysLeft(entry: {registrationDeadline?: string | null}): number {
  if (!entry.registrationDeadline) return 0
  const closes = new Date(entry.registrationDeadline).getTime()
  return Math.max(0, Math.floor((closes - Date.now()) / 86400000))
}

const soonest = computed(() => Math.min(
    ...[...awaiting.value, ...courses.value].map(entry => daysLeft(entry)),
    Number.MAX_SAFE_INTEGER))
const anything = computed(() => awaiting.value.length + courses.value.length > 0)

/** Loads the panel, which says nothing when it cannot load rather than breaking the page. */
async function loadData() {
  const [entries, cats, open] = await Promise.all([
    events.listAwaitingAnswer().catch(() => []),
    events.listCategories().catch(() => []),
    eventCourses.listAwaiting().catch(() => []),
  ])
  awaiting.value = entries
  categories.value = cats
  courses.value = [...open].sort((a, b) => daysLeft(a) - daysLeft(b))
}

onMounted(loadData)

defineExpose({reload: loadData})
</script>

<template>
  <NeutralContainer v-if="anything" class="flex flex-col max-h-[66vh]">
    <SectionHeader class="mb-3 shrink-0">
      <font-awesome-icon :icon="['fas', 'clock']" class="mr-2"/>
      {{ t('dashboard.awaitingAnswer') }}
      <InfoBadge v-if="soonest <= 1" class="ml-2">{{ t('dashboard.awaitingSoon') }}</InfoBadge>
    </SectionHeader>
    <div class="overflow-y-auto flex-1 space-y-2">
      <EmptyState v-if="!anything" compact>{{ t('dashboard.noAwaitingAnswer') }}</EmptyState>
      <RowLink v-for="entry in awaiting" :key="entry.eventId" :to="eventPage(entry)">
        <NeutralContainer
            data-testid="awaiting-answer"
            class="flex items-center justify-between gap-2 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2 min-w-0">
              <p class="truncate text-sm font-medium">{{ entry.name }}</p>
              <ColorBadge v-if="categoryOf(entry)" :color="categoryOf(entry)!.color"
                          class="shrink-0" data-testid="dashboard-event-category">
                {{ categoryOf(entry)!.name }}
              </ColorBadge>
            </div>
            <p class="text-xs text-(--text-muted)">
              {{ entry.members.map(m => m.name).join(', ') }}
            </p>
          </div>
          <span class="text-xs text-(--text-muted) shrink-0">
            {{ t('dashboard.awaitingDaysLeft', {days: daysLeft(entry)}) }}
          </span>
        </NeutralContainer>
      </RowLink>
      <RowLink v-for="entry in courses" :key="`course-${entry.courseId}`"
               :to="{name: 'event-course', params: {courseId: entry.courseId}}">
        <NeutralContainer
            data-testid="awaiting-course"
            class="flex items-center justify-between gap-2 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
        >
          <div class="min-w-0">
            <p class="truncate text-sm font-medium">
              <font-awesome-icon :icon="['fas', 'graduation-cap']" class="mr-1 h-3 w-3"/>{{ entry.name }}
            </p>
            <p class="text-xs text-(--text-muted)">{{ entry.members.map(m => m.name).join(', ') }}</p>
          </div>
          <span class="text-xs text-(--text-muted) shrink-0">
            {{ t('dashboard.awaitingDaysLeft', {days: daysLeft(entry)}) }}
          </span>
        </NeutralContainer>
      </RowLink>
    </div>
  </NeutralContainer>
</template>
