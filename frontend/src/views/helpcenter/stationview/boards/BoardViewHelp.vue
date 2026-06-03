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
    <HelpArticle title="Board-Ansicht" subtitle="Tickets auf dem Kanban-Board verwalten">
        <HelpSection title="Spalten">
            <p>Jedes Board besteht aus Spalten, die den Status eines Tickets darstellen. Tickets wandern von links nach rechts durch die Spalten. Die Spalten werden vom Board-Verwalter in den Einstellungen festgelegt.</p>
        </HelpSection>

        <HelpSection title="Tickets verschieben">
            <p>Ziehe ein Ticket per Drag-and-Drop in eine andere Spalte, um den Status zu ändern. Du kannst Tickets auch innerhalb einer Spalte umsortieren.</p>
        </HelpSection>

        <HelpSection title="Ticket-Karten">
            <p>Jede Ticket-Karte zeigt die wichtigsten Informationen auf einen Blick:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li>Ticket-Nummer und Titel</li>
                <li>Priorität (farblich markiert)</li>
                <li>Zugewiesene Person</li>
                <li>Fälligkeitsdatum</li>
                <li>Checklisten-Fortschritt</li>
                <li>Labels</li>
            </ul>
            <p class="mt-2">Klicke auf eine Karte, um die Details zu öffnen.</p>
        </HelpSection>

        <HelpSection title="Filtern und Suchen">
            <p>Nutze die Suchleiste, um Tickets nach Titel oder Nummer zu finden. Du kannst die Ansicht auch nach zugewiesener Person, Priorität oder Labels filtern.</p>
        </HelpSection>

        <HelpTip>
            Über die Buttons oben erreichst du den Backlog (ungeplante Tickets) und das Archiv (abgeschlossene Tickets).
        </HelpTip>
    </HelpArticle>
</template>
