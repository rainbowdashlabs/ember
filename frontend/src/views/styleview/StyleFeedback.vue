/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

import PrimaryButton from '@/components/button/PrimaryButton.vue'

import ProgressBar from '@/components/feedback/ProgressBar.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'

const modalOpen = ref(false)
</script>

<template>
  <!-- Progress Bars -->
  <section class="space-y-4">
    <SectionHeader>Progress Bar</SectionHeader>
    <div class="space-y-3">
      <ProgressBar :value="75"/>
      <ProgressBar :value="50" variant="secondary"/>
      <ProgressBar :value="100" variant="success"/>
      <ProgressBar :value="30" variant="error"/>
      <ProgressBar :value="60" variant="info"/>
    </div>
  </section>

  <!-- Alerts -->
  <section class="space-y-4">
    <SectionHeader>Alerts</SectionHeader>
    <div class="space-y-3">
      <Alert variant="info">This is an informational message.</Alert>
      <Alert variant="success">Operation completed successfully.</Alert>
      <Alert variant="error">Something went wrong. Please try again.</Alert>
    </div>
  </section>

  <!-- Spinner -->
  <section class="space-y-4">
    <SectionHeader>Spinner</SectionHeader>
    <div class="flex flex-wrap items-center gap-4 sm:gap-6">
      <div class="flex items-center gap-2">
        <Spinner size="sm"/>
        <MutedText size="sm">Small</MutedText>
      </div>
      <div class="flex items-center gap-2">
        <Spinner size="md"/>
        <MutedText size="sm">Medium</MutedText>
      </div>
      <div class="flex items-center gap-2">
        <Spinner size="lg"/>
        <MutedText size="sm">Large</MutedText>
      </div>
    </div>
  </section>

  <!-- Empty State -->
  <section class="space-y-4">
    <SectionHeader>Empty State</SectionHeader>
    <EmptyState>No items found.</EmptyState>
    <EmptyState compact>No items (compact).</EmptyState>
    <EmptyState message="No items (message prop)."/>
  </section>

  <!-- Async Section -->
  <section class="space-y-4">
    <SectionHeader>Async Section</SectionHeader>
    <AsyncSection :loading="true"/>
    <AsyncSection :loading="false" error="Loading failed."/>
    <AsyncSection :empty="true" :loading="false" empty-message="Nothing here yet."/>
    <AsyncSection :empty="false" :loading="false">
      <MutedText size="sm">Loaded content renders in the default slot.</MutedText>
    </AsyncSection>
  </section>

  <!-- Modal -->
  <section class="space-y-4">
    <SectionHeader>Modal</SectionHeader>
    <PrimaryButton @click="modalOpen = true">Open Modal</PrimaryButton>
    <Modal v-model="modalOpen">
      <SubHeader>Modal Title</SubHeader>
      <MutedText tag="p" size="sm" class="mt-2">This is an example modal dialog with some content.</MutedText>
    </Modal>
  </section>
</template>
