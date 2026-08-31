/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import type {AnnouncementDraft} from './announcementPrefill'

/**
 * What an entry written from an appointment says about itself.
 *
 * <p>Two things the author cannot see from the text alone. Which evening is being announced, because
 * a weekly appointment has no single date and the draft was written about exactly one of them. And,
 * the moment the entry is widened to partner stations or to the public page, what it is about to
 * take with it: an overview field is carried whether or not the appointment shows it publicly, so
 * the widening is the point at which a field marked not public would leave the station.
 */
const props = defineProps<{
  draft: AnnouncementDraft
  /** Whether the entry is on its way to the public page. */
  publicBlog: boolean
  /** Whether the entry is on its way to partner stations. */
  federated: boolean
  /** Whether the entry still carries an audience of its own. */
  restricted: boolean
}>()

const {t} = useI18n()

const widening = computed(() => props.publicBlog || props.federated)

/** The fields the appointment itself does not show publicly, which are the ones worth naming. */
const notPublic = computed(() => props.draft.fields.filter(field => !field.isPublic).map(field => field.name))

const carried = computed(() => props.draft.fields.map(field => field.name))
</script>

<template>
  <Alert :variant="widening ? 'error' : 'info'" data-testid="announcement-notice">
    <p class="font-bold">{{ t('news.announcement.title') }}</p>
    <p v-if="draft.dateLabel">{{ t('news.announcement.occurrence', {date: draft.dateLabel}) }}</p>
    <p v-else>{{ t('news.announcement.noOccurrence') }}</p>
    <p>{{ t('news.announcement.snapshot') }}</p>
    <p v-if="draft.linked">{{ t('news.announcement.linkInternal') }}</p>
    <p v-else>{{ t('news.announcement.noLink') }}</p>
    <p v-if="draft.restricted && restricted" data-testid="announcement-restricted">
      {{ t('news.announcement.restricted') }}
    </p>
    <template v-if="widening">
      <p class="font-bold mt-2" data-testid="announcement-widening">{{ t('news.announcement.widening') }}</p>
      <p v-if="carried.length">{{ t('news.announcement.carries', {fields: carried.join(', ')}) }}</p>
      <p v-if="notPublic.length">{{ t('news.announcement.notPublic', {fields: notPublic.join(', ')}) }}</p>
    </template>
  </Alert>
</template>
