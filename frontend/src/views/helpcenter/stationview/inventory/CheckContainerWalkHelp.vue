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
  <HelpArticle title="Behälter-Prüfung" subtitle="Den Inhalt eines Lagerbehälters per Scan bestätigen.">
    <HelpSection title="Ablauf">
      <p>
        Im Scan-Feld erfasst du die Items, die du im Behälter vorfindest. Items, die laut System
        in diesem Behälter liegen sollten, kannst du als <strong>bestätigt</strong> markieren;
        fehlende Items bleiben rot. Tauchen zusätzliche Items auf, die das System hier nicht
        erwartet, werden sie unter "Zusätzlich gefunden" gesammelt.
      </p>
      <p>
        Mit dem Schalter <strong>Auch Unter-Behälter einbeziehen</strong> dehnst du die Prüfung
        auf alle untergeordneten Behälter (Schubladen, Kisten, …) aus — der Scan-Verlauf läuft
        dann depth-first durch den Baum.
      </p>
    </HelpSection>

    <HelpSection title="Ergebnis">
      <p>
        Nach Abschluss landet die Prüfung im Verlauf des Behälters und die als verloren
        markierten Items werden im Inventar als verloren geführt.
      </p>
      <HelpTip>
        Eine offen gelassene Zeile wird beim Abschluss als <strong>fehlend</strong> gewertet.
      </HelpTip>
    </HelpSection>
  </HelpArticle>
</template>
