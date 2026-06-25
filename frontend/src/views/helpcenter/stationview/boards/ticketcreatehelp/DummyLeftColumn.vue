/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import DummyChecklist from './DummyChecklist.vue'
import DummyWeblinks from './DummyWeblinks.vue'
import DummyTicketLinks from './DummyTicketLinks.vue'

const {t} = useI18n()
</script>

<template>
  <div class="lg:col-span-2 space-y-4">
    <div>
      <FieldLabel class="mb-1">{{ t('boards.ticketTitle') }} *</FieldLabel>
      <TextInput model-value="Ausrüstung für Wettkampf prüfen" />
    </div>

    <div>
      <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
      <MarkdownEditor model-value="Alle Helme und Jacken auf Vollständigkeit prüfen. @Lisa bitte die Schläuche kontrollieren." :placeholder="t('boards.ticketDescription')" />
    </div>

    <DummyChecklist />
    <DummyWeblinks />
    <DummyTicketLinks />
  </div>
</template>
