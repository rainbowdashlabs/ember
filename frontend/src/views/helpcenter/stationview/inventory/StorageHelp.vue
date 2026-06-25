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
  <HelpArticle title="Lager" subtitle="Räume, Schränke, Schubladen und Kisten verwalten.">
    <HelpSection title="Wozu ist das gut?">
      <p>
        Hier baust du eine Karte deiner Lagerräume auf: ein Raum kann mehrere Schränke enthalten,
        ein Schrank mehrere Schubladen, eine Schublade mehrere Kisten — alles so tief verschachtelt,
        wie du es brauchst.
      </p>
      <p>
        Items aus deinem Inventar werden in Behältern abgelegt. So weißt du jederzeit, wo eine
        Sache hingehört, und kannst sie per Scan wiederfinden.
      </p>
    </HelpSection>

    <HelpSection title="Neuen Behälter anlegen">
      <p>
        Klicke oben rechts auf "Neuer Behälter". Vergib einen Namen, wähle optional einen Typ
        (Raum, Schrank, Kiste, …) und ordne den Behälter optional einem übergeordneten Behälter zu.
        Wenn der Behälter scannbar sein soll, gibst du einen internen Code ein, der auf einem
        Etikett aufgeklebt werden kann.
      </p>
    </HelpSection>

    <HelpSection title="Suchen & Scannen">
      <p>
        Über das Suchfeld findest du Behälter anhand von Name, Beschreibung oder Code. Mit dem
        Scan-Feld öffnet ein Scan-Treffer direkt die Detailseite des passenden Behälters.
      </p>
      <HelpTip>
        Der interne Code teilt sich den Nummernraum mit Item-Codes. Jeder Code in einer Station
        ist eindeutig — Items und Behälter dürfen sich keinen Code teilen.
      </HelpTip>
    </HelpSection>
  </HelpArticle>
</template>
