# Änderungsprotokoll

## v26.20.0

### Neue Funktionen

- **Ablaufdaten mit Erinnerungen.** Ein Profilfeld einer Wache oder eines Verbands kann ein Datum halten, das abläuft, etwa einen Erste-Hilfe-Kurs oder einen Führerschein, und die Schnellvorlagen dafür nutzen es jetzt. Es steht gelb, wenn es bald abläuft, und rot, wenn es abgelaufen ist, auf der Seite des Mitglieds und bei Feldern der Wache auch in der Mitgliederliste, und das Mitglied und die Mitgliederverwaltung der Wache oder des Verbands werden rechtzeitig erinnert.
- **Formulare mit Seiten und Verzweigungen.** Ein Formular lässt sich in Seiten aufteilen, die zur nächsten Seite, zu einer gewählten Seite weiter unten oder direkt zum Absenden führen. Eine Frage mit einer Antwort kann entscheiden, welche Seite als Nächstes kommt, und die Auswertung zeigt, wie viele eine Frage überhaupt gesehen haben.
- **Ein Formular später fertig ausfüllen.** Ein begonnenes, nicht abgesendetes Formular bleibt erhalten und öffnet sich wieder auf der Seite, auf der es verlassen wurde, bei Formularen der Wache auf jedem Gerät, bei öffentlichen Formularen auf demselben Gerät. Sehen kann es nur, wer es ausfüllt, und wer diese Person betreut, und es endet, wenn das Formular schließt.
- **Einzelne Termine absagen und wiederherstellen.** Verantwortliche können gezielt den Termin absagen, den sie gerade ansehen, während das Absagen einer ganzen Serie eine eigene, endgültige Aktion bleibt. Ein abgesagter Termin lässt sich wiederherstellen, solange er noch bevorsteht, und alle, die dafür angemeldet sind, erfahren davon.
- **Hintergrundaufgaben auf einen Blick.** Eine neue Seite unter Monitoring listet jede Arbeit, die der Server von selbst erledigt, etwa Mailversand, Erinnerungen und Aufräumen, mit Rhythmus, letztem Lauf, dessen Dauer und der letzten Fehlermeldung. Die Angaben gelten seit dem letzten Neustart.
- **Gruppensets.** Gruppen lassen sich zu einem Set zusammenfassen, etwa die Stufen einer Ausbildung, und ein Mitglied kann nur in einer Gruppe eines Sets sein. Ein Set wird auf der Gruppenseite angelegt, und wer eine andere Gruppe des Sets wählt, verschiebt das Mitglied.
### Verbesserungen

- **Zahlenspalten listen ihre Werte zum Filtern.** Neben dem Bereich listet der Filter einer Zahlenspalte wie dem Alter jetzt die Werte ihrer Zeilen zum einzelnen Ankreuzen.
- **Vorschau, Duplizieren und eine Nachricht nach dem Absenden.** Der Formular-Editor zeigt das Formular so, wie es ausgefüllt wird, samt Weg durch die Seiten, und Formulare und Fragen lassen sich duplizieren. Ein Formular kann nach dem Absenden eine eigene Nachricht und einen Link zeigen.
- **Fragen und Seiten per Ziehen sortieren.** Fragen werden innerhalb einer Seite und zwischen Seiten gezogen, Seiten als Ganzes. Geänderte Fragen gehen nicht mehr verloren, wenn der Editor vor dem Speichern verlassen wird: Ember fragt vorher nach und bietet sie beim nächsten Öffnen wieder an.
- **Die Ausleihe erreicht Partnerwachen auf anderen Instanzen.** Ausrüstung lässt sich zwischen Partnerwachen auf verschiedenen Instanzen genauso ansehen, anfragen, verleihen und zurückgeben wie zwischen Wachen einer Instanz. Beide Instanzen brauchen diese Version, und eine Partnerwache erscheint in den Angeboten, sobald sie aktualisiert hat.
- **Fehler zeigen auf die Frage.** Lassen sich Antworten nicht absenden, öffnet das Formular die Seite mit dem Problem und markiert jede betroffene Frage.
- **Abgesagte Termine sind überall gekennzeichnet.** Die Liste der kommenden Termine, der Monatskalender und der persönliche Kalender-Feed zeigen einen abgesagten Termin durchgestrichen und als abgesagt statt als stattfindend. Eine Anmeldung zu einem solchen Termin wird nicht mehr angeboten.
- **SFTP- und SMB-Speicher bedienen mehrere Anfragen gleichzeitig.** Seiten mit vielen Bildern warten nicht mehr auf eine Datei nach der anderen, und Wachen auf demselben Verbund-Speicher teilen sich ihre Verbindungen; im Test dauerten 32 Lesezugriffe neben einem großen Upload eine halbe statt vier Sekunden. Grenzen und Wartezeiten beschreibt die Speicher-Seite der Hilfe.
- **Eine klare Meldung, wenn der Speicher nicht erreichbar ist.** Antwortet der Speicher einer Wache nicht, erfahren die Nutzer mit `503 Service Unavailable`, dass er gerade nicht erreichbar ist, statt einen allgemeinen Fehler zu sehen.
- **Bilder brauchen weniger Platz und laden schneller.** Die kleineren Größen von Profilbildern, Logos, Wiki-Bildern sowie Bildern in Quiz und Fundsachen werden als WebP gespeichert, und keine Größe wird mehr abgelegt, die so groß ist wie das Bild selbst. Bereits hochgeladene Bilder bleiben, wie sie sind.
- **Das Ankündigen eines Termins ergibt einen fertigen Beitrag.** „Als Neuigkeit ankündigen" öffnet den Beitrag jetzt mit einem Terminblock für den gewählten Tag, der Beschreibung des Termins und einem kurzen Text zu dem, was der Block nicht zeigt, etwa die Anmeldefrist, die Zahl der Plätze und ob Anmeldungen bestätigt werden. Jeder Teil lässt sich vor dem Speichern noch ändern.
- **Terminblöcke in Neuigkeiten.** Eine Neuigkeit oder ein Wiki-Artikel kann einen Termin als Terminblock zeigen, auch interne Termine, sofern alle Mitglieder sie sehen dürfen, und bei einem wiederkehrenden Termin den Tag wählen, um den es geht. Termine nur für einen Teil der Wache lassen sich nicht wählen, und Leser außerhalb der Wache, etwa Partnerwachen oder der öffentliche Blog, sehen bei einem internen Termin stattdessen einen kurzen Hinweis.
- **Anwesenheitsvorlagen tragen ganze Mitgliedstypen ein.** Eine Vorlage kann neben Gruppen auch Mitgliedstypen nennen, und alle Mitglieder eines gewählten Typs stehen neben den Mitgliedern ihrer Gruppen auf ihren Listen. Wer eine Liste aus einer Vorlage startet, findet deren Typen und Gruppen schon angekreuzt und kann sie für diese eine Liste übernehmen oder ändern.
- **Die Discovery-Seite zeigt Wachen anderer Instanzen.** Die öffentliche Discovery-Seite unter `/discovery` zeigt auch die öffentlichen Wachen der anderen Ember-Instanzen, die diese Instanz kennt, jeweils mit der Instanz, zu der sie gehören, und einem Link zu ihrer öffentlichen Seite dort. Ein Suchfeld findet Wachen aller Instanzen nach Name, Ort, Verband oder Instanz.
- **Die Einrichtung erklärt die Listung im Verzeichnis.** Der Schritt zur Sichtbarkeit bei der Einrichtung einer neuen Wache erklärt jede Wahl, auch die Listung nur auf dieser Instanz, und zeigt genau, welche Angaben eine öffentliche Listung an die Discovery-Seite und an andere Instanzen weitergibt. Mit unveränderter Voreinstellung gespeichert gilt er als erledigt.
- **Gruppen für bestimmte Mitgliedstypen.** Eine Gruppe lässt sich auf einige Mitgliedstypen beschränken, etwa Team und Manager, und nimmt dann niemanden sonst auf. Ändert sich der Mitgliedstyp eines Mitglieds, verlässt es die Gruppen, die nicht mehr passen, nachdem seine Bearbeitungsseite sie aufgelistet hat.
- **Gruppen und Tags oben in den Berechtigungen eines Mitglieds.** Auf der Bearbeitungsseite eines Mitglieds stehen Gruppen und Tags als kompakte Chips über der Liste der Berechtigungen, und die Gruppen eines Sets sind eine einzige Auswahl. Gruppen für andere Mitgliedstypen erscheinen ausgegraut, mit den Typen, die sie aufnehmen.
- **Bearbeitete Kommentare zu Neuigkeiten sind gekennzeichnet.** Ein Kommentar zu einer Neuigkeit, den seine Verfasserin oder sein Verfasser geändert hat, ist als bearbeitet markiert, wie Kommentare zu Terminen, Wiki-Dateien und Board-Tickets.
- **Hinweise auf Kommentare lesen sich überall gleich.** Der Auszug, den ein Hinweis aus einem langen Kommentar zu einer Neuigkeit oder einer Wiki-Datei zitiert, endet jetzt mit „…“, wie schon bei Terminen und Board-Tickets. Ein Hinweis, dass du in einem Kommentar an einem Ticket erwähnt wurdest, nennt die Person, die ihn geschrieben hat, wo er bisher das Ticket nannte.
- **Exporte geben Antworten einheitlich aus.** Die Mitgliederliste, die Anmeldetabelle, die Anwesenheitsliste, die Terminliste und die Inventarlisten der Mitglieder zeigen Ja und Nein als Wörter in der Sprache der Wache, Daten als Tage und genannte Mitglieder mit Namen. Manche gaben bisher den gespeicherten Wert aus, etwa „true“ oder die Nummer eines Mitglieds.
- **Ein Name für jeden Feldtyp.** Jede Seite, auf der eine Wache eigene Felder anlegt, von Profilfragen bis zu Feldern an Boards, Ausrüstung, Terminen, Anwesenheitslisten und Wartelisten, bietet die Typen unter denselben Namen in derselben Liste an. Antworten sehen auch überall gleich aus: Ja und Nein mit Zeichen und Wort, Tage und Uhrzeiten so, wie man sie schreibt, und Mitglieder mit ihrem Namen, jetzt auch auf Anwesenheitslisten, an Board-Tickets und auf der öffentlichen Statusseite der Warteliste.
- **Board-Felder können Pflichtfelder sein.** Ein Board-Feld lässt sich als Pflichtfeld markieren, sein Wert kann dann geändert, aber nicht geleert werden. Die Antworten eines Auswahlfelds werden wie überall eine pro Zeile eingetragen.
- **Vorlagen bringen Anmeldefragen mit.** Im Editor für Terminvorlagen lassen sich die Fragen zur Anmeldung anlegen, im selben Editor wie an einem Termin.
- **Zahlenfelder der Ausrüstung nehmen Schritte unter eins.** Der Schritt eines Zahlenfelds der Ausrüstung lässt sich im Feldeditor auf einen Bruchteil wie 0,5 setzen, womit das Feld Kommazahlen annimmt.
- **Die Konfigurationshilfe nennt jede Einstellung.** Die Liste der Einstellungen und Umgebungsvariablen in der Hilfe wird aus dem Server selbst gelesen und zeigt jetzt auch die Grenzen für das Anmelden von Geräten, die älteren Ausweich-Mailanbieter und die verschlüsselten Speicher-Zugangsdaten, jeweils mit Schlüssel, Variable und Standardwert.
- **Benachrichtigungen des Verbands in der App.** Verband → Benachrichtigungen zeigt jetzt, was dir der Verband gemeldet hat, öffnet jeden Hinweis auf seiner Seite und markiert ihn als gelesen, und die Glocke im Menü des Verbands zeigt, wie viele ungelesen sind. Hinweise, die zurückgehalten werden, solange derselbe noch ungelesen ist, etwa Erinnerungen, kommen wieder, sobald er hier gelesen ist.
- **Ablehnungen nennen den Wert, um den es geht.** Wird etwas abgelehnt, zeigt die Meldung den Wert, um den es ging, in der Sprache der Lesenden, etwa wie viel vom Speicherplatz des Verbands noch frei ist, wie viele Inhalte noch auf eine Gruppe beschränkt sind oder zu welcher Frage eine Antwort nicht passte.
- **Abläufe können ohne Bestätigung des Mitglieds auskommen.** Jeder Ablauf auf der Seite der Abläufe hat einen Schalter, der den Erhalt eines Teils für das Mitglied bestätigt, sobald die Bewegung dort ankommt, statt auf das Mitglied zu warten. Der Verlauf der Bewegung zeigt diesen Schritt als automatisch bestätigt, andere Abläufe fragen weiter nach.

### Sicherheit

- **Einträge nur für benannte Personen waren für die ganze Wache sichtbar.** Ein Formular, ein Termin, ein Blog-Beitrag oder ein Quiz, das auf eine Liste benannter Mitglieder beschränkt war, wurde jedem Mitglied der Wache angezeigt. Jetzt sehen es nur die benannten Personen und diejenigen, die es verwalten.
- **Verborgene Termine ließen sich über ihre Adresse lesen.** Ein Mitglied konnte Angaben, Felder, Anmeldungen, Kommentare und Material eines Termins, der vor ihm verborgen war, direkt über seine Nummer abrufen, und die Anmeldezahlen enthielten auch verborgene Termine. Solche Anfragen werden jetzt beantwortet, als gäbe es den Termin nicht, außer das Mitglied darf ihn sehen oder Termine bearbeiten.
- **Partnerwachen auf derselben Instanz konnten öffnen, was nicht mit ihnen geteilt war.** Eine Partnerwache auf derselben Instanz konnte Quizze, Prüfprotokolle und Wiki-Artikel öffnen und Kommentare zu Neuigkeiten, Terminen und Wiki-Artikeln lesen und schreiben, die nie mit ihr geteilt wurden. Sie sieht jetzt genau das, was eine Partnerwache auf einer anderen Instanz sieht.
- **Partnerwachen konnten mehr ändern als das mit ihnen geteilte Board.** Eine Partnerwache, die ein geteiltes Board bearbeiten durfte, konnte Checklisten ändern, Tickets verschieben und Labels anderer Boards auf derselben Instanz vergeben. Ihre Änderungen bleiben jetzt auf dem Board, das mit ihr geteilt ist.
- **Benachrichtigungen über beschränkte Einträge erreichten Personen ohne Zugriff.** In manchen Fällen ging die Benachrichtigung über ein neues Formular, einen neuen Termin oder einen neuen Blog-Beitrag an Mitglieder außerhalb des Kreises, für den er bestimmt war, und zeigte seinen Titel. Jetzt erreicht sie nur die Mitglieder, die den Eintrag öffnen dürfen.
- **Die Aufnahme in eine Gruppe verlangt die Rechte, die sie vergibt.** Wer Gruppen verwalten durfte, konnte jeden, auch sich selbst, in eine Gruppe aufnehmen, deren Berechtigungen er selbst nicht hatte. Die Aufnahme in eine Gruppe verlangt jetzt jede Berechtigung, die die Gruppe vergibt, und eine frische Bestätigung, wo sie welche vergibt.
- **Gruppen nahmen Mitglieder anderer Wachen auf.** Die Mitgliederliste einer Gruppe nahm Personen anderer Wachen an, wenn sie direkt an den Server geschickt wurden. Solche Mitglieder werden jetzt abgelehnt.
- **Registrierungscodes anderer Wachen waren erreichbar.** Eine Instanzadministration, die in einer Wache arbeitete, konnte Registrierungscodes einer anderen Wache über ihre Nummer öffnen, ihre Gruppen ändern und sie löschen. Ein Code ist jetzt nur noch aus seiner eigenen Wache erreichbar.
- **Unveröffentlichte Instanz-Neuigkeiten erreichten die Neuigkeiten-Verwaltung der Wachen.** Wer in einer Wache Neuigkeiten verwaltet, sah Entwürfe der Instanz in der Liste der Neuigkeiten, bevor sie veröffentlicht waren. Entwürfe der Instanz bleiben jetzt bei der Instanzadministration, bis sie veröffentlicht sind.
- **Eine Wachen-Bewerbung ließ sich ohne die Bestätigungsmail bestätigen.** Die Bewerbung um eine neue Wache antwortete mit dem Code, der die Adresse der Bewerbung bestätigt, sodass sich die Adresse bestätigen ließ, ohne die Mail je erhalten zu haben. Der Code erreicht die Bewerbung jetzt nur noch über diese Mail.
- **Die Neuigkeiten-Verwaltung konnte Kommentare anderer Wachen entfernen.** Wer Neuigkeiten verwaltet, konnte einen Kommentar zu einer Neuigkeit einer anderen Wache über seine Nummer entfernen. Ein Kommentar lässt sich jetzt nur noch aus seiner eigenen Wache entfernen; bei Neuigkeiten, die die Instanz an alle Wachen richtet, ist das die Wache, aus der er geschrieben wurde.
- **Dateien einer Wache konnte jeder lesen, der auf der Instanz angemeldet war.** Wer angemeldet, aber kein Mitglied einer Wache war, konnte ihre Mediendateien, Bilder und Dokumente öffnen, ihre Mediendateien auflisten und ihren Verbund sehen, indem er die Wache angab. Das ist jetzt nur noch Mitgliedern der Wache möglich.
- **Der Code, mit dem eine Authenticator-App eingerichtet wurde, ließ sich noch einmal verwenden.** Der Code, der eine neue Authenticator-App bestätigte, wurde innerhalb seiner kurzen Gültigkeit noch von der nächsten Anmeldung oder Bestätigung angenommen. Jetzt gilt er als verbraucht, sobald die App eingerichtet ist.
- **Fragen aus der Terminvorlage einer anderen Wache ließen sich übernehmen.** Wer Termine anlegen durfte, konnte die Fragen zur Anmeldung aus der Terminvorlage einer anderen Wache in einen neuen Termin übernehmen, indem er ihre Nummer angab. Jetzt werden nur die eigenen Vorlagen der Wache verwendet, und mit einer fremden wird kein Termin angelegt.
- **Schritte anderer Abläufe ließen sich ändern.** Wer an einem Ablauf arbeiten durfte, konnte einen Schritt jedes anderen Ablaufs, auch eines anderer Wachen, abhaken, bearbeiten, löschen oder mit einer Notiz versehen, indem er ihn unter dem eigenen Ablauf angab, und dasselbe galt für die Schritte von Ablaufvorlagen. Ein Schritt ist jetzt nur noch über den Ablauf oder die Vorlage erreichbar, zu der er gehört.
- **Jedes Mitglied konnte Notizen an Schritte von Abläufen schreiben.** Ein Mitglied der Wache konnte die Notiz an einem Schritt jedes Ablaufs schreiben oder ersetzen, auch eines Ablaufs, der ihm nicht übertragen war, indem es sie direkt an den Server schickte. Notizen schreiben jetzt nur noch diejenigen, die Abläufe führen, so wie es die Seite des Ablaufs schon zeigte.
- **Jedes Mitglied konnte die Profile anderer Mitglieder lesen und ändern.** Ein Mitglied der Wache konnte die Profilangaben jedes anderen Mitglieds dort lesen und überschreiben, indem es die Anfrage direkt an den Server schickte. Profilangaben lesen und ändern jetzt nur noch das Mitglied selbst, seine Erziehungsberechtigten und diejenigen, die Mitglieder ansehen oder bearbeiten dürfen.
- **Fragen des Verbands ließen sich an ihren Sperren vorbei beantworten.** Eine Wache konnte Antworten auf Profilfragen ihres Verbands schreiben, die für das Mitglied gesperrt, der Wache vorenthalten oder dem Mitglied gar nicht gestellt waren, und die Mitgliederverwaltung eines Verbands konnte Antworten auf Fragen jeder Wache schreiben. Jede Antwort durchläuft jetzt dieselben Sperren wie eine Antwort auf die eigenen Fragen der Wache.
- **Das Entfernen einer Gruppe konnte beschränkte Inhalte für alle öffnen.** Ein Termin, eine Terminvorlage, eine Neuigkeit, ein Formular, ein Quiz oder ein Wiki-Eintrag, der nur auf eine Gruppe beschränkt war, wurde für die ganze Wache sichtbar, sobald diese Gruppe entfernt oder in einen Tag umgewandelt wurde. Eine Gruppe lässt sich jetzt erst entfernen oder in einen Tag umwandeln, wenn nichts mehr auf sie beschränkt ist.

### Änderungen

- **Einstellungen einer Frage stehen in einem Menü.** Alles außer „Pflichtfeld" steckt im Menü in der Ecke jeder Frage, und geänderte Einstellungen stehen als Etiketten unter ihrem Titel. Neue Fragen kommen über einen Knopf am Ende jeder Seite dazu.
- **Nach dem Absenden erscheint eine Bestätigung.** Nach dem Absenden eines Formulars der Wache bleibst du auf einer Seite, die das bestätigt, mit dem Weg zurück zu den Umfragen und, wo das Formular es erlaubt, der Möglichkeit, die Antwort zu ändern.
- **Die Vorlage Jugendflamme bietet „Keine" an.** Die Schnellvorlage Jugendflamme legt ein Auswahlfeld mit „Keine" und den drei Stufen an, sodass jedes Mitglied genau eine Stufe hat. Das Datum, an dem die Stufe erreicht wurde, kommt wie bisher mit.
- **Die Frist für die Mindestanzahl zählt Tage vor jedem Termin.** Ein Termin mit einer Mindestanzahl an Anmeldungen legt jetzt fest, wie viele Tage vor jedem Termin sie erreicht sein muss, statt einen festen Tag zu nennen, und einmalige Termine behalten ihre Frist. Bei wiederkehrenden Terminen entfällt die alte Frist und muss im Editor des Termins neu gesetzt werden.
- **Das Backend bekommt dreißig Sekunden zum Herunterfahren.** Die mitgelieferten Compose-Dateien und das Installationsskript geben dem Backend-Container eine `stop_grace_period` von 30 Sekunden, in denen er laufende Anfragen beendet und gepufferte Statistiken und Protokollzeilen speichert. Eigene Compose-Dateien sollten beim Backend dasselbe setzen.
- **Die E-Mail des Verbands schaltet jede Person selbst ein.** Die Benachrichtigungs-E-Mail des Verbands erreicht nur noch, wer sie unter Verband → Benachrichtigungen einschaltet, wo sie anfangs aus ist. Bis dahin bleiben seine Benachrichtigungen in der App.
- **Neue Wachen sind öffentlich gelistet.** Eine ab jetzt gegründete Wache erscheint von Anfang an auf der öffentlichen Discovery-Seite und bei anderen Ember-Instanzen und kann das bei ihrer Einrichtung oder unter Föderation → Einstellungen abschalten. Bestehende, importierte und übertragene Wachen behalten ihre Einstellung.
- **Eine frische Installation fragt nach ihrer ersten Wache.** Eine neue Instanz legt keine Wache namens „default" mehr an: Nach der ersten Anmeldung benennt und gründet der Administrator die erste Wache, wird ihr Verwalter und landet direkt in ihrer Einrichtung. Bestehende Instanzen behalten ihre Wachen unverändert.
- **Kommentare in einem Abschnitt der Datenauskunft.** Wer seine Daten anfordert, findet alle eigenen Kommentare in einem Abschnitt, ob zu Terminen, Neuigkeiten, Wiki-Dateien oder Board-Tickets, und jeder nennt, wozu er geschrieben wurde. Benachrichtigungen, die auf einen Kommentar zeigen, öffnen ihn weiterhin.
- **Board-Kommentare gehören dem, der sie geschrieben hat.** Einen Kommentar zu einem Ticket kann nur sein Verfasser ändern und nur sein Verfasser oder ein Board-Verwalter entfernen, wo bisher jeder beides konnte, der das Ticket bearbeiten durfte. Ein Kommentar lässt sich nicht mehr leer speichern.
- **Hinweise auf Kommentare an beobachteten Tickets.** Der Hinweis auf einen neuen Kommentar an einem Ticket, das du beobachtest, nennt, wer ihn geschrieben hat, in deiner Sprache, und folgt der Einstellung für Kommentare statt der für Ticket-Updates. Über den eigenen Kommentar wird niemand mehr benachrichtigt.
- **Zahlenfelder nehmen ganze Zahlen.** Zahlenfelder von Anwesenheitslisten, Wartelisten, Ausrüstung und Board-Tickets nehmen ganze Zahlen, wie ihre Eingabefelder sie schon anbieten. Ein Ausrüstungsfeld mit einer Schrittweite unter eins nimmt weiter Kommazahlen, und bereits gespeicherte Zahlen bleiben, wie sie sind.
- **Mitgliederfelder nehmen nur Mitglieder, auf die sie beschränkt sind.** Ein Mitgliederfeld einer Anwesenheitsliste oder eines Termins, das auf eine Gruppe, einen Mitgliedstyp oder ein Tag beschränkt ist, lehnt jetzt jeden außerhalb davon ab, wenn die Liste, der Termin oder eine Vorlage gespeichert und wenn eine Frage zur Anmeldung beantwortet wird, wo bisher nur geprüft wurde, wer sich selbst eintrug. Wen es schon nennt, bleibt beim erneuten Speichern stehen.
### Fehlerbehebungen

- **Späte Absagen erreichten eine offene Anwesenheitsliste nicht.** Wer nach dem Öffnen der Anwesenheitsliste für einen Tag absagte oder seinen Platz zurückgab, stand darauf weiter als offen, bis jemand die Liste mit dem Termin abglich. Eine solche Antwort trägt die Person jetzt sofort als abgesagt in die offene Liste ein, während von Hand Markierte und geschlossene Listen bleiben, wie sie sind.
- **Denselben Speicher erneut anzuwenden löschte seine Dateien.** Wer den Speicher, den eine Wache oder die Instanz schon nutzte, erneut anwendete, etwa um nur ein Passwort zu ändern, oder für eine Wache den Speicher des Verbands wählte, auf dem sie schon stand, löschte die dort abgelegten Dateien. Ember erkennt denselben Speicher jetzt, verschiebt und löscht nichts und speichert nur die neuen Einstellungen.
- **Die Mitgliederliste zeigte Spalten für Überschriften.** Überschriften und Abstände des Profilformulars erschienen als leere Spalten in der Mitgliederliste und ihrem Export. Sie werden jetzt weggelassen.
- **Eigene Kommentare auf dem Board eines Partners ließen sich nicht ändern.** Den eigenen Kommentar auf einem Board zu ändern oder zu entfernen, das eine Partnerwache mit dir teilt, bewirkte nichts. Das funktioniert jetzt, und zwischen zwei Instanzen geteilte Boards ruhen, bis beide diese Version haben.
- **Löschen-Knöpfe an Kommentaren folgten den falschen Rechten.** Wer Neuigkeiten oder das Wiki verwaltet, bekam den Knopf zum Entfernen fremder Kommentare nicht angezeigt, während Terminverwalter ihn auf Boards und bei Terminen von Partnerwachen sahen, wo das Entfernen dann scheiterte. Der Knopf erscheint jetzt für die Person, die den Kommentar geschrieben hat, und für alle, die diese Art von Inhalt verwalten.
- **Adressen in Gruppen- und Tag-Listen standen versetzt.** In den Mitgliederlisten von Gruppen und Tags stand die Adresse von jemandem mit Profilbild neben dem Bild statt unter dem Namen. Sie steht jetzt bei allen unter dem Namen.
- **Das Umsortieren von Optionen verändert keine Antworten mehr.** Wurden die Optionen einer Frage, die schon Antworten hatte, umsortiert oder umbenannt, konnte sich ändern, was diese Antworten aussagten. Antworten bleiben jetzt bei der gewählten Option, und das Entfernen einer Option, die jemand gewählt hat, fragt vorher nach.
- **Freiwillige Fragen konnten das Absenden verhindern.** In manchen Fällen wurde ein Formular abgelehnt, wenn eine freiwillige Auswahl- oder Bewertungsfrage leer blieb oder eine Auswahl nur mit einer eigenen Antwort beantwortet war. Solche Antworten werden jetzt angenommen.
- **Das Mischen von Fragen und Optionen hatte keine Wirkung.** Die Einstellungen zum Mischen der Fragen eines Formulars oder der Optionen einer Frage wurden gespeichert, beim Ausfüllen aber nicht angewendet. Jetzt wird gemischt, Fragen jeweils innerhalb ihrer Seite.
- **Exportierte Antworten waren schwer zu lesen.** Die Tabelle und das PDF der Antworten eines Formulars zeigten jede Antwort so, wie sie gespeichert ist. Jetzt steht dort der Text der gewählten Optionen.
- **Fragen an eine Gruppe konnten im Profil fehlen.** In manchen Fällen fehlte eine Frage, die eine Wache nur einer Gruppe stellt, im Profil eines Mitglieds, wenn die Wache zu einem Verband mit eigenen Fragen gehört. Solche Fragen erscheinen jetzt immer und lassen sich beantworten.
- **Abgemeldete Mitglieder ließen sich nicht wieder eintragen.** Nachdem sich jemand abgemeldet oder abgesagt hatte, bot die Liste zum Eintragen von Mitgliedern in einen Termin ihn nicht mehr an, sodass nicht einmal die Verantwortlichen ihn wieder eintragen konnten. Er wird jetzt wieder angeboten.
- **Abgemeldete Mitglieder konnten sich nicht überall wieder anmelden.** In manchen Fällen wurde ein Mitglied, das seinen Platz zurückgegeben hatte, in der Liste der kommenden Termine und auf der Seite des Termins weiter als abgemeldet angezeigt, ohne sich dort wieder anmelden zu können. Jetzt geht das an beiden Stellen.
- **Anmeldungen eines wiederkehrenden Termins vermischten die Tage.** Bei einem wiederkehrenden Termin konnte der Reiter Anmeldungen die Antwort eines Mitglieds, die Listen der offenen und bestätigten Anmeldungen und ihre Zahlen von einem anderen als dem geöffneten Tag zeigen, und das Abmelden konnte den Platz an diesem anderen Tag aufgeben. Der Reiter zeigt und betrifft jetzt den geöffneten Tag.
- **Terminlinks auf Seiten führten ins Leere.** Der Knopf eines Blocks für einen hervorgehobenen Termin, kommende Termine oder einen Rückblick auf einer Seite der Wache führte auf eine Seite, die es nicht gibt. Jetzt öffnet er den öffentlichen Kalender der Wache.
- **Terminblöcke auf Seiten vergaßen ihren Termin.** Beim Speichern einer Seite der Wache konnte der gewählte Termin eines Blocks für einen hervorgehobenen Termin, kommende Termine oder einen Rückblick verloren gehen, der danach als nicht mehr verfügbar erschien. Die Blöcke behalten jetzt, was gewählt wurde.
- **Die Liste der Profilfelder war auf mittleren Bildschirmen zu eng.** Auf Bildschirmen zwischen Telefon und breitem Desktop drückte die Liste der Profilfelder in den Mitgliedereinstellungen die Namen zusammen, bis sich die Zeilen überlagerten. Jetzt wechselt sie zu Kacheln, sobald die Tabelle nicht mehr hineinpasst.
- **Die Anmeldung wurde angeboten, wo sie nicht erlaubt war.** Einem Mitglied konnte die Anmeldung zu einem Termin angeboten werden, der nur einem Teil der Wache offensteht, und erst nach dem Drücken erfuhr es, dass er ihm nicht offensteht. Die Anmeldung wird jetzt nur denen angeboten, die sich anmelden dürfen, alle anderen sehen einen kurzen Hinweis, warum.
- **Jeder wiederkehrende Termin hieß wöchentlich.** Die Seite eines monatlichen, vierteljährlichen oder jährlichen Termins bezeichnete ihn als wöchentlich. Sie nennt jetzt, wie oft er sich wiederholt, so wie die Liste der Termine.
- **Das Absagen eines Termins sagte die ganze Serie ab.** Wurde ein wiederkehrender Termin abgesagt, von Hand oder automatisch wegen zu weniger Anmeldungen, fielen alle Termine aus, und alle, die für irgendeinen davon angemeldet waren, wurden benachrichtigt. Jetzt fällt nur der betroffene Termin aus, und die automatische Prüfung zählt allein die Anmeldungen dieses Termins.
- **Der öffentliche Kalender zeigte abgesagte Termine als stattfindend.** Die öffentliche Seite der Wache und ihr öffentlicher Kalender-Feed führten abgesagte Termine auf, als hätte sich nichts geändert. Jetzt sind sie als abgesagt gekennzeichnet.
- **Statistiken brachen nach jedem Neustart ein.** Bei jedem Neustart gingen die Seitenaufrufe bis zur letzten Stunde, die jüngsten Zahlen zum Datenverkehr, Antwortzeiten und Protokollzeilen verloren. Sie werden jetzt gespeichert, bevor der Server anhält, sofern ihm die Zeit zum Herunterfahren gelassen wird.
- **WebP-Bilder ließen sich nicht überall hochladen.** Ein WebP-Bild als Logo einer Wache oder als Ordnersymbol oder Bild im Wiki hochzuladen schlug fehl und entfernte in manchen Fällen das Bild, das vorher da war. WebP-Bilder werden jetzt wie alle anderen angenommen.
- **Der Speicher der Instanz konnte nach dem Rückzug einer Wache ausfallen.** Zog eine Wache mit eigenem Speicher zurück auf den Speicher der Instanz und lagen die Dateien der Instanz auf SFTP, SMB oder S3, schlug danach jede Datei der Instanz fehl, bis Ember neu gestartet wurde. Der Speicher der Instanz bleibt jetzt offen.
- **Eine Anwesenheitsliste vergaß, für wen sie gestartet wurde.** Eine Liste, die für ausgewählte Mitgliedstypen und Gruppen gestartet wurde, zeigte trotzdem die Gruppen der Vorlage, und der Abgleich mit dem Termin trug deren Mitglieder nach. Eine Liste behält jetzt die Personen, für die sie gestartet wurde, auf dem Bildschirm, beim erneuten Abgleich und im PDF.
- **Der Anwesenheitsbericht nannte Mitgliedstypen so, wie die Datenbank sie nennt.** Die Überschrift des Berichts und seiner Vorschau zeigte einen Mitgliedstyp als „TEAM" oder „GUARDIAN". Sie verwendet jetzt dieselben Namen wie der Rest der Seite.
- **SMB-Speicher blieb nach einem Verbindungsabbruch unerreichbar.** In manchen Fällen schlug nach einem Abbruch der Verbindung zu einem SMB-Server jede Datei darauf fehl, bis Ember neu gestartet wurde. Ember meldet sich jetzt auf der neuen Verbindung wieder an.
- **Geänderte Speicher-Einstellungen ließen Verbindungen offen.** Jede Änderung am Speicher einer Wache oder eines Verbunds ließ die Verbindung zum alten Server bis zum nächsten Neustart offen. Ersetzte Verbindungen werden jetzt geschlossen.
- **Löschen auf SFTP-Speicher konnte fehlschlagen.** In manchen Fällen wurde das Entfernen einer Datei, die auf dem SFTP-Speicher schon fehlte, mit einem Fehler beantwortet. Es gelingt jetzt still, wie auf jedem anderen Speicher.
- **Animierte Logos standen still.** Ein animiertes GIF als Logo einer Wache und die Kachel eines GIFs im Wiki zeigten nur das erste Bild. Sie bewegen sich jetzt.
- **Die Seite unter `/pitch` öffnete sich auf dem Handy im Desktop-Layout.** In manchen Fällen blieben die Anwesenheitsknöpfe ihres Beispiels auf einem Handy in Desktop-Breite, bis der Bildschirm gedreht oder die Fenstergröße geändert wurde. Die Seite wechselt jetzt gleich nach dem Laden ins Handy-Layout.
- **Eine unlesbare Nachricht konnte den Import eines Postfachs stoppen.** In manchen Fällen scheiterte der Import eines Postfachs bei jedem Durchlauf an einer einzigen Nachricht, die der Mailserver nicht herausgeben konnte, etwa einer, die ein anderes Mailprogramm gerade gelöscht hatte, bis das Postfach ausgesetzt wurde. Eine solche Nachricht wird jetzt übersprungen und der Rest des Postfachs importiert.
- **Nicht mehr erreichbare öffentliche Seiten zeigten einen unklaren Fehler.** Wer eine öffentliche Seite, einen Wiki-Artikel oder den Link zu einer Seite öffnete, die nicht mehr erreichbar war, etwa weil die öffentlichen Seiten der Wache ausgeschaltet waren, sah einen Fehler, der nichts darüber sagte, was schiefgegangen war. Die Seite sagt jetzt, dass es sie nicht mehr gibt, und der Dialog mit dem Link einer Seite warnt, solange die öffentlichen Seiten der Wache ausgeschaltet sind.
- **Neuigkeitenblöcke vergaßen ihre Neuigkeit.** Beim Speichern einer Seite oder eines Artikels ging die in einem Neuigkeitenblock gewählte Neuigkeit verloren, die danach als nicht mehr verfügbar erschien. Der Block behält jetzt seine Neuigkeit und zeigt sie so, wie sie gerade ist, und beim Auswählen lässt sich nach dem Titel durchsuchen, was alle Leser lesen dürfen: auf einer Seite der öffentliche Blog, in einer Neuigkeit oder einem Wiki-Artikel jede Neuigkeit, die allen Mitgliedern offensteht.
- **Das Teilen eines Boards mit Partnern ließ sich nicht speichern.** Das Speichern, mit welchen Partnerwachen ein Board geteilt wird, schlug fehl, sodass sich kein Board teilen ließ. Jetzt wird gespeichert, und für jeden Partner lässt sich wählen, welcher Mitgliedstyp dort das Board sehen darf.
- **Die E-Mail des Verbands in der richtigen Sprache und zur richtigen Zeit.** Die Benachrichtigungs-E-Mail des Verbands war immer auf Englisch geschrieben, und ihre Versandzeiten wurden in UTC statt in Ortszeit gelesen. Sie richtet sich jetzt nach Sprache und Zeitzone der Heimat-Wache des Verbands.
- **Keine doppelten Benachrichtigungen.** In manchen Fällen konnte dieselbe Benachrichtigung zweimal erscheinen, wenn zwei Personen im selben Moment dasselbe taten. Sie erscheint jetzt einmal.
- **Frühere Betreuende wurden weiter benachrichtigt.** Wer eine Wache verlassen hatte, konnte weiter Benachrichtigungen und E-Mails über die Mitglieder bekommen, die er früher betreut hat. Das ist nicht mehr so.
- **Quizze von Partnerwachen auf anderen Instanzen ließen sich nicht öffnen.** Ein Quiz, das eine Partnerwache auf einer anderen Instanz geteilt hat, ließ sich nicht öffnen, sobald es Fragen enthielt. Es öffnet sich jetzt mit allen Fragen.
- **Mit benannten Partnern geteilte Neuigkeiten und Termine fehlten bei Partnern auf derselben Instanz.** Eine Neuigkeit oder ein Termin, die mit benannten Partnerwachen geteilt waren, erreichten eine benannte Partnerwache auf derselben Instanz nie. Sie erreichen jetzt jede Partnerwache, die sie nennen.
- **Kommentare bei Partnerwachen auf anderen Instanzen schlugen fehl.** Ein Mitglied konnte seinen eigenen Kommentar zu einer Neuigkeit oder einem Termin einer Partnerwache auf einer anderen Instanz nicht löschen und in manchen Fällen auch keinen neuen Kommentar zu einem solchen Termin schreiben. Beides funktioniert jetzt.
- **Eigene Boards erschienen unter den Boards eines Partners.** In manchen Fällen tauchte ein Board, das eine Wache mit einer Partnerwache auf derselben Instanz geteilt hat, in ihrer eigenen Liste der Boards dieses Partners auf. Die Liste zeigt jetzt nur die Boards, die der Partner teilt.
- **Tickets von Partnern auf anderen Instanzen ließen sich nicht abbestellen.** Ein Mitglied, das ein Ticket auf einem Board einer Partnerwache auf einer anderen Instanz beobachtete, konnte das Beobachten nicht beenden. Das funktioniert jetzt.
- **Links zu Wachen auf der Netzwerkkarte führten ins Leere.** Auf der Karte des Discovery-Netzes öffnete der Link zu einer Wache einer anderen Instanz eine Seite, die es nicht gab. Sobald beide Instanzen diese Version nutzen, öffnet er die öffentliche Seite der Wache.
- **Die Discovery-Seite war auf Telefonen nicht über die Fußzeile erreichbar.** Auf schmalen Bildschirmen fehlte in der Fußzeile der Link zum Wachen-Verzeichnis. Jetzt steht er bei jeder Bildschirmbreite da.
- **Gruppen auf der Seite eines Mitglieds zu ändern konnte scheitern oder andere Änderungen zurücknehmen.** Die Auswahl von Gruppen auf der Bearbeitungsseite eines Mitglieds scheiterte für Personen, die Mitglieder bearbeiten, aber keine Gruppen verwalten durften, und zwei Personen, die gleichzeitig eine Gruppe änderten, konnten gegenseitig ihre Änderungen zurücknehmen. Die Seite speichert jetzt die Gruppen dieses einen Mitglieds.
- **Teilen mit ausgewählten Partnerwachen schlug fehl.** Ein Termin oder eine Neuigkeit, die nur mit einigen Partnerwachen geteilt war, ließ sich nicht speichern. Sie werden jetzt mit den gewählten Partnern gespeichert.
- **Das Bearbeiten eines Termins nahm seine Fragen aus dem öffentlichen Kalender.** Beim Speichern eines Termins wurde bei jeder seiner Fragen die Einstellung „öffentlich“ ausgeschaltet, sodass sie im öffentlichen Kalender nicht mehr erschienen. Fragen behalten jetzt ihre Einstellung.
- **Anwesenheitsfelder ohne Vorgabe bekamen eine.** Ein Anwesenheitsfeld ohne Vorgabewert erschien im Vorlagen-Editor so, als hätte es einen, und beim Speichern wurde ein leerer Text, eine Null oder ein Nein hinterlegt. Der Editor zeigt solche Felder jetzt ohne Vorgabe.
- **Im Anmeldeüberblick fehlte die Teilnehmergrenze.** Der Überblick über die Anmeldungen zeigte nicht, wie viele Plätze ein Termin hat. Die Grenze steht jetzt neben dem Termin.
- **Eingeschränkte Neuigkeiten zeigten kein Schloss.** Eine Neuigkeit, die nur für einen Teil der Wache bestimmt ist, erschien ohne das Schloss, das sie kennzeichnet. Sie zeigt es jetzt in der Liste der Neuigkeiten und beim Eintrag.
- **Neue Terminkategorien vergaßen Einstellungen.** Eine neue Kategorie wurde ohne die Zahl der angezeigten Termine und ohne ihre Einstellung „öffentlich“ gespeichert, sodass beides danach noch einmal gesetzt werden musste. Beides wird jetzt mit der Kategorie gespeichert.
- **Das Bearbeiten einer Terminkategorie konnte ihre Reihenfolge ändern.** Beim Speichern rückte eine Kategorie an den Anfang der Liste. Sie behält jetzt ihren Platz.
- **Eingeschränkte Formulare zeigten kein Schloss in der Liste zum Ausfüllen.** Ein Formular, das nur einem Teil der Wache gestellt ist, erschien dort ohne das Schloss, das es kennzeichnet. Es zeigt es jetzt.
- **Speichern eines Filters in der Mitgliederliste schlug fehl.** Wer die aktuellen Filter der Mitgliederliste unter einem Namen speichern wollte, bekam einen Fehler, und nichts wurde gespeichert. Der Filter wird jetzt gespeichert und wie jeder andere wieder angeboten.
- **Schritte einer Ablaufvorlage konnten nicht aufeinander warten.** Eine Abhängigkeit zwischen zwei Schritten einer Ablaufvorlage hinzuzufügen oder zu entfernen wurde abgelehnt, deshalb hatte keine Vorlage je eine. Abhängigkeiten werden jetzt gespeichert, und jeder Schritt zeigt, auf welche Schritte er wartet.
- **Schritte einer Ablaufvorlage verloren ihre Reihenfolge.** Neu angelegte oder bearbeitete Schritte einer Ablaufvorlage landeten alle am Anfang der Liste, sodass sich die Reihenfolge nach jeder Änderung verschieben konnte. Ein neuer Schritt kommt jetzt ans Ende, ein bearbeiteter bleibt an seinem Platz.
- **Eine geleerte Notiz an einem Schritt kam zurück.** Wer die Notiz eines Schritts in einem Ablauf leerte, behielt die alte Notiz. Leeren entfernt sie jetzt.
- **Quizfragen mit KI zu erzeugen schlug fehl.** Neue Fragen für einen Katalog oder eine neue Fassung ausgewählter Fragen von der KI anzufordern schlug fehl, und Fragen, die doch ankamen, wurden ohne ihre Antworten gespeichert. Erzeugte Fragen kommen jetzt an und werden vollständig gespeichert.
- **Freitext- und Bildfragen zeigten keine Antwort.** In einem nur lesbar geöffneten Quizkatalog zeigten Freitext- und Bildfragen keine richtige Antwort. Sie nennen jetzt wie alle anderen Fragearten die akzeptierten Antworten.
- **Korrekturen fehlten im Verlauf eines Gegenstands.** Wenn eine Kontrolle richtigstellte, wer einen Gegenstand hat, erschien das im Verlauf wie eine gewöhnliche Rückgabe. Solche Einträge sind jetzt als Korrektur gekennzeichnet.
- **Eine Art, die das Umstellen einer Liste verhinderte, wurde nicht genannt.** Ließ sich eine Sammlung nicht in einen Bestand umstellen, weil darin noch Arten angelegt sind, erschien der Grund als unlesbarer Text. Jetzt wird die Art genannt, die im Weg steht.
- **Der Inventarschalter eines Verbands stand immer auf aus.** Die Einstellung, dass ein Verband seine Ausrüstung in Ember führt, erschien beim Öffnen seiner Inventareinstellungen immer ausgeschaltet, auch wenn sie an war. Sie zeigt jetzt, wie sie eingestellt ist.
- **Abgewiesene unsignierte Mails erschienen als unlesbarer Text.** Im Importprotokoll eines Postfachs, das nur signierte Mails ablegt, erschien eine wegen fehlender, fremder oder ungültiger Signatur abgewiesene Nachricht als unlesbarer Text statt mit einem Grund. Jetzt wird der Grund genannt.
- **Die öffentliche URL-Kennung einer Wache ließ sich nicht entfernen.** Wer in den Föderations-Einstellungen die URL-Kennung der öffentlichen Seite leerte, behielt die alte, und sie kam nach dem Neuladen zurück. Ein leeres Feld entfernt sie jetzt.
- **Ein neues Lesezeichen auf einem föderierten Board erschien nicht.** Ein Lesezeichen auf der Seite der föderierten Boards blieb bis zum Neuladen unmarkiert, und ein zweiter Klick versuchte, das Board noch einmal zu merken. Das Lesezeichen erscheint jetzt sofort, und ein zweiter Klick entfernt es.
- **Erziehungsberechtigte sahen die Ausrüstung ihrer betreuten Mitglieder ohne ihren Stand.** Unter „Mein Inventar“ zeigte diese Ausrüstung weder den Schritt eines Tauschs noch ihr Bild und bot für Gegenstände, die schon unterwegs waren, weiter Tausch und Verlustmeldung an. Sie erscheint jetzt genau so, wie das Mitglied sie selbst sieht.
- **Eine Profilantwort konnte bei der falschen Frage landen.** In manchen Fällen, wenn eine Wache und ihr Verband je eine Frage unter derselben Nummer stellten, zeigte die eigene Profilseite eines Mitglieds für beide eine Antwort und speicherte sie nur bei der Frage der Wache. Jede Frage behält jetzt ihre eigene Antwort.
- **Konten ohne Adresse zeigten "(null)".** Ein Konto, das sich mit einem Benutzernamen anmeldet und keine E-Mail-Adresse hat, erschien in der Kontoauswahl der Administration und beim Zurücksetzen seines zweiten Faktors mit seinem Namen und dahinter "(null)". Jetzt steht dort nur der Name.
- **Hilfebeispiele ließen eine Berechtigung leer.** In den Beispielen des Hilfecenters zu Mitglieder-, Mitgliedstyp- und Gruppenberechtigungen erschienen die Rechte für Anwesenheits- und Terminverwaltung, die das Beispiel vergibt, als nicht vergeben. Die Beispiele zeigen sie jetzt angehakt.
- **Die Mitgliederliste markierte nie ein unvollständiges Profil.** Mitglieder, die eine Pflichtfrage ihres Profils offen gelassen hatten, sahen in der Mitgliederliste aus wie alle anderen. Sie tragen jetzt „Unvollständig“ neben ihrem Namen, beurteilt wie bei der Erinnerung in ihrem eigenen Profil.
- **Ehemalige Mitglieder konnten Listen und Exporte abbrechen lassen.** In manchen Fällen ließ ein ehemaliges Mitglied, dessen Konto entfernt worden war, den Anwesenheitsbericht und seinen Export, eine Inventarprüfung, die Bestands- und Bewegungsexporte, die Liste der betreuten Mitglieder eines Erziehungsberechtigten oder den Änderungsverlauf eines Profils mit einem Fehler abbrechen. Ein solches Mitglied erscheint jetzt mit Namen oder Nummer.
- **„Im Browser öffnen" in einem Feed-Eintrag konnte ins Leere führen.** In manchen Fällen trug eine Benachrichtigung im RSS- oder Atom-Feed einen Knopf „Im Browser öffnen" ohne Ziel. Er öffnet jetzt dieselbe Seite wie der Eintrag selbst.
- **Fragen zur Anmeldung aus einer Terminvorlage kamen nicht an.** Ein Termin, der aus einer Terminvorlage erstellt wurde, übernahm die Fragen zur Anmeldung aus der Vorlage nicht und in manchen Fällen stattdessen die einer anderen Vorlage. Er übernimmt jetzt die Fragen der Vorlage, aus der er erstellt wurde, und sie stehen schon vor dem Speichern im Editor.
- **Eine wiederholte Meldung von Sweego konnte eine Mail doppelt senden.** In manchen Fällen, wenn Sweego dieselbe Meldung über eine nicht zugestellte Mail erneut schickte, ging die Mail zweimal hinaus oder wechselte zu früh zum nächsten Anbieter. Eine erneut eintreffende Meldung wird jetzt erkannt und nur einmal gezählt.
- **Antworten auf Profilfragen des Verbands wurden nicht geprüft.** Eine Antwort auf eine Profilfrage des Verbands wurde gespeichert, was immer sie enthielt, etwa eine Auswahl, die die Frage nicht anbietet, oder ein Datum, das keines ist. Sie wird jetzt geprüft wie eine Antwort auf die eigenen Fragen der Wache.
- **Der Mitgliederimport übernahm Antworten, die eine Frage nicht annimmt.** Beim Import von Mitgliedern wurde eine Zelle wie eine unbekannte Auswahl, ein Tag, der keiner ist, oder "vielleicht" unter einer Ja/Nein-Frage so gespeichert, wie sie dastand. Eine solche Zelle wird jetzt ausgelassen, und Vorschau und Ergebnis nennen ihre Zeile.
- **Board-Felder nahmen jeden Wert.** Ein eigenes Feld an einem Board-Ticket ließ sich mit einem Datum speichern, das kein Datum ist, oder mit einer Auswahl, die das Feld nicht anbietet. Solche Werte werden jetzt beim Speichern abgewiesen.
- **Datumsfelder von Anwesenheitslisten konnten nicht mit heute beginnen.** Ein Feld einer Anwesenheitsvorlage, das mit dem heutigen Datum beginnen soll, wurde beim Speichern mit dem Hinweis abgewiesen, es erwarte ein Datum. Es lässt sich wieder speichern, und neue Listen beginnen mit dem Tag, an dem sie angelegt werden.
- **Wer eine Gruppe verlassen hatte, konnte sich nicht aus einem Feld austragen.** Ein Mitglied, das sich in ein Feld eines Termins eingetragen hatte, das auf eine Gruppe, einen Mitgliedstyp oder ein Tag beschränkt ist, wurde beim Austragen abgewiesen, nachdem es nicht mehr dazugehörte. Das Austragen klappt jetzt immer.
- **Ja-Antworten konnten als Nein erscheinen.** In manchen Fällen erschien ein Ja, das eine ältere Version gespeichert hatte, bei den Feldern eines Termins und in den Antworten auf Anmeldefragen als Nein. Solche Antworten erscheinen jetzt überall als Ja.
- **Ein Zahlenfeld am Board konnte keine Null halten.** Eine 0 in einem Zahlenfeld eines Tickets leerte das Feld. Die Null bleibt jetzt stehen.
- **Antworten auf Anmeldefragen hielten sich nicht an die Grenzen der Frage.** In manchen Fällen nahm das Feld für eine Zahlenfrage Zahlen außerhalb ihres Bereichs, und eine auf eine Gruppe oder ein Tag beschränkte Mitgliederfrage bot alle Mitglieder an, sodass das Speichern dann scheiterte. Das Feld hält sich jetzt an den Bereich und bietet nur die Mitglieder an, die die Frage annimmt.
- **Ausrüstung aus dem Lager des Verbands ließ sich an der Wache nicht öffnen.** Ein Stück, das der Verband in seinem eigenen Lager führt und einer Wache geschickt hat, stand in den Listen der Wache und fand sich beim Scannen, aber beim Öffnen hieß es, es sei nicht vorhanden. Die Wache öffnet es jetzt, gibt es aus und meldet es wie jedes andere Stück, das sie hat.
- **Die Wahl eines Ablaufs konnte scheitern, wenn sie zweimal gleichzeitig gespeichert wurde.** In manchen Fällen, wenn der Ablauf für ein Inventar an zwei Stellen im selben Moment gewählt wurde, wurde eine davon mit dem Hinweis abgelehnt, der Eintrag sei schon vorhanden. Beide Speichervorgänge gehen jetzt durch, und der letzte ist der Ablauf, der gilt.
- **Ausrüstung auf der Seite eines Mitglieds widersprach der Liste der Bewegungen.** Ein Stück mit laufendem Tausch oder laufender Rückgabe nannte den Schritt, auf den noch gewartet wurde, sodass eine Jacke als zurückgenommen galt, während das Mitglied sie noch hatte, und ein Ersatz, dessen Erhalt das Mitglied noch bestätigen sollte, zeigte gar nichts. Jedes Stück zeigt jetzt den letzten erledigten Schritt und wer an der Reihe ist, genau wie die Liste der Bewegungen.
- **Die Schnellprüfung nannte den Schritt, auf den noch gewartet wurde.** Bei einer Inventarprüfung zeigte ein Stück mit laufendem Tausch oder laufender Rückgabe den Schritt, auf den die Bewegung wartete, sodass eine Jacke als zurückgenommen galt, während sie noch in der Hand lag, und ein Ersatz, dessen Erhalt das Mitglied noch bestätigen sollte, bot weiter einen Tausch an, der dann abgelehnt wurde. Die Schnellprüfung zeigt jetzt den letzten erledigten Schritt und wer an der Reihe ist, genau wie die Liste der Bewegungen.
- **Der Test des Verbandsspeichers zeigte den genauen Verbindungsfehler.** Ein fehlgeschlagener Speichertest auf der Speicherseite des Verbands zeigte den genauen Grund, etwa eine abgewiesene Verbindung oder eine Zeitüberschreitung, was mehr über das Netz hinter der Adresse verrät, als es sollte. Er antwortet jetzt so allgemein wie der Speichertest einer Wache, und der genaue Grund steht im Protokoll der Instanz.
- **Dokumente eines gelöschten Mitglieds wurden zu Unterlagen der Wache.** Dokumente über ein Mitglied blieben zurück, wenn das Mitglied oder sein Konto gelöscht wurde, nannten niemanden mehr und konnten dann von allen gelesen werden, die die eigenen Dokumente der Wache lesen dürfen. Sie werden jetzt mit dem Mitglied gelöscht, und solange ein aufbewahrtes Dokument nur dieses Mitglied nennt, wird das Löschen abgelehnt, damit das Mitglied stattdessen archiviert werden kann.
- **Das Archivieren über den Verband ließ die Zugänge des Mitglieds bestehen.** Ein Mitglied, das über die Mitgliederverwaltung des Verbands archiviert wurde, behielt seine Anmeldung, Rollen, Erziehungsberechtigten, Gruppen, Tags, Dokumente und Profilangaben, als wäre es nie ausgetreten. Das Archivieren dort tut jetzt genau dasselbe wie das Archivieren an der Wache und wird in denselben Fällen abgelehnt, etwa wenn noch Ausrüstung ausgegeben ist.
- **Links in Mails des Verbands führten auf die falsche Seite.** Der Knopf in der Benachrichtigungsmail des Verbands öffnete eine Seite, die es nicht gibt, und die meisten Hinweise darin öffneten die Startseite einer Wache statt der Seite des Verbands, um die es ging. Sie öffnen jetzt die Seiten des Verbands, und zwar in dem Verband, um den es in der Mail geht.
- **Manche Hinweise führten ins Leere.** Ein Hinweis auf eine Leihanfrage, eine Speicherwarnung oder importierte Post, die noch abgelegt werden muss, tat in der App beim Öffnen nichts und öffnete aus einer Mail oder einem Feed die Startseite. Er öffnet jetzt die Leihanfrage, die Speicherseite und die Mitgliederdokumente.
- **Benachrichtigungseinstellungen ließen sich nicht mehr speichern.** Sobald der Schalter für Tausch-Anfragen auf der Seite der Benachrichtigungseinstellungen einmal betätigt war, scheiterte jede weitere Änderung auf dieser Seite beim Speichern. Der Schalter ist entfernt, da diese Hinweise längst zu Hinweisen über Bewegungen geworden sind, und die Seite speichert wieder.
- **Antworten der Wache und des Verbands gerieten durcheinander.** Hatten eine Frage der Wache und eine des Verbands dieselbe Nummer, zeigten manche Profilseiten die eine Antwort unter der anderen und speicherten sie bei der falschen Frage. Jetzt hält jede Profilseite die beiden auseinander.
- **Verbandsverwalter ohne Wache konnten kein Profil speichern.** Verwaltest du die Mitglieder des Verbands, gehörst aber zu keiner seiner Wachen, scheiterte das Speichern eines Profils. Jetzt klappt es, und der Änderungsverlauf nennt dich.
- **Feldverwalter kamen nicht an die Fragen des Verbands.** Durftest du die Profilfragen des Verbands bearbeiten, aber nicht seine Mitglieder sehen, blieb die Liste der Fragen für dich zu. Jetzt öffnet sie sich.
- **Dem Verband ließen sich keine Abstände hinzufügen.** Ein Abstand verlangte einen Namen, den das Formular gar nicht zeigte. Abstände werden jetzt von selbst durchnummeriert, wie an der Wache.
- **Beim Archivieren blieben die Antworten an den Verband stehen.** Hast du ein Mitglied archiviert, blieben seine Antworten auf die Fragen des Verbands erhalten, auch wenn eine Frage nicht zum Behalten markiert war. Jetzt werden sie gelöscht wie die der Wache.
- **„Bei Archivierung behalten“ ging bei neuen Fragen der Wache verloren.** Wer eine Frage mit diesem Haken anlegte, bekam sie ohne die Einstellung gespeichert. Jetzt bleibt der Haken von Anfang an.
- **Mitglieder erfuhren nicht, wenn der Verband ihr Profil änderte.** Der Verband konnte dein Profil über seine Mitgliederseite ausfüllen, ohne dass du davon hörtest. Jetzt bekommst du dann eine Benachrichtigung.

## v26.19.5

### Neue Funktionen

- **Ember hält sich selbst aktuell.** Auf Wunsch richtet der Installer einen Job ein, der stündlich die neueste Version holt, Ember damit neu startet und das alte Image aufräumt. Die Installationsseite bietet das als Schalter an, und die Hosting-Hilfe zeigt die Zeile zum Einrichten von Hand.

### Sicherheit

- **Die täglichen Zahlen verraten ihren Absender nicht mehr.** Die Zahlen an einen Beacon waren mit dem Schlüssel deiner Instanz signiert, der Beacon erkannte also, woher sie kamen. Jetzt gehen sie unsigniert raus, so wie es die Beacon-Einstellungen immer versprochen haben.

### Fehlerbehebungen

- **Die Suche findet nach einem Datenbank-Update wieder alles.** In manchen Fällen übersah die Suche in Wiki, Dokumenten und Boards nach einer neuen PostgreSQL-Hauptversion Wörter mit „ae", „oe" oder „ue". Ember baut die Suchindizes jetzt beim ersten Start nach so einem Wechsel neu auf.
- **Das Bearbeiten eines Formulars behält seine Antworten.** In manchen Fällen löschte das Speichern eines Formulars die schon gegebenen Antworten. Jetzt verschwinden nur Antworten auf Fragen, die du entfernt hast, und Ember fragt vorher nach.
- **Geänderte Daten im Verlauf sind gut lesbar.** Änderte sich ein Datum im Profil, zeigte der Verlauf beide Daten so, wie sie gespeichert sind, etwa 2026-03-31. Jetzt steht dort 31.03.2026, genau wie im Profil.
- **Team-Mitglieder und Manager sehen ihre betreuten Mitglieder.** Ihr Reiter für Beziehungen hieß Erziehungsberechtigte und bot an, jemanden zuzuordnen, was für sie gar nicht geht. Jetzt zeigt er die Mitglieder, um die sie sich kümmern, wie bei Erziehungsberechtigten.
- **Ein erneut ausgefülltes Formular behält die frühere Antwort.** Ein Formular für ein betreutes Mitglied begann immer leer, und ein zweites Absenden konnte die erste Antwort überschreiben, selbst wo Antworten nicht änderbar sind. Jetzt öffnet sich die frühere Antwort zum Bearbeiten, wo das Formular es erlaubt, und bleibt sonst unangetastet.
- **Das Alter beim Geburtsdatum lässt sich wieder ausblenden.** Wer das Alter neben einem Geburtsdatumsfeld ausschaltete, konnte das Feld nicht mehr speichern. Jetzt wird die Einstellung gespeichert und beachtet.
- **Schnellvorlagen für Profilfelder legen ihre Felder an.** Die meisten Schnellvorlagen in den Einstellungen der Mitgliederfelder, etwa Adresse oder Geburtsdatum, legten nichts an. Jetzt legen sie ihre Felder an, die richtigen als Pflichtfeld oder nur für die Mitgliederverwaltung bearbeitbar.

## v26.19.4

### Verbesserungen

- **Mehrtägige Termine zeigen beide Enden.** Die Terminseite und die Liste zum Verwalten zeigen so einen Termin jetzt vom ersten Tag mit Uhrzeit bis zum letzten. Er sieht nicht mehr aus, als liefe er jeden Tag von acht bis vier.

### Fehlerbehebungen

- **Mehrtägige Termine enden am richtigen Tag.** Ihre eigene Seite zeigte das Ende am Tag des Beginns. Jetzt steht dort der Tag, an dem sie wirklich enden.

## v26.19.3

### Verbesserungen

- **Ein Tausch zeigt beide Größen auf einen Blick.** In der Liste der Bewegungen stehen abgegebene und gewünschte Größe nebeneinander, mit einem Pfeil dazwischen. So siehst du sofort, was getauscht wird.
- **Beim Ersatzteil ist die richtige Größe sofort da.** Die gewünschte Größe steht über dem Teil, das du auswählst oder neu erfasst. Teile im Lager in dieser Größe sind hervorgehoben und stehen oben.

### Änderungen

- **Pro Termin antworten nur bei Serienterminen.** Der Schalter, eine Frage für jeden Termin einzeln zu beantworten, fehlt jetzt bei einmaligen Terminen. Dort gibt es ja nur einen Termin.
- **Die heutigen Termine stehen auf der Seite Termine.** Termine → Verwalten zeigt sie nicht mehr oben an. Du findest sie samt Anwesenheit auf der Seite Termine in der Seitenleiste.

### Fehlerbehebungen

- **Anwesenheitslisten nehmen nicht mehr alle als anwesend an.** Eine aus einem Termin erstellte Liste trug alle Zusagen als anwesend ein, und bei der Kontrolle blieb nichts zu prüfen. Diese Zeilen bleiben jetzt offen, bis du sie abhakst.
- **Pflichtformulare und Pflichttests fragen nur ihre Zielgruppe.** Ein auf Gruppen beschränktes Formular oder ein solcher Test forderte trotzdem die ganze Wache auf. Jetzt werden nur die gefragt, für die es gedacht ist, und Erziehungsberechtigte für die Mitglieder in ihrer Obhut.
- **Nachträgliche Erwähnungen in Kommentaren benachrichtigen jetzt.** Nur Erwähnungen im ursprünglichen Kommentar kamen an, ein später ergänzter Name blieb unbemerkt. Jetzt benachrichtigen auch ergänzte Erwähnungen, und bereits vorhandene werden nicht erneut gemeldet.

## v26.19.2

### Verbesserungen

- **Wenn etwas schiefgeht, steht da, was.** Statt „Das hat nicht funktioniert" sagt eine Fehlermeldung jetzt, was passiert ist, wer es beheben kann und was als Nächstes zu tun ist. Sieht es nach einem Fehler in Ember aus, kannst du ihn direkt melden, und die Betreiber deiner Installation bekommen alles, was sie brauchen.
- **Bei Regeln gibt es keinen Melde-Knopf mehr.** Etwas, das du nicht darfst, ein vergebener Name, eine zu große Datei: Das steht jetzt klar da, ohne Melde-Knopf. So kommen nur die Meldungen an, die sich zu lesen lohnen.
- **Was geklappt hat, meldet keinen Fehlschlag mehr.** Speichern, Löschen, Einladen und Übergeben meldeten einen Fehler, wenn nur die Liste dahinter nicht neu lud, und viele machten es dann doppelt. Ember unterscheidet das jetzt, und eine bloß veraltete Ansicht sagt das auch.
- **Jeder Fehler trägt einen kurzen Code.** Fehlermeldungen zeigen jetzt einen Code wie F-021, kurz genug fürs Telefon. Er steckt auch in der Meldung und führt direkt zu der einen Stelle, an der es hakt.
- **Fehlermeldungen sind auf Deutsch.** Meldungen direkt vom Server kamen bisher auf Englisch an. Jetzt sind sie durchgehend deutsch, und was noch nicht übersetzt ist, erscheint trotzdem, statt zu verschwinden.

- **Vergangene Termine haben einen eigenen Reiter.** Beide Terminlisten trennen jetzt, was kommt, von dem, was vorbei ist, und du scrollst nicht mehr durch Jahre an Erledigtem. Ein Serientermin bleibt bei den kommenden, solange er noch stattfindet, mit seinem nächsten Datum.
- **Terminlisten durchsuchen, filtern und blättern.** Jede Liste auf beiden Seiten nimmt eine Suche, eine Kategorie und einen Zeitraum an und lädt seitenweise. Deine Auswahl steht in der Adresse, so kannst du die Liste als Lesezeichen sichern oder weitergeben.
- **Die Terminübersicht liest sich nach Datum.** Einmalige Termine stehen in einer Liste nach Datum, jeweils mit ihrer Kategorie, und Serientermine haben einen eigenen Block. Eine Kategorie zu wählen ist jetzt ein Filter statt einer Überschrift zum Hinscrollen.
- **Geschlossene Umfragen lassen sich wieder öffnen.** Das Menü einer geschlossenen Umfrage bietet jetzt an, sie wieder zu öffnen. Die Umfrage der letzten Saison läuft so erneut, ohne dass du sie neu schreibst.
- **Antworten einer Umfrage verwerfen und neu starten.** Das Menü löscht auf Wunsch alle gesammelten Antworten, Umfrage und Fragen bleiben stehen. Ein Probelauf oder eine Runde an die falschen Leute lässt sich so zurücksetzen, und alle dürfen erneut antworten.
- **Eine abgelaufene Umfrage sagt, wann sie endete.** Ihre Kachel in der Liste der Wache zeigt das Datum, an dem sie geschlossen hat. Die Seite hinter dem Link sagt, seit wann keine Antworten mehr angenommen werden, statt nur, dass sie zu ist.
- **Umfrage-Einstellungen speichern sich beim Ändern.** Name, Daten, Reichweite und Schalter sind gesichert, sobald du sie änderst, und eine zu weit reichende Umfrage hängt nicht mehr an einem vergessenen Klick. Die Fragen warten weiter auf Speichern, und die Seite sagt dir das.

### Sicherheit

- **Die Anmeldung verrät nicht mehr, wer ein Konto hat.** Falsche Adresse, falsches Passwort und ein Konto mit anderer Anmeldeart bekommen jetzt genau dieselbe Antwort. Über das Anmeldeformular lässt sich nicht mehr herausfinden, welche Adressen registriert sind, und dasselbe gilt für Bestätigungslinks, Passkey-Anmeldung, Gerätecodes und den zweiten Faktor.

### Änderungen

- **Kein Hinweis mehr auf Konten ohne Passwort.** Die Anmeldeseite sagt nicht mehr, wenn ein Konto sich ohne Passwort anmeldet, denn genau das machte das Formular zur Kontenliste. Mit Passkey nutzt du wie bisher die Schaltfläche dafür.

### Fehlerbehebungen

- **Die Identitätsbestätigung nennt den echten Grund.** Die zusätzliche Bestätigung vor sensiblen Änderungen sagte immer „Passwort falsch" oder „Ungültiger Code", also wirkten zu viele Versuche oder eine abgelaufene Bestätigung wie ein Tippfehler. Jetzt steht da, was wirklich schiefging.
- **Keine englischen Fehlermeldungen mehr.** Meldungen direkt vom Server erschienen unverändert, mitten auf einer deutschen Seite also ein englischer Satz. Jetzt sind sie alle übersetzt.

- **Zwischenüberschriften bei Profilfeldern sind wieder Überschriften.** Das Formular für neue Mitglieder und das für Erziehungsberechtigte zeigten jede Überschrift der Wache als leeres Eingabefeld. Beide zeigen Überschriften jetzt als Überschriften, mit den Fragen in der Reihenfolge der Wache.
- **Erziehungsberechtigte können reservierte Fragen nicht mehr beantworten.** Eine Frage, die der Mitgliederverwaltung vorbehalten ist und die sie nur lesen dürfen, wurde ihnen wie jede andere angeboten. Jetzt erscheint sie ausgefüllt und gesperrt.
- **Monatliche und vierteljährliche Termine öffnen am richtigen Tag.** Ohne Tag in der Adresse zeigte so ein Termin den nächsten passenden Wochentag, und Anmeldungen, Anwesenheit und Fragen gehörten zu einem falschen Tag. Jetzt öffnet er am nächsten echten Termin, Pausen der Wache und Serienende eingerechnet.
- **Links auf öffentliche Umfragen funktionieren wieder.** Der angebotene Link enthielt den lesbaren Namen der Wache, angenommen wurde aber nur die Adresse mit ihrer internen Kennung, also führte jeder kopierte Link ins Leere. Jetzt öffnen diese Links die Umfrage.
- **Eine öffentliche Umfrage erscheint auch allein.** Eine öffentliche Umfrage steht im Rahmen der Wache, und eine Wache ohne öffentliche Seiten, Wiki, Kalender, Warteliste und Blog gab nichts über sich preis. Die Seite der Umfrage blieb für alle leer, jetzt zeigt sie die Umfrage.
- **„Genau" bei Auswahlfragen lässt sich speichern.** Die Einstellung „genau" bei Fragen mit mehreren Antworten wurde beim Speichern abgelehnt, und die Umfrage kam kommentarlos unverändert zurück. Jetzt wird sie gespeichert.
- **Umfrage-Kacheln wissen, wann eine Umfrage zu ist.** Eine Kachel zeigte „offen" nach dem Enddatum, während die Umfrage selbst schon geschlossen meldete, denn die Kachel prüfte nur das Schließen von Hand. Jetzt liest sie auch die Daten und sagt, wenn eine Umfrage noch nicht begonnen hat.

## v26.19.1

### Verbesserungen

- **Listeneinträge im neuen Tab öffnen.** Zeilen und Karten in der ganzen Wache verhalten sich jetzt wie die Links, die sie sind. Mittlere Maustaste für einen neuen Tab, rechte zum Kopieren der Adresse, dazu Tastatur und Vorlesen als Link.
- **Das öffentliche Wiki sieht aus wie das echte.** Öffentliche Ordner und Artikel sehen jetzt so aus, wie die Mitglieder sie kennen: gleiche Einträge, gleiche Suchergebnisse, Ordnerbilder und Dateivorschauen. Auch der Wechsel zwischen Kacheln und einzeiliger Liste ist da.
- **Geteilte öffentliche Links sagen, was sie zeigen.** Kalender, Blog-Beiträge, Wiki-Artikel und Warteliste tragen einen eigenen Namen und eine kurze Beschreibung. Ein Link im Chat zeigt jetzt eine richtige Vorschau mit dem Logo der Wache.
- **Öffentliche Seiten kommen fertig an.** Seiten, Kalender, Blog-Beiträge und Wiki-Artikel kommen jetzt lesefertig vom Server, ohne Ladekreis. Dadurch sehen auch Suchmaschinen, was darauf steht.
- **Öffentliche Daten folgen der Uhr der Wache.** Termine, Blog-Beiträge und Wiki-Artikel zeigen Datum und Uhrzeit in der Zeitzone der Wache, egal wo jemand liest. Ein Termin um sieben Uhr abends heißt auch im Ausland sieben Uhr.

### Fehlerbehebungen

- **Das öffentliche Wiki behält lesbare Adressen.** Ordner und Artikel im öffentlichen Wiki wechselten auf eine Adresse mit der internen Kennung der Wache statt ihrem Namen. So ging die lesbare Adresse verloren und beim Teilen der richtige Link, jetzt nutzen alle öffentlichen Links den lesbaren Namen, sofern es einen gibt.

## v26.19.0

### Neue Funktionen

- **Umfragen und Kontaktformulare per Link verschicken.** Jede Umfrage und jedes Kontaktformular hat jetzt einen eigenen Link, der es auf einer schlichten Seite mit dem Namen der Wache öffnet. Wer den Link hat, kann antworten, er steht in keinem Menü und Suchmaschinen ignorieren ihn, und ein neuer Link beendet jede Kopie des alten.
- **Öffentliche Formulare nur noch über ihren Link.** Umfragen und Kontaktformulare sind standardmäßig öffentlich erreichbar, damit sie auf einer öffentlichen Seite stehen können. Schaltest du das ab, funktioniert nur noch der Link, und ein neuer Link schließt wirklich jeden Weg hinein.
- **Seiten nur über ihren Link erreichbar.** Neben Entwurf und Veröffentlicht kann eine Seite jetzt für alle offen sein, die ihren Link haben. Sie fehlt in Menü und Sitemap, steht ohne Seiten darüber oder darunter, und ihr Link lässt sich ebenso ersetzen.
- **Mitglieder-Felder pro Datum ausfüllen.** Ein Mitglieder-Feld an einem Serientermin kann für jeden Tag einen eigenen Eintrag halten. So steht drin, wer diese Woche fährt und wer nächste, und ohne die Einstellung gilt wie bisher ein Eintrag für die ganze Reihe.

### Verbesserungen

- **Ein Mitglieder-Feld setzt dich auf die Liste.** Wer in einem Mitglieder-Feld eines Termins steht, nimmt teil: Die Person steht auf der Anmeldeliste, wird mitgezählt und sieht den Termin in Kalender und Abo. Der Platz ist sofort bestätigt und wird zurückgegeben, indem der Name aus dem Feld verschwindet.
- **Das Kalender-Abonnement reicht ein Jahr zurück.** Bisher blieb nur die letzte Woche drin, der letzte Herbst war also unerreichbar. Jetzt umfasst es ein Jahr in beide Richtungen.
- **Beim Beschränken auf den Link stehen die betroffenen Seiten da.** Eine Umfrage auf einer Seite funktioniert dort nicht mehr, sobald sie nur noch über ihren Link läuft. Beim Umstellen nennt Ember diese Seiten, und sie zeigen einen Hinweis, bis jemand die Umfrage entfernt.
- **Bewertung, Rangfolge und Skala in öffentlichen Umfragen.** Öffentliche Umfragen boten diese drei Fragearten im Editor an, zeigten beim Ausfüllen aber nichts dazu. Jetzt funktionieren alle sechs Fragearten überall, wo eine Umfrage beantwortet wird.

### Fehlerbehebungen

- **Ungeöffnete oder geschlossene öffentliche Umfragen sagen das.** Eine Umfrage auf einer öffentlichen Seite vor dem Öffnen oder nach dem Schließen zeigte Fragen und einen Absenden-Knopf, der in „Bitte erneut versuchen" endete. Jetzt sagt sie klar, dass sie noch nicht offen oder schon geschlossen ist, und bietet nichts zum Ausfüllen an.
- **Links auf andere Seiten führen wieder ans Ziel.** Eine Karte mit Verweis auf eine andere Seite verlor ihr Ziel beim Speichern, zeigte einen Platzhaltertitel und führte nirgendwohin. Karten behalten ihr Ziel jetzt und folgen ihm, wenn eine Seite umbenannt oder verschoben wird.
- **Abgeschaltete öffentliche Seiten bleiben abgeschaltet.** Abgeschaltete öffentliche Seiten verschwanden aus Menü und Sitemap, ließen sich über die Adresse aber weiter öffnen. Jetzt wird die Einstellung bei jedem Abruf geprüft, per Adresse wie per verschicktem Link.
- **Serientermine zeigen alle ihre Tage.** Die Terminübersicht behielt pro Termin nur einen Eintrag, ein wöchentlicher Dienst blieb also eine Zeile, egal wie weit du blättertest. Jetzt läuft die Liste Tag für Tag, zehn auf einmal.
- **Öffentliche Formulare stehen nicht mehr in der internen Liste.** Unter `/station/forms` standen auch Umfragen und Kontaktformulare für öffentliche Seiten neben den eigenen. Jetzt stehen dort nur noch die internen Umfragen der Wache.
- **Öffentliche Formulare bieten keine nutzlosen Einstellungen mehr.** Formulare ohne Anmeldung boten weiter „Antworten änderbar" und „Antwort erwartet" an, die beide wissen müssen, wer antwortet. Beides gibt es jetzt nur, wo man sich zum Antworten anmeldet.
- **Öffentliche Formulare tun nicht mehr so, als filterten sie.** Öffentliche Umfragen und Kontaktformulare boten dieselbe Auswahl an Mitgliedsarten, Gruppen, Tags und Personen wie interne, doch nichts wertete sie aus. Die Auswahl erscheint jetzt nur bei Umfragen, die Mitglieder der Wache beantworten.
- **Aus öffentlichen Umfrageergebnissen geht es richtig zurück.** Der Weg zurück führte zur internen Umfrageliste, in der die Umfrage gar nicht steht. Jetzt geht es zurück zur Liste, von der du kamst.
- **Seiten unter unveröffentlichten Seiten bleiben privat.** Eine veröffentlichte Seite unter einer unveröffentlichten ließ sich per Adresse öffnen und stand in der Sitemap. Jetzt ist eine Seite nur öffentlich, wenn alles darüber es auch ist.
- **Öffentliche Formulare benachrichtigen nicht mehr die ganze Wache.** Ein geöffnetes Kontaktformular oder eine öffentliche Umfrage meldete allen ein neues Formular, das sie dann abwies. Jetzt werden nur interne Umfragen angekündigt.

## v26.18.7

### Verbesserungen

- **Termin und Anwesenheitsliste sind verknüpft.** Das Menü am Termin öffnet die Liste für den angezeigten Tag oder legt eine an. Das Menü an der Liste führt zurück zum Termin an genau diesem Tag.
- **Geräte-Anmeldung fragt nach einer Zahl.** Das Gerät, das hereinmöchte, zeigt eine zweistellige Zahl, und das freischaltende Gerät wählt sie aus sechs aus. Wer nur ein Bild des Codes bekommen hat, sieht diese Zahl nicht, ein weitergeleiteter Code reicht also nicht mehr.
- **Zum Freischalten genügt ein Scan.** Der QR-Code enthält jetzt den Anmeldecode, und dein Handy öffnet direkt, was es freischalten soll. Kein Abtippen von acht Zeichen mehr, der Code steht aber weiter da, falls jemand nicht scannen kann.
- **Neue Einstellungen für geteilte Internetzugänge.** Gehen deine Mitglieder über einen gemeinsamen Anschluss ins Internet, kannst du die Grenzen pro Adresse für die Geräte-Anmeldung erweitern. Die Einstellungen stehen unter `auth.deviceHandshake`.

### Sicherheit

- **Ein Anmeldecode gehört jetzt zu einem Konto.** Bisher konnte jedes angemeldete Mitglied jeden offenen Code freischalten, ein herumgereichter Code gab also das Konto an den Antwortenden preis. Jetzt gibst du vorher deine Adresse oder deinen Benutzernamen an, und nur dieses Konto kann den Code freischalten.

### Fehlerbehebungen

- **Anwesenheit an jedem Tag erfassen.** Der Eintrag dafür erschien nur, solange der angezeigte Tag heute war, nachtragen oder vorbereiten ging also nicht. Jetzt gilt er für den Tag, den die Seite zeigt, und die Liste gehört zu diesem Tag.
- **Seltene Termine erscheinen in der Terminübersicht.** Die Liste sah nur vier Wochen voraus, Termine im Quartals- oder Jahrestakt fehlten darin ganz. Jetzt sieht sie so weit voraus, wie sie für eine volle Seite braucht.
- **Jeder Serientag bekommt seine eigene Anwesenheitsliste.** Die Anwesenheit eines wöchentlichen Termins landete auf dem ersten Datum der Reihe, und an jedem weiteren Tag öffnete sich dieselbe Liste. Jetzt bekommt jeder Tag seine eigene Liste mit seinem Datum.
- **Mehrere Geräte hinter einer Adresse können sich anmelden.** In einem Büro oder Gerätehaus mit gemeinsamem Anschluss wurde das zweite wartende Gerät abgewiesen und wartete dann endlos und stumm. Jetzt hat jedes Gerät und jedes Konto sein eigenes Kontingent.
- **Zu viele Versuche werden auch so benannt.** Ein Bildschirm, der zu oft versuchte, zeigte eine allgemeine Fehlermeldung und riet zu einem neuen Code, was den nächsten Versuch kostete. Jetzt steht dort, dass zu oft versucht wurde, und es wird gewartet.
- **Geräte-Anmeldung in jedem Browser.** Der Link auf der Anmeldeseite erschien nur in Browsern mit Passkey-Unterstützung, obwohl diese Anmeldung keinen braucht. Jetzt sehen ihn alle.

## v26.18.6

### Neue Funktionen

- **Umfrageergebnisse nach Mitgliedern auswerten.** Du kannst die Ergebnisse einer internen Umfrage nach Mitgliedsart, Gruppen, Tags, Alter und Profilangaben filtern und aufteilen. Vergleiche die Jugend mit der Einsatzabteilung oder schau dir die über 40-Jährigen an, nebeneinander in eigenen Farben und als Lesezeichen speicherbar.

### Verbesserungen

- **Umfrage-Exporte zeigen, wer geantwortet hat.** Tabelle und Ausdruck einer internen Umfrage enthalten jetzt neben dem Namen auch Mitgliedsart, Gruppen und Alter.
- **Bilder öffnen sich per Klick groß.** Bilder und Galeriebilder auf Seiten und in Artikeln aus dem Seiten-Editor sowie Fotos von Fundsachen öffnen sich jetzt bildschirmfüllend, ungeschnitten und mit Bildunterschrift. Escape oder ein Klick daneben schließt sie.
- **Hinweisboxen, Zitate und Bildunterschriften heben sich ab.** Im PDF aus dem Wiki stehen Hinweisboxen und Zitate in einem hinterlegten Kasten mit farbigem Balken. Die Zeile unter einem Bild steht klein und mittig darunter als Bildunterschrift.

### Änderungen

- **Markdown-Dateien im Wiki heißen jetzt Artikel.** Im Menü Neu und auf der Kachel steht jetzt „Artikel“ statt „Markdown-Datei“.

### Fehlerbehebungen

- **PDFs aus dem Wiki enthalten ihre Bilder.** Beim Speichern als PDF fiel jedes Bild weg, höchstens der Alternativtext blieb. Jetzt druckt Ember die eigenen Bilder der Wache mit, passend verkleinert oder in ihrer eingestellten Breite.
- **PDFs aus dem Wiki behalten ihre Formatierung.** Farbiger Text, Markierungen und Unterstreichungen kamen als einfacher Text, und Markierungen behielten ihre Gleichheitszeichen. Jetzt sehen sie im Druck aus wie im Artikel.
- **Bilder aus dem Seiten-Editor zeigen ihre Beschreibung.** Hatte ein Bildblock keinen eigenen Text, fehlten Alternativtext und Beschreibung aus der Mediathek in Artikeln und Neuigkeiten. Jetzt erscheinen sie dort und in Suche, Vorschau und PDF, wie schon auf Seiten.
- **Kacheln bleiben auf breiten Bildschirmen handlich.** Auf großen Monitoren blieb das Wiki bei vier Spalten und das Fundbüro bei drei, die Kacheln wurden riesig. Jetzt wächst die Zahl der Spalten mit dem Fenster.
- **Fotos im Fundbüro zeigen die ganze Fundsache.** Fotos wurden auf ihre Karte zugeschnitten und konnten so den Gegenstand verdecken. Jetzt ist das ganze Foto zu sehen, passend verkleinert.

## v26.18.5

### Neue Funktionen

- **Favoriten im Wiki.** Markiere jede Datei und jeden Ordner mit dem Stern, auch was eine Partnerwache mit euch teilt. Alles landet im Ordner Favoriten am Anfang des Wikis, und nur du siehst deine Favoriten.
- **Das Wiki zeigt, was seine Dateien sind.** Bilder und PDFs zeigen jetzt eine Vorschau auf ihrer Kachel, PDFs mit ihrer ersten Seite. So unterscheidest du einen Ordner voller Blätter, ohne jedes zu öffnen, und ältere Dateien bekommen ihre Vorschau beim ersten Öffnen ihres Ordners.

### Verbesserungen

- **Wiki-Dateien direkt von der Kachel laden.** Hochgeladene Dateien haben jetzt einen Download-Knopf auf ihrer Kachel, so wie Artikel ihr PDF anbieten. Du bekommst die Datei genau so, wie sie hochgeladen wurde.
- **Bilder auf Seiten laden in passender Größe.** Banner, Galerien und Bilder auf Seiten holen jetzt eine passend große Fassung statt des Originalfotos. Seiten öffnen schneller und brauchen auf dem Handy weniger Daten.

### Fehlerbehebungen

- **PDFs speichern in der installierten App klappt.** Unter Android hinterließ das Speichern eines PDFs in der über Firefox installierten App eine leere Seite, und andere Dateien meldeten trotz Erfolg einen Fehler. Jetzt öffnen PDFs im Betrachter des Browsers zum Speichern, und gespeicherte Dateien melden keinen Fehler mehr.
- **Gespeicherte Filter im Anwesenheitsbericht funktionieren.** Ein Filter unter einem Namen endete in einer allgemeinen Fehlermeldung, und ältere Filter verloren Mitgliedstypen oder Gruppen. Jetzt behält ein Filter alle Mitgliedstypen und Gruppen und gilt für die aktuelle Woche, den Monat, das Quartal oder das Jahr.

## v26.18.4

### Neue Funktionen

- **Wir heben deinen ungespeicherten Text auf.** Verlässt du eine Seite, eine Neuigkeit oder einen Wiki-Artikel ohne Speichern, bleibt dein Text im Browser. Kommst du zurück, bietet Ember ihn dir wieder an, bis du speicherst oder eine Woche vergangen ist.

### Verbesserungen

- **Dateien öffnen sich auf dem Handy, statt zu verschwinden.** Dokumente, Bilder und Aufnahmen öffnen sich auf Handy und Tablet jetzt direkt in der App, mit einem Knopf zum Speichern. Langsame Downloads endeten früher im Nichts, jetzt kommen sie an.
- **Exporte tragen einen Namen, der sagt, was drin ist.** Ein Bericht kommt etwa als `Anwesenheit - Januar 2026.pdf` an, statt jedes Mal gleich zu heißen. Der Name folgt der Sprache der Dokumente deiner Wache.
- **Tabellen öffnen sich sauber in deinem Programm.** Du wählst beim Export zwischen Semikolon und Komma. Umlaute kommen jetzt immer richtig an.
- **Anwesenheit je Quartal auswerten.** Neben Woche, Monat und Jahr kannst du jetzt auch ein Quartal wählen.
- **Den Anwesenheitsbericht gibt es als Tabelle.** Du kannst die Stunden je Name als Tabelle exportieren, nicht nur drucken. Das hilft allen, die anderswo weiterrechnen.
- **Du kannst die Antworten eines Formulars drucken.** Bisher gab es sie nur als Tabelle.
- **Der Anwesenheitsbericht lässt Abwesende weg.** Mitglieder ohne Stunden und ohne Termine füllen die Übersicht nicht mehr mit leeren Zeilen. Die Monatstabellen machen das schon länger so.
- **Bilder behalten Alternativtext und Bildunterschrift.** Hat eine Datei einen Alternativtext oder eine Bildunterschrift, nutzt eine Seite sie. Eigener Text der Kachel geht weiterhin vor.
- **Listen lassen sich auf dem Handy leichter abarbeiten.** Die Aktionen einer Zeile stehen jetzt am Fuß ihrer Karte, jeder Knopf in voller Breite. Auch die Spalten auf einer Karte haben mehr Luft.
- **Termintexte sehen in den Listen gut aus.** Unter Termine → Kommende und beim Anlegen einer Anwesenheit zeigten Beschreibungen rohe Zeichen oder schoben alles andere vom Bildschirm. Jetzt erscheinen sie so, wie sie geschrieben wurden, und enden nach ein paar Zeilen, wie bei Neuigkeiten.
- **Zwanzig neue Bilder für deine Ausrüstung.** Die Kleidung bekommt Hose, Stiefel, Turnschuhe, T-Shirt, Pullover, Kappe und Socke, die Ausrüstung dreizehn weitere Bilder von Axt bis Landkarte. Die bisherigen Notlösungen heißen jetzt schlichter, etwa Sohlen statt Fußspuren.

### Änderungen

- **Dokumente sind deutsch, außer du willst Englisch.** Eine Wache ohne eingestellte Sprache bekam ihre Berichte und Listen bisher auf Englisch, jetzt auf Deutsch. Stell die Sprache deiner Wache auf Englisch, wenn alles bleiben soll, wie es war.

### Fehlerbehebungen

- **Tabellen in einer Kachel gehen nicht mehr verloren.** Eine im Texteditor geschriebene Tabelle behielt nur ihre Wörter, die Zeilen waren nach dem Speichern weg. Jetzt bleibt eine Tabelle eine Tabelle.
- **Tabellenspalten passen zu ihrem Inhalt.** Spalten wuchsen mit dem längsten Wort, und das Ziehen an einer Kante arbeitete dagegen. Jetzt teilen sie sich die Breite gleichmäßig, im Editor wie auf der veröffentlichten Seite.
- **Der Speichern-Knopf bleibt in Reichweite.** Langer Text schob ihn unter den Rand des Fensters. Der Editor übernimmt das Geschriebene jetzt sofort und braucht keinen Knopf mehr.
- **Die Suche findet etwas, sobald die Liste geladen ist.** Wer in eine Auswahl tippte, bevor ihre Liste geladen war, sah unter Umständen dauerhaft keine Treffer. Jetzt sucht sie erneut, sobald die Liste da ist.
- **Anwesenheitslisten kommen schon ausgefüllt an.** Eine Liste zu einem Termin startete mit lauter offenen Namen, du musstest erst nachsehen, wer zugesagt hatte. Jetzt sind alle Zusagen als anwesend markiert, und bei Terminen mit Anmeldung alle anderen als abgemeldet.
- **Von Hand Eintragen liest den richtigen Tag.** Bei einem wiederkehrenden Termin las Ember die Antworten von heute statt die für den Abend der Liste, sodass jemand als abgemeldet auftauchen konnte. Jetzt entscheidet der Tag der Liste.
- **Verlustmeldungen lassen sich auf dem iPhone wieder sichern.** Dieser eine Download-Knopf ging am Teilen-Menü vorbei, sodass die Datei auf iPhone oder iPad ungesichert bleiben konnte. Jetzt funktioniert er wie alle anderen.
- **Exporte funktionieren auf Android-Handys.** Ein Download-Knopf konnte einen leeren Tab und keine Datei hinterlassen, vor allem in Browsern innerhalb anderer Apps. Berichte und Listen öffnen sich jetzt auf dem Bildschirm, bereit zum Lesen, Sichern oder Teilen.

## v26.18.3

### Verbesserungen

- **Die Spaltenauswahl schließt sich von selbst.** Bisher blieb sie offen, bis du ihren Knopf noch einmal gedrückt hast. Jetzt schließt sie ein Klick daneben oder Escape, und das Menü einer Zeile schließt sich, sobald du ein anderes öffnest.
- **Alle Spalten oder keine mit einem Klick.** Zwei Knöpfe oben in der Spaltenauswahl erledigen das für dich. Eine lange Liste verteilt sich jetzt auf mehrere Spalten, statt aus dem Bildschirm zu laufen.
- **Tabellenfilter passen zum Inhalt der Spalte.** Mitgliederliste, Inventarstücke und Anmeldungen filtern Daten nach Tag, Geburtsdaten nach Alter, Zahlen nach Bereich und Auswahlfelder nach Namen. Auch die Sortierung folgt dem, sodass Zahlen und Tage richtig stehen.
- **Tabellen merken sich deine Spalten.** Hast du Komfort-Einstellungen erlaubt, sind deine Spalten beim nächsten Besuch im selben Browser noch da. Die Datenschutzerklärung führt das jetzt einmal für alle Tabellen auf und fragt deshalb einmalig neu nach deiner Zustimmung.
- **Sortieren und Filtern auch auf dem Handy.** Auf einem kleinen Bildschirm werden die Zeilen zu Karten mit deinen Spalten. Sortierung und Filter stehen direkt darüber.
- **Weitere Listen der Wache sortieren und filtern nach jeder Spalte.** Bewegungen, geliehene Ausrüstung, Inventarprüfungen, Wartelisten, ehemalige Mitglieder, das Backlog der Boards und mehr funktionieren jetzt wie die Mitgliederliste. Jede Spaltenüberschrift sortiert und filtert, und bei längeren Tabellen wählst du die Spalten.
- **Listen von Verband und Administration ziehen nach.** Mitglieder, Speicher, Bewegungen und Ausrüstung des Verbands sowie Bewerbungen, Protokolle und Beacon-Zahlen der Administration sortieren und filtern über jede Spaltenüberschrift. Auch dort wählst du die Spalten.
- **Unbestätigte Bewerbungen fallen gleich auf.** Eine Bewerbung mit noch unbestätigter Adresse sieht nicht mehr aus wie jede andere wartende. Du kannst beide unterscheiden und filtern.
- **Beacon-Zahlen zeigen Konten und Wachen.** Diese zwei Spalten wurden erfasst, aber nie gezeigt. Jetzt kannst du sie in der Spaltenauswahl einschalten.

### Änderungen

- **Sortieren und Filtern wohnen in den Spaltenüberschriften.** Die eigenen Sortier- und Filterknöpfe bei Bewegungen, geliehener Ausrüstung und Inventarprüfungen sind weg, die Spaltenüberschriften übernehmen. Die Mitgliederliste im Inventar vergisst einmalig ihre bisherige Spaltenauswahl.

### Fehlerbehebungen

- **Anmeldungen sortieren richtig nach Datum.** Eine Datumsspalte in den Anmeldungen sortierte Tage als Text, sodass der 2. Januar vor dem 15. Dezember des Vorjahres stand. Jetzt sortiert sie nach dem echten Tag.
- **Gespeicherte Mitgliederfilter behalten offene Fragen.** Ein gespeicherter Filter nach unbeantworteten Fragen verlor diese Bedingung beim erneuten Auswählen. Jetzt kommt er vollständig zurück.
- **Gruppiertes Inventar folgt der Tabellensteuerung.** Bei nach Art gruppierten Stücken hatten Spaltenauswahl, Sortieren und Filtern keine Wirkung. Jetzt gelten sie für jede Gruppe.
- **Erziehungsberechtigte können die Dateien eines Termins öffnen.** Sie sahen einen beschränkten Termin über ihr Kind, bekamen seine Dateien aber verweigert. Jetzt stehen ihnen die Dateien offen, sobald eines ihrer Kinder den Termin sehen darf.
- **Downloads funktionieren auf dem iPhone.** Ein Download-Knopf auf iPhone oder iPad konnte ohne Meldung ins Leere gehen, vor allem in Browsern innerhalb anderer Apps. Jetzt öffnet die Datei das Teilen-Menü, und du sicherst sie in Dateien oder gibst sie weiter.

## v26.18.2

### Neue Funktionen

- **Personenlisten mit den Spalten, die du willst.** Die Gästeliste eines Termins und die Mitgliederliste zeigen neben jedem Namen, was deine Wache wissen will. Speicher eine Spaltenauswahl unter einem Namen und nimm sie als Blatt oder Tabelle mit, immer nur mit dem, was du sehen darfst.

### Fehlerbehebungen

- **Jährliche Termine fallen auf den richtigen Tag.** Bei Wachen, deren Uhr der des Servers vorausgeht, konnte ein jährlicher Termin einen Tag zu früh fallen. Ember liest den Tag jetzt auf der Uhr deiner Wache.
- **Die Anmeldung schließt nicht mehr einen Tag zu früh.** Die Frist fragte den Server nach dem Datum statt die Wache, und Wachen, die vorausgehen, schlossen ihre Listen deshalb einen Tag zu früh. Jetzt fragt sie die Wache.

## v26.18.1

### Neue Funktionen

- **Gib einer Partnerwache eigene Plätze.** Ein geteilter Termin kann Plätze für eine andere Wache zurücklegen, mit oder ohne Obergrenze. Diese Wache wählt dann selbst ihre Leute dafür aus, ganz ohne Bestätigung.
- **Bestimme, wann deine Benachrichtigungen verschickt werden.** Unter Station → Mailing legst du die Uhrzeiten fest, etwa sieben Uhr morgens und zwei Uhr nachmittags, oder einfach stündlich. Willst du eine Mail am Tag, bekommst du jetzt genau eine.

### Verbesserungen

- **Abmeldung zurücknehmen, wenn du es dir anders überlegst.** Fünf Minuten lang kannst du sie über die eingeblendete Meldung zurücknehmen und behältst deinen alten Platz in der Warteschlange. Die letzten beiden Knöpfe, die einen Platz mit einem Druck zurückgaben, fragen jetzt vorher nach, wie alle anderen.
- **Zu spät zur Anmeldung? Ember sagt, wer hilft.** Nach dem Anmeldeschluss gab es bisher nur eine nichtssagende Fehlermeldung. Jetzt steht dort, dass die Anmeldung geschlossen ist und die Terminleitung dich noch aufnehmen kann.
- **Gäste von Partnerwachen zählen wie eigene Mitglieder.** Ihre Anmeldung wird sofort angenommen, wo keine Bestätigung nötig ist, und abgelehnt, wo die Anmeldung zu ist oder der Termin ausfällt. Eine Abmeldung bleibt vermerkt, damit die ausrichtende Wache sieht, wer abgesagt und wer nie geantwortet hat.
- **Drei weitere Bilder für deine Ausrüstung.** Kopfhörer, Schlüssel und Tacker stehen jetzt unter Ausrüstung und Allgemein zur Auswahl.
- **Wer einem Verbund folgt, hört von dessen Partnern.** Benachrichtigungen aus einem Verbund wurden gesammelt, aber nie verschickt. Jetzt gehen sie mit denselben Zeiten und Einstellungen raus wie die einer Wache.
- **Termindateien öffnen sich direkt.** Ein Druck auf eine Datei zeigt sie an Ort und Stelle, so findest du die Karte unter vier Blättern, ohne alle herunterzuladen. Speichern hat weiterhin seinen eigenen Knopf.
- **Dateien zeigen, was drinsteckt.** Bilder und die erste Seite eines PDFs erscheinen jetzt neben jeder Datei, im Termin wie in der Mediathek. Schluss mit Reihen gleicher Symbole.
- **Auswahlmöglichkeiten an Terminen und Wartelisten bekommen je eine Zeile.** Beide nahmen die ganze Liste in einem Feld, sodass eine Auswahl mit Komma unbemerkt zu zweien wurde. Jetzt nutzen sie den gewohnten Editor mit einer sortierbaren Zeile je Auswahl.
- **Der Löschen-Knopf an Terminfeldern bleibt an seinem Platz.** Er rutschte mitten in den Bereich, wenn seine Zeile umbrach. Jetzt sitzt er oben rechts an seinem Feld.
- **Die Neuigkeitenliste liest sich wieder wie eine Liste.** Lange Beiträge werden auf wenige Zeilen gekürzt und laden zum Weiterlesen ein. Den ganzen Text findest du auf der Seite des Beitrags.

### Änderungen

- **Benachrichtigungsmails gehen frühestens stündlich raus.** Instanzen mit einer Sammelzeit unter einer Stunde verschicken jetzt zur vollen Stunde. Wer sich auf eine kürzere Wartezeit verlassen hat, sollte das wissen.

### Fehlerbehebungen

- **Neuigkeiten vom Ember-Team tragen ihr Zeichen auch in der Liste.** Das Ember-Logo erschien erst im geöffneten Beitrag, in der Liste war er von den Neuigkeiten deiner Wache nicht zu unterscheiden.
- **Das Löschen eines Kontos meldet keine Scheinprobleme mehr.** Es führte Daten auf, die angeblich nicht aufgeräumt werden konnten, obwohl nichts übrig war. Ember prüft diese Liste jetzt gegen die Datenbank selbst, damit sie stimmt.
- **Wer seinen Platz zurückgibt, bleibt sichtbar.** Ein unbestätigter Platz verschwand beim Zurückgeben spurlos aus der Liste, obwohl eine Benachrichtigung rausging. Jetzt bleibt jeder zurückgegebene Platz als zurückgezogen stehen, und eine neue Anmeldung klappt wie gewohnt.
- **Spät am Abend geöffnete Listen gelten dem richtigen Tag.** Eine Anwesenheit aus einem wiederkehrenden Termin las die Uhr des Servers und öffnete kurz vor Mitternacht den Vortag. Jetzt richtet sich der Tag durchgehend nach der Wache.

## v26.18.0

### Neue Funktionen

- **Häng ein Bild der Seite an deine Problemmeldung.** Teile diesen Tab oder wähle einen eigenen Screenshot und übermale vor dem Senden alles Private. Von allein wird nie etwas aufgenommen, und Passwortfelder sind abgedeckt, bevor du das Bild überhaupt siehst.
- **Nenn Mitglieder so, wie alle sie wirklich nennen.** Maximilian trägt den Spitznamen Max in sein Profil ein, und Board, Kommentare, Benachrichtigungen und Mails sagen Max, Mitgliederlisten zeigen `Maximilian "Max" Hoffmann`. Setzen dürfen ihn das Mitglied, seine Erziehungsberechtigten und die Mitgliederverwaltung, und eine Wache, die lieber Registernamen führt, schaltet das ab, ohne Spitznamen zu verlieren.
- **Dokumente behalten den Namen aus dem Register.** Anwesenheitslisten, Prüfprotokolle, Exporte und Datenauskünfte nennen Maximilian Hoffmann, egal was die Bildschirme zeigen. Wer die Wache verlässt, behält in alten Einträgen den Namen, unter dem die Wache ihn kannte.
- **Eine Anwesenheit ohne passende Vorlage starten.** Neben den Vorlagen gibt es jetzt eine leere Anwesenheit. Sie fragt, welche Mitgliedstypen und Gruppen dazugehören, und öffnet dann die gewohnte vorausgefüllte Liste.

### Verbesserungen

- **Vorlagen sagen dir, was sie mitbringen.** Jede Kachel für eine neue Anwesenheit nennt die Gruppen, die sie einträgt, und die Fragen, die sie stellt. Du wählst nicht mehr aus dem Gedächtnis.
- **Das Änderungsprotokoll zeigt jedes Erscheinungsdatum.** Jede Version trägt ihren Tag, mit der genauen Uhrzeit unter dem Mauszeiger. Ein Link am Ende zeigt ihre Änderungen auf GitHub.
- **Ein Geburtsdatum geht auch ohne Alter.** Das Feld hat einen neuen Schalter, standardmäßig an. Wachen, die das Alter separat abfragen, zeigen es nicht mehr doppelt.
- **Berechnete Felder zeigen nur Einstellungen, die zählen.** Niemand schreibt in sie hinein, also fragen sie nicht mehr nach Pflichtantwort, Schreibrecht, Änderungsmeldung oder Startwert.
- **Anwesenheitslisten bleiben übersichtlich.** Offene Vorgänge stehen nicht mehr unter jeder Zeile, eine Zeile zeigt ihre Anzahl neben einem Geburtstag, ein Druck öffnet alles. Vierzig Leute mit je einem Tausch machen aus der Liste keinen Einkaufszettel mehr.
- **Einen Tausch direkt vor Ort abbrechen.** Wer die Kontrolle führt, löscht einen Tausch von der Liste, nach einer kurzen Nachfrage mit dem Teil. Dort stehen nur Tausche, bei denen noch nichts übergeben wurde, es gibt also nichts zurückzunehmen.
- **Bilder von Meldungen prüfen, bevor sie zum Beacon gehen.** Eine Meldung mit Bild wartet in der Verwaltungsliste, wo du mehr übermalen oder das Bild herausnehmen kannst. Ein Schalter in den Beacon-Einstellungen schickt sie stattdessen sofort los.
- **Problemmeldungen räumen sich selbst auf.** Dreißig Tage nachdem eine Meldung als erledigt markiert wurde, wird sie samt Bild gelöscht. Bisher blieben sie für immer liegen.

### Fehlerbehebungen

- **Ein Profilfoto auszuwählen friert die Seite nicht mehr ein.** Die Seite reagierte nicht, während das Foto verkleinert wurde, ohne Hinweis, sodass Leute noch einmal drückten. Jetzt erscheint sofort eine Wartemarkierung, und die Arbeit läuft im Hintergrund, wo der Browser es erlaubt.
- **Speichern eines Profils meldet keine Scheinänderungen mehr.** Unbeantwortete Fragen und automatisch berechnete Alter erschienen als Änderungen, die niemand gemacht hatte. Sie werden nicht mehr aufgezeichnet und niemandem zur Bestätigung vorgelegt.
- **Erziehungsberechtigte können die Dokumente ihres Kindes lesen.** Einverständniserklärung, ärztlicher Vermerk und andere Dokumente wurden genau der Person verweigert, die sie unterschreibt. Jetzt stehen sie unter ihren eigenen Dokumenten zum Lesen, und was vor dem Mitglied verborgen ist, bleibt es auch vor ihnen.
- **Niemand wird einen Tag zu früh älter.** Auf Geräten mit einer Zeitzone hinter UTC lag das Alter einen Tag voraus. Ein Geburtsdatum bedeutet jetzt überall denselben Tag.
- **Berechnete Alter bleiben aktuell.** Profil und Antwortformular zeigten eine einmal geschriebene Zahl, die sich nie mehr änderte. Ember berechnet sie jetzt überall, wo sie steht, und niemand kann sie überschreiben.
- **Mitglieder öffnen ihren eigenen Vorgang ohne Fehler.** Die Seite zeigte einem Mitglied vier Ablehnungen und ein Formular, das es nicht nutzen konnte. Die Wahl des Ersatzteils sehen jetzt nur, wer das Lager lesen darf, alle anderen einen Hinweis, dass die Wache es einträgt.
- **Nur die richtige Partei kann einen Schritt ablehnen.** Jeder offene Vorgang bot allen Betrachtern den Ablehnen-Knopf an, sodass ihn jemand aus Versehen schließen konnte. Jetzt sieht ihn nur die Partei, die an der Reihe ist, und wer übergehen darf.
- **Termine am späten Abend zeigen ihre Anmeldungen.** Ein Termin, der in deiner Zeitzone nach Mitternacht endet, konnte eine leere Anmeldeliste zeigen, ohne Checkliste oder Umfrage. Ember liest den Tag jetzt durchgehend nach der Uhr der Wache.
- **Das Umbenennen einer Datumsfrage behält ihre Alter.** Altersfelder hingen am Namen der Frage, eine Umbenennung ließ die Spalte leer. Jetzt folgen sie der Frage selbst, und bestehende Altersfelder werden übernommen.

### Änderungen

- **Namen werden überall gleich geschrieben.** Ember bestimmte die Schreibweise eines Namens an rund 160 Stellen einzeln, sodass sich Liste und Auswahl widersprechen konnten. Jetzt gilt eine Regel für jeden Bildschirm.

## v26.17.3

### Fehlerbehebungen

- **Terminzeiten folgen der Uhr deiner Wache.** Ein Abend von 09:00 bis 14:00 kam im Benachrichtigungs-Feed als 07:00 bis 12:00 an, weil niemand die Zeitzone der Wache fragte. Feeds, exportierte Selbstchecks und Kalenderdateien nutzen jetzt die Zeit der Wache, und Wachen ohne Zeitzone lesen weiter in UTC.
- **Erinnerungen für kurz nach Mitternacht kommen pünktlich.** Ember bestimmte die Tage nach der Uhr des Servers, sodass halb eins in der Wache als Vorabend zählte. Erinnerungen dafür kamen einen Tag zu früh, jetzt nicht mehr.

## v26.17.2

### Verbesserungen

- **Beacons erfahren, was ein Fehler wirklich war.** Ein Fehler kam bisher nur als Quelle, Stufe und Anzahl an, sodass verschiedene Fehlschläge in einer namenlosen Zeile landeten. Jetzt reisen die geloggten Details mit, ohne Mailadressen.
- **Weitergegebene Meldungen bringen ihren Bildschirm mit.** Browser, Fenstergröße, die Rechte der schreibenden Person und die letzten Anfragen der Seite reisen mit, Adressen ohne ihre Parameter. Der Name bleibt in der Wache, denn „Der Knopf tut nichts" benennt allein noch keinen Fehler.

### Fehlerbehebungen

- **Links auf andere Ember-Seiten funktionieren wieder.** Als Pfad geschriebene Links, und so verlinkt Ember auf sich selbst, kamen als unterstrichener Text ohne Ziel an. Auch der Link im Eintrag nach einer Aktualisierung war betroffen, Links auf andere Seiten nie.
- **Systemeinträge zeigen keine Knöpfe mehr, die scheitern.** Bearbeiten, Löschen und Nachsehen, wer einen Eintrag von Ember gelesen hat, antworteten alle mit „nicht gefunden". Diese Knöpfe sind weg, und solche Einträge tragen jetzt das Ember-Logo statt Initialen, die niemandem gehören.
- **Benachrichtigungen schreiben Daten so, wie du sie schreibst.** Erinnerungen und andere Benachrichtigungen mit einem Tag zeigten ihn als 2026-09-19. Jetzt steht dort 19.09.2026.

## v26.17.1

### Fehlerbehebungen

- **Zustellungen an ein Beacon kommen endlich an.** Jeder Fehler und jede Meldung wurde als unsigniert abgewiesen, weil der Schlüssel zur Prüfung fehlte, und im Log stand nichts. Jetzt bringen Zustellungen den Schlüssel mit, und das Log sagt, wenn ein Beacon eine abweist oder nicht erreichbar ist.
- **Meldungen von Hand an ein Beacon schicken.** Meldungen von vor dem Einschalten des Schalters kamen nie dort an. Die Liste zeigt dir jetzt genau, was hinausgeht, und lässt dich senden, wie das Fehlerprotokoll.

## v26.17.0

### Neue Funktionen

- **Gib einem Termin Dateien mit.** Laufzettel, Formular zum Mitbringen oder Ablaufplan wählst du aus der Mediathek oder lädst sie hoch, und sie stehen auf der Terminseite zum Herunterladen bereit. Jede Datei ist entweder für alle, die den Termin sehen dürfen, Partnerwachen eingeschlossen, oder nur für die Leitung.
- **Das Änderungsprotokoll spricht Deutsch und kommt mit Ember.** Die Seite liest ihre Einträge jetzt von deiner eigenen Instanz statt von GitHub. Sie funktioniert ohne Internetverbindung, und niemand hinterlässt beim Lesen eine Spur bei GitHub.
- **Nach einem Update sagen dir die Neuigkeiten, was neu ist.** Ember schreibt einmalig einen Eintrag für die Verwaltung mit genau den Änderungen dieser Version. Ein Link führt zum vollständigen Änderungsprotokoll.

### Änderungen

- **Ein neues Recht für die internen Daten eines Termins.** Es öffnet das benötigte Material und die zurückgehaltenen Dateien, ohne Änderungen zu erlauben. Wer Termine bearbeiten darf, hat es schon, also gib es Helfern, die Abende leiten, aber kein Inventar führen.
- **Eine Profilfrage einmal schreiben und selbst wählen, wer antwortet.** Mitglieder → Konfiguration hat keinen Reiter je Mitgliedsart mehr, jede Frage gibt es einmal, und du wählst beliebige Mitgliedsarten und Gruppen dafür. Pflichtantwort, Feldbreite und Schreibrecht können je Zielgruppe anders sein, und jedes Formular hat seine eigene Reihenfolge.
- **Neue Fragen warten, bis du eine Zielgruppe wählst.** Eine Frage erreicht kein Profil, bevor du sagst, für wen sie ist, und die Liste zeigt das direkt an der Zeile. Anwärter sind jetzt eine eigene Art, du kannst sie also weniger fragen.
- **Ordne ein Formular direkt in der Vorschau an.** Zieh ein Feld an seinen Platz und an seiner rechten Kante, um die Breite festzulegen. Mit einem Abstand bringst du Fragen genau in die Reihe, die du willst.
- **Mehrere Fragen auf einmal einer Zielgruppe stellen.** Hak sie in der Liste an und wähl eine Mitgliedsart oder Gruppe. Fertig, in einem Zug.

### Fehlerbehebungen

- **Doppelte Fragen werden zu einer zusammengeführt.** Eine Frage, die je Mitgliedsart einmal angelegt war, erschien bei Leuten mit beiden Arten doppelt und sammelte zwei Antworten. Beim Update werden gleichnamige Fragen gleicher Art zusammengeführt, die ausgefüllte Antwort bleibt, und vorher landet eine Sicherung im Datenverzeichnis.
- **Problemmeldungen erreichen jetzt das Beacon.** Das Einschalten von „Problemmeldungen automatisch weitergeben" speicherte die Wahl, schickte aber nichts. Jetzt gehen Meldungen raus, sobald sie geschrieben sind, ohne die Abfrage in der Adresse der Seite.
- **Beacons nehmen an, was ihnen gemeldet wird.** Meldete eine Instanz nicht an sich selbst, wies ein Beacon jede Sendung als fremd ab, ohne Hinweis warum. Jetzt prüft es Sendungen gegen die eigene Adresse aus `api.baseUrl`.
- **Der Knopf für fehlendes Material erscheint nur bei denen, die ihn nutzen dürfen.** Material beim Verband anzufragen wies alle ohne dieses Recht ab. Jetzt sehen den Knopf nur, wer Material anfragen darf, und alle anderen sehen weiterhin, was fehlt.
- **Vergangene Bögen tragen das Datum des Abends.** Ein Wochen später ausgefüllter Bogen stand unter dem Tag der Eingabe, sodass ein Abend im Juli oben mit September stand. Jetzt tragen vergangene Bögen ihr eigenes Datum, der jüngste Abend zuerst.
