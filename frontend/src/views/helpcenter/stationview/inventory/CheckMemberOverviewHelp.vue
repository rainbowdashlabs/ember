/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
</script>

<template>
  <HelpArticle title="Mitglieder-Prüfung" subtitle="Mitglieder zur Inventur auswählen.">
    <HelpSection title="Übersicht">
      <p>
        Hier siehst du eine Liste aller Mitglieder, mit der Anzahl an aktuell zugewiesenen Items
        und dem Datum der letzten Prüfung. Wähle ein Mitglied aus, um die Prüfung zu starten.
      </p>
    </HelpSection>

    <HelpSection title="Ablauf einer Prüfung">
      <p>
        Beim Start der Prüfung wird das Mitglied für die Dauer gesperrt, damit niemand sonst
        seine Items zur gleichen Zeit verändert. Du scannst die Items ein, das System markiert sie
        als bestätigt. Fehlende oder zusätzliche Items werden im Ergebnis sichtbar.
      </p>
    </HelpSection>
  </HelpArticle>
</template>
