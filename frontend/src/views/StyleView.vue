/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

import ThemeToggle from '@/components/theme/ThemeToggle.vue'

import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SecondaryContainer from '@/components/container/SecondaryContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'

import DownloadButton from '@/components/button/DownloadButton.vue'
import UploadButton from '@/components/button/UploadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ConfirmButton from '@/components/button/ConfirmButton.vue'
import EditButton from '@/components/button/EditButton.vue'

import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import SliderInput from '@/components/input/number/SliderInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeInput from '@/components/input/datetime/TimeInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'

import ProgressBar from '@/components/feedback/ProgressBar.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'

const textValue = ref('')
const textAreaValue = ref('')
const numberValue = ref(0)
const decimalValue = ref(0)
const sliderValue = ref(50)
const dateValue = ref('')
const timeValue = ref('')
const timeShortValue = ref('')
const selectValue = ref('option1')
const toggleValue = ref(false)
const modalOpen = ref(false)
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-6 sm:px-8 sm:py-8 space-y-10 sm:space-y-12">
    <div class="flex items-center justify-between">
      <PageHeader>Style Guide</PageHeader>
      <ThemeToggle/>
    </div>

    <!-- Typography -->
    <section class="space-y-4">
      <SectionHeader>Typography</SectionHeader>
      <div class="space-y-2">
        <PageHeader>Page Header</PageHeader>
        <SectionHeader>Section Header</SectionHeader>
        <SubHeader>Sub Header</SubHeader>
      </div>
    </section>

    <!-- Buttons -->
    <section class="space-y-4">
      <SectionHeader>Buttons</SectionHeader>
      <div class="flex flex-wrap gap-3 items-center">
        <PrimaryButton>Primary</PrimaryButton>
        <SecondaryButton>Secondary</SecondaryButton>
        <SuccessButton>Success</SuccessButton>
        <ErrorButton>Error</ErrorButton>
        <InfoButton>Info</InfoButton>
      </div>
      <div class="flex flex-wrap gap-3 items-center">
        <PrimaryButton disabled>Primary</PrimaryButton>
        <SecondaryButton disabled>Secondary</SecondaryButton>
        <SuccessButton disabled>Success</SuccessButton>
        <ErrorButton disabled>Error</ErrorButton>
        <InfoButton disabled>Info</InfoButton>
      </div>
      <p class="text-sm text-[var(--text-muted)]">Second row: disabled state</p>
    </section>

    <!-- Icon Buttons -->
    <section class="space-y-4">
      <SectionHeader>Icon Buttons</SectionHeader>
      <div class="flex flex-wrap gap-3 items-center">
        <DownloadButton/>
        <UploadButton/>
        <DeleteButton/>
        <ConfirmButton/>
        <EditButton/>
      </div>
      <div class="flex flex-wrap gap-3 items-center">
        <DownloadButton disabled/>
        <UploadButton disabled/>
        <DeleteButton disabled/>
        <ConfirmButton disabled/>
        <EditButton disabled/>
      </div>
      <p class="text-sm text-[var(--text-muted)]">Second row: disabled state</p>
    </section>

    <!-- Text Inputs -->
    <section class="space-y-4">
      <SectionHeader>Text Inputs</SectionHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-1">
          <label class="text-sm font-medium">Text</label>
          <TextInput v-model="textValue" placeholder="Enter text..."/>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Text (disabled)</label>
          <TextInput disabled placeholder="Disabled"/>
        </div>
      </div>
      <div class="space-y-1">
        <label class="text-sm font-medium">Text Area</label>
        <TextAreaInput v-model="textAreaValue" placeholder="Enter longer text..."/>
      </div>
      <div class="space-y-1">
        <label class="text-sm font-medium">Text Area (disabled)</label>
        <TextAreaInput :rows="2" disabled placeholder="Disabled"/>
      </div>
    </section>

    <!-- Number Inputs -->
    <section class="space-y-4">
      <SectionHeader>Number Inputs</SectionHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-1">
          <label class="text-sm font-medium">Number (integer)</label>
          <NumberInput v-model="numberValue" placeholder="Enter number..."/>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Decimal</label>
          <DecimalInput v-model="decimalValue" placeholder="Enter decimal..."/>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Number (disabled)</label>
          <NumberInput disabled/>
        </div>
      </div>
      <div class="space-y-1">
        <label class="text-sm font-medium">Slider</label>
        <SliderInput v-model="sliderValue"/>
      </div>
      <div class="space-y-1">
        <label class="text-sm font-medium">Slider (with input)</label>
        <SliderInput v-model="sliderValue" show-input/>
      </div>
      <div class="space-y-1">
        <label class="text-sm font-medium">Slider (disabled)</label>
        <SliderInput :model-value="40" disabled/>
      </div>
    </section>

    <!-- Date & Time Inputs -->
    <section class="space-y-4">
      <SectionHeader>Date & Time Inputs</SectionHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-1">
          <label class="text-sm font-medium">Date</label>
          <DateInput v-model="dateValue"/>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Time</label>
          <TimeInput v-model="timeValue"/>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Time (no seconds)</label>
          <TimeShortInput v-model="timeShortValue"/>
        </div>
      </div>
    </section>

    <!-- Select -->
    <section class="space-y-4">
      <SectionHeader>Select</SectionHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-1">
          <label class="text-sm font-medium">Select</label>
          <SelectInput v-model="selectValue">
            <option value="option1">Option 1</option>
            <option value="option2">Option 2</option>
            <option value="option3">Option 3</option>
          </SelectInput>
        </div>
        <div class="space-y-1">
          <label class="text-sm font-medium">Select (disabled)</label>
          <SelectInput disabled>
            <option>Disabled</option>
          </SelectInput>
        </div>
      </div>
    </section>

    <!-- Toggle -->
    <section class="space-y-4">
      <SectionHeader>Toggle</SectionHeader>
      <div class="flex flex-wrap items-center gap-4 sm:gap-6">
        <div class="flex items-center gap-2">
          <ToggleInput v-model="toggleValue"/>
          <span class="text-sm">{{ toggleValue ? 'On' : 'Off' }}</span>
        </div>
        <div class="flex items-center gap-2">
          <ToggleInput disabled/>
          <span class="text-sm text-[var(--text-muted)]">Disabled (off)</span>
        </div>
        <div class="flex items-center gap-2">
          <ToggleInput :model-value="true" disabled/>
          <span class="text-sm text-[var(--text-muted)]">Disabled (on)</span>
        </div>
      </div>
    </section>

    <!-- Badges -->
    <section class="space-y-4">
      <SectionHeader>Badges</SectionHeader>
      <div class="flex flex-wrap gap-3 items-center">
        <PrimaryBadge>Primary</PrimaryBadge>
        <SecondaryBadge>Secondary</SecondaryBadge>
        <SuccessBadge>Success</SuccessBadge>
        <ErrorBadge>Error</ErrorBadge>
        <InfoBadge>Info</InfoBadge>
        <SizeBadge>M</SizeBadge>
        <SizeBadge lost>M (lost)</SizeBadge>
      </div>
    </section>

    <!-- Containers -->
    <section class="space-y-4">
      <SectionHeader>Containers</SectionHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <PrimaryContainer>Primary container</PrimaryContainer>
        <SecondaryContainer>Secondary container</SecondaryContainer>
        <SuccessContainer>Success container</SuccessContainer>
        <ErrorContainer>Error container</ErrorContainer>
        <InfoContainer>Info container</InfoContainer>
        <NeutralContainer>Neutral container</NeutralContainer>
      </div>
    </section>

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
          <span class="text-sm">Small</span>
        </div>
        <div class="flex items-center gap-2">
          <Spinner size="md"/>
          <span class="text-sm">Medium</span>
        </div>
        <div class="flex items-center gap-2">
          <Spinner size="lg"/>
          <span class="text-sm">Large</span>
        </div>
      </div>
    </section>

    <!-- Modal -->
    <section class="space-y-4">
      <SectionHeader>Modal</SectionHeader>
      <PrimaryButton @click="modalOpen = true">Open Modal</PrimaryButton>
      <Modal v-model="modalOpen">
        <SubHeader>Modal Title</SubHeader>
        <p class="mt-2 text-sm text-[var(--text-muted)]">This is an example modal dialog with some content.</p>
      </Modal>
    </section>

    <!-- Theme Toggle -->
    <section class="space-y-4">
      <SectionHeader>Theme Toggle</SectionHeader>
      <div class="flex items-center gap-4">
        <ThemeToggle/>
        <span class="text-sm text-[var(--text-muted)]">Click to toggle light / dark</span>
      </div>
    </section>
  </div>
</template>
