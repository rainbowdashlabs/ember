/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import SliderInput from '@/components/input/number/SliderInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeInput from '@/components/input/datetime/TimeInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import CompactToggle from '@/components/input/toggle/CompactToggle.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'

const textValue = ref('')
const textAreaValue = ref('')
const passwordValue = ref('')
const numberValue = ref(0)
const decimalValue = ref(0)
const sliderValue = ref(50)
const dateValue = ref('')
const timeValue = ref('')
const timeShortValue = ref('')
const selectValue = ref('option1')
const toggleValue = ref(false)
const checkboxValue = ref(true)
const radioValue = ref('a')
</script>

<template>
  <!-- Text Inputs -->
  <section class="space-y-4">
    <SectionHeader>Text Inputs</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div class="space-y-1">
        <FieldLabel>Text</FieldLabel>
        <TextInput v-model="textValue" placeholder="Enter text..."/>
      </div>
      <div class="space-y-1">
        <FieldLabel>Text (disabled)</FieldLabel>
        <TextInput disabled placeholder="Disabled"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>Password</FieldLabel>
        <PasswordInput v-model="passwordValue" placeholder="Enter password..."/>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel>Text Area</FieldLabel>
      <TextAreaInput v-model="textAreaValue" placeholder="Enter longer text..."/>
    </div>
  </section>

  <!-- Number Inputs -->
  <section class="space-y-4">
    <SectionHeader>Number Inputs</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div class="space-y-1">
        <FieldLabel>Number (integer)</FieldLabel>
        <NumberInput v-model="numberValue" placeholder="Enter number..."/>
      </div>
      <div class="space-y-1">
        <FieldLabel>Decimal</FieldLabel>
        <DecimalInput v-model="decimalValue" placeholder="Enter decimal..."/>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel>Slider</FieldLabel>
      <SliderInput v-model="sliderValue"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>Slider (with input)</FieldLabel>
      <SliderInput v-model="sliderValue" show-input/>
    </div>
  </section>

  <!-- Date & Time Inputs -->
  <section class="space-y-4">
    <SectionHeader>Date & Time Inputs</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div class="space-y-1">
        <FieldLabel>Date</FieldLabel>
        <DateInput v-model="dateValue"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>Time</FieldLabel>
        <TimeInput v-model="timeValue"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>Time (no seconds)</FieldLabel>
        <TimeShortInput v-model="timeShortValue"/>
      </div>
    </div>
  </section>

  <!-- Select -->
  <section class="space-y-4">
    <SectionHeader>Select</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div class="space-y-1">
        <FieldLabel>Select</FieldLabel>
        <SelectInput v-model="selectValue" class="w-full">
          <option value="option1">Option 1</option>
          <option value="option2">Option 2</option>
          <option value="option3">Option 3</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>Select (disabled)</FieldLabel>
        <SelectInput disabled class="w-full">
          <option>Disabled</option>
        </SelectInput>
      </div>
    </div>
  </section>

  <!-- Toggle & Checkbox & Radio -->
  <section class="space-y-4">
    <SectionHeader>Toggle</SectionHeader>
    <div class="flex flex-wrap items-center gap-4 sm:gap-6">
      <FieldLabel inline><ToggleInput v-model="toggleValue"/> {{ toggleValue ? 'On' : 'Off' }}</FieldLabel>
      <FieldLabel inline class="opacity-60"><ToggleInput disabled/> Disabled (off)</FieldLabel>
      <FieldLabel inline class="opacity-60"><ToggleInput :model-value="true" disabled/> Disabled (on)</FieldLabel>
    </div>
  </section>

  <section class="space-y-4">
    <SectionHeader>Compact Toggle</SectionHeader>
    <div class="flex flex-wrap items-center gap-4 sm:gap-6">
      <FieldLabel inline><CompactToggle v-model="toggleValue"/> {{ toggleValue ? 'On' : 'Off' }}</FieldLabel>
      <FieldLabel inline class="opacity-60"><CompactToggle disabled/> Disabled</FieldLabel>
    </div>
  </section>

  <section class="space-y-4">
    <SectionHeader>Checkbox</SectionHeader>
    <div class="flex flex-wrap items-center gap-4 sm:gap-6">
      <FieldLabel inline class="cursor-pointer"><CheckboxInput v-model="checkboxValue"/> Checked</FieldLabel>
      <FieldLabel inline class="cursor-pointer"><CheckboxInput :model-value="false"/> Unchecked</FieldLabel>
      <FieldLabel inline class="opacity-60"><CheckboxInput disabled/> Disabled</FieldLabel>
    </div>
  </section>

  <section class="space-y-4">
    <SectionHeader>Radio</SectionHeader>
    <div class="flex flex-wrap items-center gap-4 sm:gap-6">
      <FieldLabel inline class="cursor-pointer"><RadioInput v-model="radioValue" value="a"/> Option A</FieldLabel>
      <FieldLabel inline class="cursor-pointer"><RadioInput v-model="radioValue" value="b"/> Option B</FieldLabel>
      <FieldLabel inline class="cursor-pointer"><RadioInput v-model="radioValue" value="c"/> Option C</FieldLabel>
    </div>
  </section>
</template>
