/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SessionsSection from '@/views/stationview/profile/settingsview/SessionsSection.vue'
import GdprSection from '@/views/stationview/profile/settingsview/GdprSection.vue'
import type {PitchTrust} from './pitchTypes'

/** The account's own sections: where it is logged in, and what it can take with it. */
defineProps<{
  trust: PitchTrust
}>()
</script>

<template>
  <SessionsSection v-if="trust.sessions" :sessions="trust.sessions"/>

  <GdprSection v-if="trust.gdpr" :exporting-gdpr="false" :exporting-member-id="null"
               :managed-members="trust.gdpr.managedMembers" :deleting-account="false"/>
</template>
