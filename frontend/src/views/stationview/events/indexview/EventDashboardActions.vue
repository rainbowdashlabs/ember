/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

/** What a reader can do to the station's appointments as a whole, above the lists of them. */
const emit = defineEmits<{
  addEvent: []
}>()

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <SectionHeader>{{ t('events.allEvents') }}</SectionHeader>
    <ButtonRow align="end">
      <SecondaryButton :icon="['fas', 'folder-plus']" @click="router.push({name: eventRoutes.categories})">
        {{ t('events.manageCategories') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'calendar-plus']" @click="router.push({name: eventRoutes.batch})">
        {{ t('sidebar.eventBatch') }}
      </SecondaryButton>
      <PrimaryButton data-onboarding="events.create" :icon="['fas', 'plus']" @click="emit('addEvent')">
        {{ t('events.addEvent') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
</template>
