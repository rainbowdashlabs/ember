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
  <HelpArticle title="Behälter-Prüfung" subtitle="Prüfen, was sich tatsächlich in einem Behälter befindet.">
    <HelpSection title="Wozu ist das gut?">
      <p>
        Bei einer Behälter-Prüfung wählst du einen Lagerbehälter aus und scannst alle Items, die
        tatsächlich darin liegen. Fehlt etwas oder taucht etwas auf, das laut System anderswo
        sein sollte, wird das im Ergebnis markiert.
      </p>
      <p>
        Enthält der Behälter weitere Behälter, kannst du die Prüfung wahlweise nur auf die
        direkte Ebene beschränken oder den gesamten Behälter samt Unterbehältern abdecken.
      </p>
    </HelpSection>

    <HelpTip>
      Behälter-Prüfungen werden aktuell vorbereitet und stehen mit einem nachfolgenden Update zur
      Verfügung. Die Mitglieder-Prüfung steht jetzt schon zur Verfügung.
    </HelpTip>
  </HelpArticle>
</template>
