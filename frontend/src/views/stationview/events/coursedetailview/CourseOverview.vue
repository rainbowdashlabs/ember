/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProseContent from '@/components/display/ProseContent.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import type {CourseDetail} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {asksForAll} from '@/util/courseChoice'
import {formatDateTime} from '@/util/format'
import {renderMarkdown} from '@/util/markdown'

/**
 * What a course is: its description, places, deadline and confirmation, and its parts grouped by set
 * with how many of each set a sign-up has to choose. A part links to its appointment.
 */
const props = defineProps<{
  detail: CourseDetail
  /** Where a part leads; to the station's own appointment unless a partner's course page says otherwise. */
  partLink?: (eventId: number) => RouteLocationRaw
}>()

function linkOf(eventId: number): RouteLocationRaw {
  return props.partLink ? props.partLink(eventId) : {name: 'event-detail', params: {id: eventId}}
}

const {t} = useI18n()
const {stationTimezone} = useSession()

const course = computed(() => props.detail.course)

/** The parts of a set in date order. */
function partsOf(setId: number) {
  return props.detail.parts.filter(part => part.setId === setId)
}

/** The rule a set states, "all mandatory" or "at least n of m". */
function ruleOf(setId: number): string {
  const set = props.detail.sets.find(candidate => candidate.id === setId)!
  if (asksForAll(set, props.detail.parts)) return t('courses.setAll')
  return t('courses.setRule', {minimum: set.minimum, count: partsOf(setId).length})
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="course-overview">
    <div class="flex flex-wrap items-center gap-2">
      <ErrorBadge v-if="course.cancelled">
        {{ course.cancelReason ? t('courses.cancelledReason', {reason: course.cancelReason}) : t('courses.cancelled') }}
      </ErrorBadge>
      <InfoBadge v-if="course.registrationDeadline">
        {{ t('courses.deadline', {date: formatDateTime(course.registrationDeadline, stationTimezone)}) }}
      </InfoBadge>
      <MutedText tag="span" size="sm">
        {{ course.registrationLimit != null
          ? t('courses.places', {holding: course.holding, limit: course.registrationLimit})
          : t('courses.placesOpen', {holding: course.holding}) }}
      </MutedText>
      <MutedText v-if="course.requiresConfirmation" tag="span" size="sm">{{ t('courses.confirmation') }}</MutedText>
    </div>
    <ProseContent v-if="course.description" v-html="renderMarkdown(course.description)"/>
    <SectionHeader>{{ t('courses.parts') }}</SectionHeader>
    <MutedText v-if="detail.parts.length === 0" tag="p" size="sm">{{ t('courses.noParts') }}</MutedText>
    <section v-for="set in detail.sets" :key="set.id" class="space-y-2" :data-testid="`course-set-${set.id}`">
      <SubHeader>{{ set.name }} <MutedText tag="span" size="sm">({{ ruleOf(set.id) }})</MutedText></SubHeader>
      <ul class="space-y-1">
        <li v-for="part in partsOf(set.id)" :key="part.eventId">
          <RowLink :to="linkOf(part.eventId)" class="flex flex-wrap items-center gap-2 text-sm">
            <span :class="{'line-through text-(--text-muted)': part.calledOff}">{{ part.name }}</span>
            <MutedText tag="span" size="sm">{{ formatDateTime(part.startTime, stationTimezone) }}</MutedText>
            <ErrorBadge v-if="part.calledOff">{{ t('courses.partCalledOff') }}</ErrorBadge>
            <MutedText v-else-if="part.tookPlace" tag="span" size="sm">{{ t('courses.partPast') }}</MutedText>
          </RowLink>
        </li>
      </ul>
    </section>
  </NeutralContainer>
</template>
