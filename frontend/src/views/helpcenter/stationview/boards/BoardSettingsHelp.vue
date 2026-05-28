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
    <HelpArticle title="Planer" subtitle="Aufgaben mit Kanban-Boards organisieren">
        <HelpSection title="Was sind Boards?">
            <p>Boards sind Kanban-Tafeln, mit denen ihr Aufgaben organisieren könnt. Jedes Board hat Spalten (z.B. „Offen", „In Arbeit", „Erledigt") und Tickets, die zwischen den Spalten verschoben werden.</p>
        </HelpSection>

        <HelpSection title="Tickets erstellen">
            <p>Klicke auf „Ticket erstellen" um ein neues Ticket anzulegen. Gib einen Titel ein und wähle die Spalte und Priorität. Optional kannst du eine Beschreibung, ein Fälligkeitsdatum und eine Person zuweisen.</p>
        </HelpSection>

        <HelpSection title="Ticket-Details">
            <p>Klicke auf ein Ticket um die Details zu öffnen. Hier kannst du die Beschreibung bearbeiten, eine Checkliste hinzufügen, Kommentare schreiben und das Ticket zwischen Spalten verschieben.</p>
        </HelpSection>

        <HelpSection title="Checkliste">
            <p>Jedes Ticket kann eine Checkliste haben. Der Fortschritt wird auf der Ticket-Karte als Balken angezeigt.</p>
        </HelpSection>

        <HelpSection title="Verknüpfungen">
            <p>Tickets können miteinander verknüpft werden: „Blockiert", „Blockiert von", „Bezieht sich auf", „Verursacht" und „Verursacht von".</p>
        </HelpSection>

        <HelpSection title="Föderation">
            <p>In den Board-Einstellungen kannst du ein Board mit Partnerwachen teilen. Wähle die Partner aus und lege für jeden den Zugriffsmodus fest:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li><strong>Nur Lesen:</strong> Die Partnerwache kann das Board sehen, aber nichts bearbeiten.</li>
                <li><strong>Vollzugriff:</strong> Die Partnerwache kann Tickets erstellen, bearbeiten und kommentieren.</li>
            </ul>
            <p class="mt-2">Optional kannst du bei Vollzugriff festlegen, welche Rollen bearbeiten dürfen.</p>
        </HelpSection>

        <HelpTip>
            Boards können über die Wachen-Einstellungen als Modul aktiviert oder deaktiviert werden. Zum Erstellen und Verwalten von Boards wird die Rolle „Board-Verwalter" benötigt.
        </HelpTip>
    </HelpArticle>
</template>
