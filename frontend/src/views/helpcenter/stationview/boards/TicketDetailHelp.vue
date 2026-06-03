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
    <HelpArticle title="Ticket-Details" subtitle="Alle Informationen zu einem Ticket einsehen und bearbeiten">
        <HelpSection title="Beschreibung">
            <p>Jedes Ticket hat eine Beschreibung, die mit Markdown formatiert werden kann. Klicke auf die Beschreibung, um sie zu bearbeiten.</p>
        </HelpSection>

        <HelpSection title="Zuweisen und Planen">
            <p>Weise das Ticket einer Person zu und setze ein Fälligkeitsdatum. Du kannst auch die Priorität und die Spalte ändern.</p>
        </HelpSection>

        <HelpSection title="Checkliste">
            <p>Füge eine Checkliste hinzu, um Teilaufgaben zu verfolgen. Der Fortschritt wird als Balken auf der Ticket-Karte angezeigt. Punkte können abgehakt, umbenannt und gelöscht werden.</p>
        </HelpSection>

        <HelpSection title="Verknüpfungen">
            <p>Verbinde Tickets miteinander, um Abhängigkeiten zu zeigen:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li><strong>Blockiert / Blockiert von:</strong> Zeigt, dass ein Ticket ein anderes aufhält.</li>
                <li><strong>Bezieht sich auf:</strong> Zeigt einen Zusammenhang zwischen Tickets.</li>
                <li><strong>Verursacht / Verursacht von:</strong> Zeigt eine Ursache-Wirkung-Beziehung.</li>
            </ul>
        </HelpSection>

        <HelpSection title="Labels">
            <p>Labels helfen, Tickets zu kategorisieren. Du kannst einem Ticket mehrere Labels zuweisen. Labels werden vom Board-Verwalter in den Einstellungen erstellt.</p>
        </HelpSection>

        <HelpSection title="Weblinks und Anhänge">
            <p>Füge Weblinks zu externen Seiten oder Dateien als Anhänge hinzu. Anhänge können heruntergeladen und gelöscht werden.</p>
        </HelpSection>

        <HelpSection title="Wissensdatenbank">
            <p>Verknüpfe Tickets mit Artikeln aus der Wissensdatenbank, um relevante Informationen direkt zugänglich zu machen.</p>
        </HelpSection>

        <HelpSection title="Kommentare">
            <p>Schreibe Kommentare, um Informationen zu teilen oder Fragen zu stellen. Kommentare unterstützen Markdown-Formatierung und können bearbeitet oder gelöscht werden.</p>
        </HelpSection>

        <HelpSection title="Aktivität und Verlauf">
            <p>Der Aktivitäts-Tab zeigt alle Änderungen am Ticket: Spalten-Wechsel, Zuweisungen, Bearbeitungen und Kommentare. So kannst du den gesamten Verlauf nachvollziehen.</p>
        </HelpSection>

        <HelpSection title="Beobachten">
            <p>Klicke auf „Beobachten", um über Änderungen am Ticket benachrichtigt zu werden. Du erhältst dann Benachrichtigungen bei neuen Kommentaren, Statusänderungen und anderen Aktualisierungen.</p>
        </HelpSection>

        <HelpTip>
            Du kannst ein Ticket archivieren, indem du es in die letzte Spalte verschiebst. Nach der eingestellten Anzahl Tage wird es automatisch ins Archiv verschoben.
        </HelpTip>
    </HelpArticle>
</template>
