/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
</script>

<template>
  <HelpArticle title="Items zuweisen" subtitle="Items an Mitglieder ausgeben und wieder zurücknehmen.">
    <HelpSection title="Wer kann zuweisen?">
      <p>
        Die Zuweisungs-Seite ist nur für Mitglieder mit der Berechtigung
        <em>Inventar zuweisen</em> sichtbar. Diese Berechtigung ist standardmäßig für alle
        Rollen aus, damit die Verantwortung explizit beim Stationsbesitzer liegt.
      </p>
    </HelpSection>

    <HelpSection title="Ablauf">
      <p>
        Wähle ein Mitglied aus, dann scanne das Item, das du übergibst — die Zuweisung wird
        direkt gespeichert. Scannst du das gleiche Item erneut mit dem gleichen Mitglied
        ausgewählt, wird es zurückgenommen.
      </p>
      <p>
        Ein Item kann gleichzeitig nur an einem Ort sein: Sobald du es einem Mitglied gibst,
        verliert es seine Lagerplatz-Zuordnung. Stellst du es zurück ins Lager, wird die
        Zuweisung beim Mitglied beendet.
      </p>
    </HelpSection>

    <HelpTip>
      Der Schnellmodus ist für Schichten mit vielen Items in Folge gedacht — du gibst immer nur
      ein Mitglied an, scannst danach mehrere Items hintereinander.
    </HelpTip>
  </HelpArticle>
</template>
