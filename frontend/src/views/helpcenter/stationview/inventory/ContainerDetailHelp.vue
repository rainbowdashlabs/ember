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
  <HelpArticle title="Behälter-Details" subtitle="Inhalt, Position und Verlauf eines Behälters.">
    <HelpSection title="Was zeigt diese Seite?">
      <p>
        Oben siehst du den Pfad vom Wurzel-Behälter bis hierher — z. B. "Lager A / Schrank 2 /
        Kiste 7". Jede Stufe ist anklickbar, sodass du die übergeordneten Behälter direkt
        aufrufen kannst.
      </p>
      <p>
        Im Bereich "Inhalt" werden die Behälter, die direkt in diesem liegen, und die Items, die
        diesem Behälter zugeordnet sind, aufgelistet. Mit dem Schalter "Verschachtelt anzeigen"
        bekommst du zusätzlich alle Items aus untergeordneten Behältern eingeblendet.
      </p>
    </HelpSection>

    <HelpSection title="Bearbeiten und Löschen">
      <p>
        Über "Bearbeiten" lassen sich Name, Typ, übergeordneter Behälter, interner Code und
        Beschreibung anpassen. Beim Löschen werden untergeordnete Behälter zu Wurzelelementen
        und enthaltene Items verlieren nur ihre Lagerzuordnung — die Items selbst bleiben
        erhalten.
      </p>
    </HelpSection>

    <HelpSection title="Verlauf">
      <p>
        Jede Änderung (Erstellung, Umbenennung, Umzug, Löschung) wird im Verlauf protokolliert,
        damit nachvollziehbar bleibt, wer wann was angepasst hat.
      </p>
    </HelpSection>
  </HelpArticle>
</template>
