/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DummyFrozenQuestion from '@/views/helpcenter/stationview/quiz/testdetailhelp/DummyFrozenQuestion.vue'
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex items-center justify-between mb-2">
        <span class="font-semibold text-sm">Testfragen (3)</span>
        <SecondaryButton :icon="['fas', 'rotate']" disabled>
          Fragen generieren
        </SecondaryButton>
      </div>
      <DummyFrozenQuestion :index="1" text="Was ist die Hauptaufgabe der Feuerwehr?" type="Multiple Choice" points="2 Punkte" />
      <DummyFrozenQuestion :index="2" text="Ordne die Schläuche dem Durchmesser zu." type="Zuordnung" points="4 Punkte" />
      <DummyFrozenQuestion :index="3" text="Die Feuerwehr ist nur für Brände zuständig." type="Wahr/Falsch" points="1 Punkt" />
    </div>
  </NeutralContainer>
</template>
