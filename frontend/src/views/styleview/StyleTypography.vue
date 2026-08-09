/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import BulletList from '@/components/typography/BulletList.vue'
import StatValue from '@/components/typography/StatValue.vue'

import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'

import CodeSnippet from '@/components/display/CodeSnippet.vue'
import KeyBadge from '@/components/display/KeyBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import AccentIcon from '@/components/display/AccentIcon.vue'
import ColorDot from '@/components/display/ColorDot.vue'
import ProseContent from '@/components/display/ProseContent.vue'
</script>

<template>
  <!-- Typography -->
  <section class="space-y-4">
    <SectionHeader>Typography</SectionHeader>
    <div class="space-y-2">
      <PageHeader>Page Header (h1)</PageHeader>
      <SectionHeader>Section Header (h2)</SectionHeader>
      <SubHeader>Sub Header (h3)</SubHeader>
    </div>
    <div class="space-y-2">
      <SectionLabel>Section Label</SectionLabel>
      <div>
        <MutedText>Muted text (xs, default)</MutedText>
      </div>
      <div>
        <MutedText size="sm">Muted text (sm)</MutedText>
      </div>
      <div>
        <MutedText tag="p" size="sm">Muted text as paragraph</MutedText>
      </div>
      <div>
        <MutedText size="base">Muted text (base, inherits the surrounding size)</MutedText>
      </div>
    </div>
  </section>

  <!-- Field Labels -->
  <section class="space-y-4">
    <SectionHeader>Field Labels</SectionHeader>
    <div class="space-y-3">
      <FieldLabel>Normal label</FieldLabel>
      <FieldLabel hint>Hint label (muted, smaller)</FieldLabel>
      <FieldLabel inline><ToggleInput :model-value="true"/> Inline label (with toggle)</FieldLabel>
      <FieldLabel inline><CheckboxInput :model-value="true"/> Inline label (with checkbox)</FieldLabel>
      <FieldHint>FieldHint shorthand</FieldHint>
    </div>
  </section>

  <!-- Stat Values -->
  <section class="space-y-4">
    <SectionHeader>Stat Value</SectionHeader>
    <div class="flex gap-6">
      <div class="text-center">
        <StatValue>42</StatValue>
        <MutedText tag="div">Primary</MutedText>
      </div>
      <div class="text-center">
        <StatValue color="success">18</StatValue>
        <MutedText tag="div">Success</MutedText>
      </div>
      <div class="text-center">
        <StatValue color="error">3</StatValue>
        <MutedText tag="div">Error</MutedText>
      </div>
      <div class="text-center">
        <StatValue color="muted">0</StatValue>
        <MutedText tag="div">Muted</MutedText>
      </div>
    </div>
  </section>

  <!-- Bullet List -->
  <section class="space-y-4">
    <SectionHeader>Bullet List</SectionHeader>
    <BulletList>
      <li>First item</li>
      <li>Second item</li>
      <li>Third item</li>
    </BulletList>
  </section>

  <!-- Display Components -->
  <section class="space-y-4">
    <SectionHeader>Display Components</SectionHeader>
    <div class="space-y-3">
      <SubHeader>Code Snippet</SubHeader>
      <p class="text-sm">Inline: <CodeSnippet>npm run build</CodeSnippet></p>
      <CodeSnippet block>const x = 42;
const doubled = x * 2;</CodeSnippet>
    </div>
    <div class="space-y-3">
      <SubHeader>Key Badge</SubHeader>
      <div class="flex gap-2 items-center text-xs">
        <KeyBadge>Ctrl</KeyBadge> + <KeyBadge>S</KeyBadge> to save
      </div>
    </div>
    <div class="space-y-3">
      <SubHeader>Muted Icon</SubHeader>
      <div class="flex gap-4 items-center">
        <MutedIcon :icon="['fas', 'chevron-right']" size="xs"/>
        <MutedIcon :icon="['fas', 'chevron-right']"/>
        <MutedIcon :icon="['fas', 'chevron-right']" size="md"/>
        <MutedIcon :icon="['fas', 'chevron-right']" size="inline"/>
        <MutedText>xs / sm (default) / md / inline</MutedText>
      </div>
    </div>
  </section>

  <section class="space-y-4">
    <SectionHeader>Markers and Rich Text</SectionHeader>
    <div class="space-y-3">
      <SubHeader>Accent Icon</SubHeader>
      <div class="flex gap-3 items-start">
        <AccentIcon :icon="['fas', 'server']"/>
        <MutedText>Leading icon for feature and requirement rows</MutedText>
      </div>
    </div>
    <div class="space-y-3">
      <SubHeader>Color Dot</SubHeader>
      <div class="flex gap-3 items-center">
        <ColorDot color="#FF6421"/>
        <ColorDot color="#3694FF"/>
        <ColorDot color="#00C507"/>
        <MutedText>Group and tag colours</MutedText>
      </div>
    </div>
    <div class="space-y-3">
      <SubHeader>Prose Content</SubHeader>
      <ProseContent>
        <p>Rendered markdown lands here with headings, lists and links already styled.</p>
      </ProseContent>
    </div>
  </section>
</template>
