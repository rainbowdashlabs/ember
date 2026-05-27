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
    <HelpArticle title="Ticket erstellen" subtitle="Ein neues Ticket mit allen Details anlegen">
        <HelpSection title="Schnell oder ausführlich">
            <p>Du kannst Tickets auf zwei Wegen erstellen:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li><strong>Schnell-Erstellung:</strong> Über den „Ticket erstellen"-Button auf dem Board. Hier gibst du nur die wichtigsten Infos ein.</li>
                <li><strong>Ausführlich:</strong> Klicke im Schnell-Dialog auf „Mehr Optionen", um die erweiterte Ansicht zu öffnen. Hier kannst du direkt Checklisten, Verknüpfungen, Labels und Weblinks hinzufügen.</li>
            </ul>
        </HelpSection>

        <HelpSection title="Startspalte">
            <p>Neue Tickets können nur im Backlog oder in der ersten Spalte des Boards erstellt werden. So wird sichergestellt, dass Tickets den vorgesehenen Weg durch das Board nehmen.</p>
        </HelpSection>

        <HelpSection title="Checkliste vorbereiten">
            <p>Du kannst direkt beim Erstellen eine Checkliste mit Unterpunkten anlegen. Diese wird zusammen mit dem Ticket gespeichert.</p>
        </HelpSection>

        <HelpSection title="Verknüpfungen und Weblinks">
            <p>Verknüpfe das neue Ticket gleich mit bestehenden Tickets (z.B. „blockiert von" oder „bezieht sich auf") oder füge Weblinks zu externen Ressourcen hinzu.</p>
        </HelpSection>

        <HelpTip>
            <p>Wenn du im Schnell-Dialog bereits Daten eingegeben hast, werden diese automatisch in die erweiterte Ansicht übernommen.</p>
        </HelpTip>
    </HelpArticle>
</template>
