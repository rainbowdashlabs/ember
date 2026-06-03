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
    <HelpArticle title="Boards" subtitle="Aufgaben mit Kanban-Boards organisieren">
        <HelpSection title="Was sind Boards?">
            <p>Boards sind Kanban-Tafeln, mit denen ihr Aufgaben organisieren könnt. Jedes Board hat Spalten (z.B. „Offen", „In Arbeit", „Erledigt") und Tickets, die zwischen den Spalten verschoben werden.</p>
        </HelpSection>

        <HelpSection title="Übersicht">
            <p>Auf der Übersichtsseite siehst du alle Boards, auf die du Zugriff hast. Jedes Board zeigt den Namen, das Kürzel und die Anzahl offener Tickets. Klicke auf ein Board, um es zu öffnen.</p>
        </HelpSection>

        <HelpSection title="Zugriff">
            <p>Der Zugriff auf Boards kann eingeschränkt werden. Es gibt zwei Stufen:</p>
            <ul class="list-disc ml-4 space-y-1">
                <li><strong>Lesen:</strong> Du kannst Tickets sehen, aber nicht erstellen oder bearbeiten.</li>
                <li><strong>Bearbeiten:</strong> Du kannst Tickets erstellen, bearbeiten, verschieben und kommentieren.</li>
            </ul>
            <p class="mt-2">Welche Stufe du hast, wird vom Board-Verwalter über Mitgliedstyp, Gruppen oder Tags festgelegt.</p>
        </HelpSection>

        <HelpTip>
            Boards können über die Stationseinstellungen als Modul aktiviert oder deaktiviert werden. Zum Erstellen und Verwalten von Boards wird die Berechtigung „Boards" benötigt.
        </HelpTip>
    </HelpArticle>
</template>
