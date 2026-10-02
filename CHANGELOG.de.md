# Änderungsprotokoll

## v26.20.0

### Neue Funktionen

- **Ablaufdaten, die rechtzeitig erinnern.** Ein Profilfeld kann jetzt ein Datum halten, das abläuft, etwa einen Erste-Hilfe-Kurs oder einen Führerschein. Es wird gelb, wenn es bald so weit ist, und rot, wenn es abgelaufen ist, und das Mitglied und seine Mitgliederverwaltung bekommen vorher eine Erinnerung.
- **Formulare mit Seiten und Verzweigungen.** Du kannst ein Formular in Seiten aufteilen und jede Seite zur nächsten, zu einer späteren Seite oder direkt zum Absenden führen. Eine Frage mit einer Antwort kann die nächste Seite bestimmen, und die Auswertung zeigt, wie viele eine Frage gesehen haben.
- **Weitermachen, wo du aufgehört hast.** Ein begonnenes, nicht abgesendetes Formular bleibt erhalten und öffnet sich auf der Seite, auf der du aufgehört hast. Sehen kannst nur du und wer dich betreut, und es verschwindet, wenn das Formular schließt.
- **Einzelne Termine absagen und wiederherstellen.** Verantwortliche sagen jetzt gezielt den Termin ab, den sie gerade vor sich haben, und eine ganze Serie abzusagen bleibt ein eigener, endgültiger Schritt. Solange ein abgesagter Termin noch bevorsteht, holst du ihn zurück, und alle Angemeldeten erfahren davon.
- **Hintergrundaufgaben auf einen Blick.** Eine neue Seite unter Monitoring zeigt jede Arbeit, die der Server von selbst erledigt, etwa Mailversand, Erinnerungen und Aufräumen. Du siehst Rhythmus, letzten Lauf, Dauer und letzte Fehlermeldung, gezählt seit dem letzten Neustart.
- **Gruppensets für Stufen und Abschnitte.** Du kannst Gruppen zu einem Set bündeln, etwa die Stufen einer Ausbildung, und ein Mitglied ist nur in einer Gruppe pro Set. Sets legst du auf der Gruppenseite an, und wählst du eine andere Gruppe des Sets, wechselt das Mitglied dorthin.
### Verbesserungen

- **Zahlenspalten nach ihren Werten filtern.** Neben dem Bereich listet der Filter einer Zahlenspalte wie dem Alter jetzt die Werte ihrer Zeilen. Du kreuzt einfach die an, die du brauchst.
- **Vorschau, Duplizieren und ein Dankeschön.** Der Formular-Editor zeigt das Formular jetzt genau so, wie es ausgefüllt wird, samt Weg durch die Seiten. Formulare und Fragen lassen sich duplizieren, und nach dem Absenden kann eine eigene Nachricht mit Link erscheinen.
- **Fragen und Seiten per Ziehen sortieren.** Du ziehst Fragen innerhalb einer Seite oder auf eine andere, und auch ganze Seiten. Verlässt du den Editor mit ungespeicherten Fragen, fragt Ember nach und bietet sie beim nächsten Öffnen wieder an.
- **Die Ausleihe reicht zu Partnern auf anderen Instanzen.** Partnerwachen auf verschiedenen Instanzen können jetzt Ausrüstung ansehen, anfragen, verleihen und zurückgeben wie Wachen einer Instanz. Beide Instanzen brauchen diese Version, und ein Partner erscheint in den Angeboten, sobald er aktualisiert hat.
- **Fehler zeigen direkt auf die Frage.** Lassen sich Antworten nicht absenden, springt das Formular zur Seite mit dem Problem. Jede betroffene Frage ist markiert.
- **Abgesagte Termine sind überall gekennzeichnet.** Die kommenden Termine, der Monatskalender und dein persönlicher Kalender-Feed streichen einen abgesagten Termin jetzt durch und kennzeichnen ihn als abgesagt. Anmelden kann man sich dafür nicht mehr.
- **SFTP- und SMB-Speicher arbeiten parallel.** Seiten voller Bilder laden nicht mehr eine Datei nach der anderen, und Wachen auf demselben Verbund-Speicher teilen sich ihre Verbindungen. Im Test dauerten 32 Lesezugriffe neben einem großen Upload eine halbe statt vier Sekunden, und die Grenzen beschreibt die Speicher-Seite der Hilfe.
- **Eine klare Meldung, wenn der Speicher fehlt.** Antwortet der Speicher einer Wache nicht, erfahren die Leser jetzt, dass er gerade nicht erreichbar ist. Sie bekommen `503 Service Unavailable` statt eines allgemeinen Fehlers.
- **Bilder brauchen weniger Platz und laden schneller.** Die kleineren Größen von Profilbildern, Logos und Bildern im Wiki, im Quiz und bei Fundsachen werden jetzt als WebP gespeichert. Keine Größe wird mehr abgelegt, die so groß ist wie das Original, und bereits hochgeladene Bilder bleiben, wie sie sind.
- **Aus einem Termin wird ein fertiger Beitrag.** „Als Neuigkeit ankündigen“ öffnet einen Beitrag mit Terminblock für den gewählten Tag und der Beschreibung des Termins. Ein kurzer Text nennt, was der Block nicht zeigt, etwa Anmeldefrist und Plätze, und du kannst alles noch ändern.
- **Terminblöcke jetzt auch in Neuigkeiten.** Eine Neuigkeit oder ein Wiki-Artikel kann jetzt einen Termin als Terminblock zeigen, auch einen bestimmten Tag eines wiederkehrenden Termins. Leser außerhalb der Wache, etwa Partnerwachen oder der öffentliche Blog, sehen bei internen Terminen einen kurzen Hinweis.
- **Anwesenheitsvorlagen tragen ganze Mitgliedstypen ein.** Eine Vorlage kann jetzt neben Gruppen auch Mitgliedstypen nennen, und alle eines gewählten Typs stehen auf ihren Listen. Startest du eine Liste aus einer Vorlage, sind deren Typen und Gruppen schon angekreuzt, und du übernimmst oder änderst sie für diese Liste.
- **Die Discovery-Seite zeigt Wachen anderer Instanzen.** Die öffentliche Discovery-Seite unter `/discovery` zeigt jetzt auch die öffentlichen Wachen der anderen Ember-Instanzen, die diese kennt, jeweils mit ihrer Instanz und einem Link zu ihrer Seite dort. Ein Suchfeld findet Wachen aller Instanzen nach Name, Ort, Verband oder Instanz.
- **Die Einrichtung erklärt die Listung im Verzeichnis.** Der Schritt zur Sichtbarkeit bei der Einrichtung einer neuen Wache erklärt jetzt jede Wahl, auch die Listung nur auf dieser Instanz. Er zeigt genau, welche Angaben eine öffentliche Listung weitergibt, und wer die Voreinstellung unverändert speichert, hat den Schritt erledigt.
- **Gruppen für bestimmte Mitgliedstypen.** Eine Gruppe lässt sich jetzt auf einige Mitgliedstypen beschränken, etwa Team und Manager, und nimmt niemanden sonst auf. Ändert sich der Typ eines Mitglieds, verlässt es die Gruppen, die nicht mehr passen, und seine Bearbeitungsseite listet sie vorher auf.
- **Gruppen und Tags stehen oben bei den Berechtigungen.** Auf der Bearbeitungsseite eines Mitglieds stehen Gruppen und Tags jetzt als kompakte Chips über den Berechtigungen, und die Gruppen eines Sets sind eine einzige Auswahl. Gruppen für andere Mitgliedstypen erscheinen ausgegraut, mit den Typen, die sie aufnehmen.
- **Bearbeitete Kommentare zu Neuigkeiten sind gekennzeichnet.** Ändert jemand seinen Kommentar zu einer Neuigkeit, ist er jetzt als bearbeitet markiert. Bei Terminen, Wiki-Dateien und Board-Tickets war das schon so.
- **Hinweise auf Kommentare lesen sich überall gleich.** Zitiert ein Hinweis einen langen Kommentar zu einer Neuigkeit oder Wiki-Datei, endet der Auszug jetzt mit „…“, wie bei Terminen und Board-Tickets. Wirst du in einem Kommentar an einem Ticket erwähnt, nennt der Hinweis die Person, die ihn geschrieben hat, statt des Tickets.
- **Exporte geben Antworten einheitlich aus.** Mitgliederliste, Anmeldetabelle, Anwesenheitsliste, Terminliste und die Inventarlisten der Mitglieder zeigen Ja und Nein jetzt als Wörter in der Sprache der Wache. Daten stehen als Tage und Mitglieder mit Namen, wo manche Exporte bisher Rohwerte wie „true“ oder eine Mitgliedsnummer zeigten.
- **Ein Name für jeden Feldtyp.** Jede Seite, auf der eine Wache eigene Felder anlegt, von Profilfragen bis zu Wartelisten, bietet die Typen jetzt unter denselben Namen an. Auch Antworten sehen überall gleich aus, jetzt auch auf Anwesenheitslisten, an Board-Tickets und auf der öffentlichen Statusseite der Warteliste.
- **Board-Felder können Pflichtfelder sein.** Du kannst ein Board-Feld als Pflichtfeld markieren, dann lässt sich sein Wert ändern, aber nicht leeren. Die Antworten eines Auswahlfelds trägst du wie überall eine pro Zeile ein.
- **Vorlagen bringen Anmeldefragen mit.** Im Editor für Terminvorlagen legst du jetzt die Fragen zur Anmeldung an. Es ist derselbe Editor wie an einem Termin.
- **Zahlenfelder der Ausrüstung nehmen Schritte unter eins.** Im Feldeditor kannst du den Schritt eines Zahlenfelds der Ausrüstung auf einen Bruchteil wie 0,5 setzen. Dann nimmt das Feld Kommazahlen an.
- **Die Konfigurationshilfe nennt jede Einstellung.** Die Liste der Einstellungen und Umgebungsvariablen in der Hilfe kommt jetzt direkt vom Server. Sie zeigt auch die Grenzen für das Anmelden von Geräten, die älteren Ausweich-Mailanbieter und die verschlüsselten Speicher-Zugangsdaten, jeweils mit Schlüssel, Variable und Standardwert.
- **Benachrichtigungen des Verbands in der App.** Unter Verband → Benachrichtigungen siehst du jetzt, was dir der Verband gemeldet hat, und ein Hinweis öffnet seine Seite und gilt dann als gelesen. Die Glocke im Menü des Verbands zählt die ungelesenen, und zurückgehaltene Erinnerungen kommen wieder, sobald du die vorige hier gelesen hast.
- **Ablehnungen nennen den Wert, um den es geht.** Wird etwas abgelehnt, zeigt die Meldung jetzt den Wert, um den es geht, in deiner Sprache. Das kann sein, wie viel Speicher des Verbands noch frei ist, wie viele Inhalte noch auf eine Gruppe beschränkt sind oder zu welcher Frage eine Antwort nicht passte.
- **Abläufe können ohne Bestätigung des Mitglieds auskommen.** Jeder Ablauf auf der Seite der Abläufe hat einen Schalter, der den Erhalt für das Mitglied bestätigt, sobald ein Teil dort ankommt. Der Verlauf der Bewegung zeigt diesen Schritt als automatisch bestätigt, und andere Abläufe fragen weiter nach.
- **Der Speicher deines Verbands hat einen Verlauf.** Die Speicher-Seite des Verbands zeigt jetzt jede Änderung: den Speicher, den du eingerichtet hast, deine Entscheidungen, jeden Umzug und jede abgelehnte Änderung. Einen Umzug, den du für eine Wache anstößt, siehst du auch im Verlauf dieser Wache.
- **Jede Speicher-Seite fragt, bevor Dateien umziehen.** Verschiebst du die Dateien einer Wache oder gibst den Speicher deines Verbands auf, fragt die Seite jetzt einmal nach, so wie es die Seiten der Wache und der Instanz schon taten. Auf allen drei Seiten siehst du dieselbe Übersicht und dieselbe Frage.
- **Abgelehnte Speicher-Änderungen werden festgehalten.** Wird eine Änderung am Speicher abgelehnt, steht das jetzt im Verlauf der Wache, des Verbands oder der Instanz, mit dem Grund, den du gesehen hast.
- **Räum die Unterlagen Ausgeschiedener auf.** Ein neuer Schalter auf der Dokumentenseite zeigt nur Dokumente von Personen, die alle ausgeschieden sind, archiviert oder gelöscht. Hak einzelne an oder alle, die der Filter findet, und lösch sie nach einer einzigen Rückfrage in einem Zug.
- **Der Verband sieht Dokumente so wie die Wache.** Auf der Seite einer Person im Verband sind Dokumente Kacheln, die du öffnest und in der Vorschau ansiehst, wie an der Wache. Ein Dokument, das du dort ablegst, nennt dich als die Person, die es abgelegt hat.

### Sicherheit

- **Einträge für benannte Personen bleiben bei ihnen.** Ein Formular, Termin, Blog-Beitrag oder Quiz für eine Liste benannter Mitglieder war für die ganze Wache sichtbar. Jetzt sehen ihn nur die benannten Personen und wer ihn verwaltet.
- **Verborgene Termine bleiben verborgen.** Ein Mitglied konnte Angaben, Anmeldungen und Kommentare eines verborgenen Termins direkt über seine Nummer abrufen, und die Anmeldezahlen zählten verborgene Termine mit. Ember antwortet jetzt, als gäbe es den Termin nicht, außer das Mitglied darf ihn sehen oder bearbeiten.
- **Partner auf derselben Instanz sehen nur Geteiltes.** Eine Partnerwache auf derselben Instanz konnte Quizze, Prüfprotokolle und Wiki-Artikel öffnen und Kommentare lesen und schreiben, die nie mit ihr geteilt wurden. Jetzt sieht sie genau das, was ein Partner auf einer anderen Instanz sieht.
- **Partner bleiben auf dem geteilten Board.** Eine Partnerwache, die ein geteiltes Board bearbeiten durfte, konnte Checklisten ändern, Tickets verschieben und Labels auf anderen Boards derselben Instanz vergeben. Ihre Änderungen bleiben jetzt auf dem Board, das mit ihr geteilt ist.
- **Benachrichtigungen erreichen nur die Richtigen.** In manchen Fällen ging die Benachrichtigung über ein neues Formular, einen Termin oder einen Blog-Beitrag an Mitglieder außerhalb des Kreises und zeigte den Titel. Jetzt erreicht sie nur, wer den Eintrag öffnen darf.
- **Die Aufnahme in eine Gruppe verlangt ihre Rechte.** Wer Gruppen verwalten durfte, konnte jeden, auch sich selbst, in eine Gruppe aufnehmen, deren Berechtigungen er nicht hatte. Jetzt braucht das jede Berechtigung, die die Gruppe vergibt, und wo sie welche vergibt, eine frische Bestätigung.
- **Gruppen nehmen nur Mitglieder ihrer Wache.** Die Mitgliederliste einer Gruppe nahm Personen anderer Wachen an, wenn sie direkt an den Server geschickt wurden. Ember lehnt sie jetzt ab.
- **Registrierungscodes bleiben bei ihrer Wache.** Eine Instanzadministration, die in einer Wache arbeitete, konnte Registrierungscodes einer anderen Wache über ihre Nummer öffnen, löschen und ihre Gruppen ändern. Ein Code ist jetzt nur aus seiner eigenen Wache erreichbar.
- **Entwürfe der Instanz bleiben bei der Instanzadministration.** Wer in einer Wache Neuigkeiten verwaltet, sah Entwürfe der Instanz schon vor ihrer Veröffentlichung. Jetzt bleiben sie bei der Instanzadministration, bis sie erscheinen.
- **Wachen-Bewerbungen brauchen die Bestätigungsmail.** Die Bewerbung um eine neue Wache antwortete mit dem Code, der die Adresse bestätigt, sodass es auch ohne die Mail ging. Der Code kommt jetzt nur noch mit dieser Mail.
- **Kommentare entfernt nur die eigene Wache.** Wer Neuigkeiten verwaltet, konnte einen Kommentar zu einer Neuigkeit einer anderen Wache über seine Nummer entfernen. Das geht jetzt nur noch aus der eigenen Wache, bei Neuigkeiten der Instanz aus der Wache, in der der Kommentar geschrieben wurde.
- **Dateien einer Wache gibt es nur für Mitglieder.** Wer auf der Instanz angemeldet war, konnte Mediendateien, Bilder und Dokumente einer Wache öffnen, ihre Medien auflisten und ihren Verbund sehen, indem er einfach die Wache angab. Jetzt antwortet Ember darauf nur Mitgliedern der Wache.
- **Einrichtungscodes für Authenticator-Apps gelten nur einmal.** Der Code, mit dem du eine neue Authenticator-App bestätigt hast, galt innerhalb seiner kurzen Gültigkeit noch für die nächste Anmeldung oder Bestätigung. Jetzt ist er verbraucht, sobald die App eingerichtet ist.
- **Vorlagen anderer Wachen lassen sich nicht übernehmen.** Wer Termine anlegen durfte, konnte die Anmeldefragen aus der Terminvorlage einer anderen Wache übernehmen, indem er ihre Nummer angab. Jetzt zählen nur die eigenen Vorlagen, und mit einer fremden entsteht kein Termin.
- **Schritte bleiben in ihrem Ablauf.** Wer an einem Ablauf arbeiten durfte, konnte Schritte jedes anderen Ablaufs abhaken, bearbeiten, löschen oder mit einer Notiz versehen, auch in anderen Wachen und bei Ablaufvorlagen. Ein Schritt ist jetzt nur noch über seinen eigenen Ablauf oder seine Vorlage erreichbar.
- **Notizen an Schritten schreibt nur, wer Abläufe führt.** Jedes Mitglied der Wache konnte die Notiz an einem Schritt jedes Ablaufs schreiben oder ersetzen, indem es sie direkt an den Server schickte. Jetzt geht das nur noch für die, die Abläufe führen, wie es die Seite des Ablaufs schon zeigte.
- **Profile bleiben bei denen, die sie sehen dürfen.** Jedes Mitglied der Wache konnte die Profilangaben anderer Mitglieder lesen und überschreiben, indem es die Anfrage direkt an den Server schickte. Jetzt dürfen das nur das Mitglied selbst, seine Erziehungsberechtigten und wer Mitglieder ansehen oder bearbeiten darf.
- **Fragen des Verbands achten ihre Sperren.** Eine Wache konnte Profilfragen ihres Verbands an deren Sperren vorbei beantworten, auch solche, die ihr vorenthalten oder dem Mitglied gar nicht gestellt waren. Auch die Mitgliederverwaltung des Verbands konnte Fragen jeder Wache beantworten, und jetzt durchläuft jede Antwort dieselben Sperren wie bei den eigenen Fragen der Wache.
- **Eine entfernte Gruppe öffnet keine Inhalte mehr.** Ein Termin, eine Vorlage, eine Neuigkeit, ein Formular, ein Quiz oder ein Wiki-Eintrag nur für eine Gruppe wurde für die ganze Wache sichtbar, sobald die Gruppe entfernt oder zum Tag wurde. Jetzt geht beides erst, wenn nichts mehr auf die Gruppe beschränkt ist.
- **Instanzspeicher wird geprüft wie bei Wachen.** Beim Speichern lehnt die Instanz jetzt unvollständige Zugangsdaten und Adressen ab, die sie nicht erreichen darf. Liegen deine Dateien auf einem Server in deinem eigenen Netz, setz vor dem Umstellen `federation.allowPrivateHosts`.
- **Verborgene Unterlagen der Wache bleiben verborgen.** Ein verborgenes Dokument, das niemanden nennt, konnte jeder öffnen, der die eigenen Unterlagen der Wache lesen darf, sofern er seine Nummer kannte. Jetzt öffnet es nur, wer Mitgliederdokumente einsehen darf.
- **Tags und Aufbewahrung bleiben bei der Dokumentenverwaltung.** Wer nur Dokumente für sich selbst hochladen darf, konnte der Wache neue Tags anlegen und ein Dokument über die Mitgliedschaft hinaus aufbewahren lassen. Beides bleibt jetzt bei denen, die Dokumente verwalten.
- **Dateien zählen als das, was sie sind.** Ein Dokument konnte sich als Bild oder PDF ausgeben und etwas anderes sein, und wurde dann als solches angezeigt. Jetzt entscheidet der Inhalt, und eine Datei, deren Name etwas anderes behauptet, wird abgewiesen.
- **Eine Gruppe entfernen braucht ihre Rechte.** Wer Gruppen verwalten durfte, konnte eine Gruppe löschen oder in einen Tag umwandeln und so allen darin Berechtigungen nehmen, die er selbst nicht hatte. Jetzt brauchst du dafür jede Berechtigung der Gruppe, und bei einer Gruppe mit Berechtigungen bestätigst du dich noch einmal kurz.

### Änderungen

- **Einstellungen einer Frage stehen im Menü.** Alles außer „Pflichtfeld“ steckt jetzt im Menü in der Ecke jeder Frage. Geänderte Einstellungen stehen als Etiketten unter dem Titel, und ein Knopf am Ende jeder Seite fügt neue Fragen hinzu.
- **Nach dem Absenden kommt eine Bestätigung.** Schickst du ein Formular der Wache ab, sagt dir eine Seite, dass es angekommen ist. Von dort geht es zurück zu den Umfragen und, wo erlaubt, zum Ändern deiner Antwort.
- **Die Vorlage Jugendflamme bietet „Keine“ an.** Die Schnellvorlage legt jetzt ein Auswahlfeld mit „Keine“ und den drei Stufen an, sodass jedes Mitglied genau eine Stufe hat. Das Datum, an dem die Stufe erreicht wurde, kommt wie bisher mit.
- **Die Mindestanzahl zählt Tage vor jedem Termin.** Ein Termin mit einer Mindestanzahl an Anmeldungen legt jetzt fest, wie viele Tage vor jedem Termin sie erreicht sein muss, statt einen festen Tag zu nennen. Einmalige Termine behalten ihre Frist, bei wiederkehrenden entfällt die alte und muss im Editor neu gesetzt werden.
- **Das Backend bekommt dreißig Sekunden zum Herunterfahren.** Die mitgelieferten Compose-Dateien und das Installationsskript geben dem Backend-Container eine `stop_grace_period` von 30 Sekunden, um laufende Anfragen zu beenden und gepufferte Statistiken und Protokollzeilen zu speichern. Nutzt du eigene Compose-Dateien, setz beim Backend dasselbe.
- **Die E-Mail des Verbands schaltest du selbst ein.** Die Benachrichtigungs-E-Mail des Verbands erreicht nur noch, wer sie unter Verband → Benachrichtigungen einschaltet, wo sie anfangs aus ist. Bis dahin bleiben seine Benachrichtigungen in der App.
- **Neue Wachen sind öffentlich gelistet.** Eine ab jetzt gegründete Wache erscheint von Anfang an auf der öffentlichen Discovery-Seite und bei anderen Ember-Instanzen. Abschalten lässt sich das bei der Einrichtung oder unter Föderation → Einstellungen, und bestehende, importierte und übertragene Wachen behalten ihre Einstellung.
- **Eine frische Installation fragt nach ihrer ersten Wache.** Eine neue Instanz legt keine Wache namens „default“ mehr an. Nach der ersten Anmeldung benennt und gründet der Administrator die erste Wache, wird ihr Verwalter und landet direkt in ihrer Einrichtung, während bestehende Instanzen ihre Wachen behalten.
- **Deine Kommentare in einem Abschnitt der Datenauskunft.** Forderst du deine Daten an, stehen alle deine Kommentare jetzt in einem Abschnitt, ob zu Terminen, Neuigkeiten, Wiki-Dateien oder Board-Tickets. Jeder nennt, wozu er geschrieben wurde, und Benachrichtigungen, die auf einen Kommentar zeigen, öffnen ihn weiterhin.
- **Board-Kommentare gehören ihren Verfassern.** Einen Kommentar zu einem Ticket ändert jetzt nur, wer ihn geschrieben hat, und entfernen kann ihn nur diese Person oder ein Board-Verwalter, nicht mehr jeder, der das Ticket bearbeiten darf. Leer speichern lässt sich ein Kommentar nicht mehr.
- **Klarere Hinweise an beobachteten Tickets.** Der Hinweis auf einen neuen Kommentar an einem Ticket, das du beobachtest, nennt in deiner Sprache, wer ihn geschrieben hat. Er folgt deiner Einstellung für Kommentare statt der für Ticket-Updates, und über den eigenen Kommentar wird niemand mehr benachrichtigt.
- **Zahlenfelder nehmen ganze Zahlen.** Zahlenfelder von Anwesenheitslisten, Wartelisten, Ausrüstung und Board-Tickets nehmen ganze Zahlen, wie ihre Eingabefelder es schon nahelegten. Ein Ausrüstungsfeld mit einer Schrittweite unter eins nimmt weiter Kommazahlen, und gespeicherte Zahlen bleiben, wie sie sind.
- **Mitgliederfelder halten sich an ihre Grenzen.** Ein Mitgliederfeld einer Anwesenheitsliste oder eines Termins, das auf eine Gruppe, einen Mitgliedstyp oder ein Tag beschränkt ist, lehnt jetzt jeden außerhalb davon ab. Das gilt beim Speichern von Liste, Termin oder Vorlage und beim Beantworten einer Anmeldefrage, und wen das Feld schon nennt, der bleibt.
### Fehlerbehebungen

- **Späte Absagen erreichen die offene Anwesenheitsliste.** Wer nach dem Öffnen der Liste absagte oder seinen Platz zurückgab, stand darauf weiter als offen, bis jemand die Liste abglich. Jetzt steht die Person sofort als abgesagt da, während von Hand Markierte und geschlossene Listen bleiben, wie sie sind.
- **Zweimal gespeicherter Speicher frisst keine Dateien mehr.** Hast du einen Speicher angewendet, auf dem du schon warst, etwa nur um das Passwort zu ändern, wurden die Dateien dort gelöscht. Ember erkennt jetzt, dass es derselbe Ort ist, und lässt deine Dateien in Ruhe.
- **Überschriften werden keine Spalten mehr.** Überschriften und Abstände des Profilformulars erschienen als leere Spalten in der Mitgliederliste und ihrem Export. Jetzt fallen sie weg.
- **Eigene Kommentare auf Partner-Boards lassen sich ändern.** Den eigenen Kommentar auf einem Board zu ändern oder zu entfernen, das eine Partnerwache mit dir teilt, bewirkte nichts. Jetzt klappt es, und zwischen zwei Instanzen geteilte Boards ruhen, bis beide diese Version haben.
- **Löschen-Knöpfe an Kommentaren erscheinen bei den Richtigen.** Wer Neuigkeiten oder das Wiki verwaltet, sah den Knopf zum Entfernen fremder Kommentare nicht, während Terminverwalter ihn dort sahen, wo das Entfernen dann scheiterte. Jetzt erscheint er für die Person, die den Kommentar geschrieben hat, und für alle, die diese Inhalte verwalten.
- **Adressen in Gruppen- und Tag-Listen stehen richtig.** In den Mitgliederlisten von Gruppen und Tags stand die Adresse von jemandem mit Profilbild neben dem Bild statt unter dem Namen. Jetzt steht sie bei allen unter dem Namen.
- **Umsortierte Optionen lassen Antworten in Ruhe.** Wurden die Optionen einer Frage mit Antworten umsortiert oder umbenannt, konnte sich ändern, was diese Antworten aussagten. Antworten bleiben jetzt bei der gewählten Option, und das Entfernen einer gewählten Option fragt vorher nach.
- **Freiwillige Fragen blockieren das Absenden nicht mehr.** In manchen Fällen wurde ein Formular abgelehnt, wenn eine freiwillige Auswahl- oder Bewertungsfrage leer blieb. Dasselbe galt für eine Auswahl nur mit eigener Antwort, und beides wird jetzt angenommen.
- **Das Mischen von Fragen und Optionen wirkt.** Die Einstellungen zum Mischen wurden gespeichert, beim Ausfüllen aber ignoriert. Jetzt wird gemischt, Fragen jeweils innerhalb ihrer Seite.
- **Exportierte Antworten sind gut lesbar.** Tabelle und PDF der Antworten zeigten jede Antwort so, wie sie gespeichert ist. Jetzt steht dort der Text der gewählten Optionen.
- **Fragen an eine Gruppe stehen in jedem Profil.** In manchen Fällen fehlte eine Frage, die eine Wache nur einer Gruppe stellt, wenn auch der Verband eigene Fragen stellt. Solche Fragen erscheinen jetzt immer und lassen sich beantworten.
- **Abgemeldete Mitglieder lassen sich wieder eintragen.** Nach einer Abmeldung oder Absage bot die Liste zum Eintragen in einen Termin das Mitglied nicht mehr an. Jetzt können die Verantwortlichen es wieder eintragen.
- **Abgemeldete Mitglieder können sich überall neu anmelden.** In manchen Fällen stand ein Mitglied, das seinen Platz zurückgegeben hatte, weiter als abgemeldet da, ohne sich neu anmelden zu können. In der Liste der kommenden Termine und auf der Seite des Termins geht das jetzt.
- **Wiederkehrende Termine halten ihre Tage auseinander.** Bei einem wiederkehrenden Termin konnte der Reiter Anmeldungen Antworten, Listen und Zahlen von einem anderen Tag als dem geöffneten zeigen, und das Abmelden konnte den Platz an diesem anderen Tag aufgeben. Jetzt bleibt der Reiter beim geöffneten Tag.
- **Terminlinks auf Seiten führen ans Ziel.** Der Knopf eines Terminblocks auf einer Seite der Wache führte auf eine Seite, die es nicht gibt. Jetzt öffnet er den öffentlichen Kalender der Wache.
- **Terminblöcke auf Seiten behalten ihren Termin.** Beim Speichern einer Seite der Wache konnte der gewählte Termin eines Terminblocks verloren gehen und erschien dann als nicht mehr verfügbar. Die Blöcke behalten jetzt, was du gewählt hast.
- **Die Liste der Profilfelder passt auf mittlere Bildschirme.** Zwischen Telefon und breitem Desktop drückte die Liste die Namen zusammen, bis sich die Zeilen überlagerten. Jetzt wechselt sie zu Kacheln, sobald die Tabelle nicht mehr passt.
- **Anmelden nur, wo es erlaubt ist.** Einem Mitglied konnte die Anmeldung zu einem Termin für einen Teil der Wache angeboten werden, und erst nach dem Drücken kam die Absage. Jetzt sehen den Knopf nur die, die sich anmelden dürfen, und alle anderen einen kurzen Hinweis, warum.
- **Wiederkehrende Termine nennen ihren echten Rhythmus.** Die Seite eines monatlichen, vierteljährlichen oder jährlichen Termins nannte ihn wöchentlich. Jetzt steht dort, wie oft er sich wiederholt, wie in der Liste der Termine.
- **Eine Absage trifft nicht mehr die ganze Serie.** Wurde ein wiederkehrender Termin abgesagt, von Hand oder wegen zu weniger Anmeldungen, fielen alle Termine aus, und alle Angemeldeten wurden benachrichtigt. Jetzt fällt nur der betroffene Termin aus, und die automatische Prüfung zählt nur seine Anmeldungen.
- **Der öffentliche Kalender kennzeichnet abgesagte Termine.** Die öffentliche Seite der Wache und ihr öffentlicher Kalender-Feed zeigten abgesagte Termine, als wäre nichts gewesen. Jetzt sind sie als abgesagt gekennzeichnet.
- **Statistiken brechen nach einem Neustart nicht mehr ein.** Jeder Neustart verlor bis zu einer Stunde Seitenaufrufe, dazu die jüngsten Zahlen zum Datenverkehr, Antwortzeiten und Protokollzeilen. Jetzt wird alles gespeichert, bevor der Server anhält, sofern er die Zeit zum Herunterfahren bekommt.
- **WebP-Bilder lassen sich überall hochladen.** Ein WebP-Bild als Logo einer Wache oder als Ordnersymbol oder Bild im Wiki hochzuladen schlug fehl und entfernte manchmal das Bild, das vorher da war. WebP wird jetzt wie jedes andere Format angenommen.
- **Der Instanzspeicher hält, wenn eine Wache zurückkommt.** Zog eine Wache mit eigenem Speicher zurück auf den Speicher der Instanz, konnte danach jede Datei der Instanz auf SFTP, SMB oder S3 fehlschlagen, bis Ember neu startete. Der Speicher der Instanz bleibt jetzt offen.
- **Anwesenheitslisten merken sich, für wen sie sind.** Eine Liste für ausgewählte Mitgliedstypen und Gruppen zeigte trotzdem die Gruppen der Vorlage, und der Abgleich mit dem Termin trug deren Mitglieder nach. Jetzt behält die Liste ihre Personen, auf dem Bildschirm, beim erneuten Abgleich und im PDF.
- **Der Anwesenheitsbericht nennt Typen richtig.** Die Überschrift des Berichts und seiner Vorschau zeigte einen Mitgliedstyp als „TEAM“ oder „GUARDIAN“. Jetzt stehen dort dieselben Namen wie auf dem Rest der Seite.
- **SMB-Speicher erholt sich nach einem Verbindungsabbruch.** In manchen Fällen schlug nach einem Abbruch der Verbindung zu einem SMB-Server jede Datei darauf fehl, bis Ember neu startete. Ember meldet sich jetzt auf der neuen Verbindung wieder an.
- **Geänderte Speicher-Einstellungen schließen alte Verbindungen.** Jede Änderung am Speicher einer Wache oder eines Verbunds ließ die Verbindung zum alten Server bis zum Neustart offen. Ersetzte Verbindungen werden jetzt geschlossen.
- **Löschen auf SFTP-Speicher scheitert nicht mehr.** In manchen Fällen gab das Entfernen einer Datei, die auf dem SFTP-Speicher schon fehlte, einen Fehler zurück. Jetzt gelingt es still, wie auf jedem anderen Speicher.
- **Animierte Logos standen still.** Ein animiertes GIF als Logo einer Wache und die Kachel eines GIFs im Wiki zeigten nur das erste Bild. Jetzt bewegen sie sich.
- **Die Seite unter `/pitch` passt gleich aufs Handy.** In manchen Fällen blieben die Anwesenheitsknöpfe ihres Beispiels auf dem Handy in Desktop-Breite, bis du den Bildschirm drehtest oder die Größe ändertest. Jetzt wechselt die Seite gleich nach dem Laden ins Handy-Layout.
- **Eine unlesbare Nachricht stoppt keinen Postfach-Import mehr.** In manchen Fällen scheiterte der Import bei jedem Durchlauf an einer Nachricht, die der Mailserver nicht herausgab, bis das Postfach ausgesetzt wurde. Ember überspringt diese Nachricht jetzt und importiert den Rest.
- **Verschwundene Seiten sagen es auch.** Wer eine öffentliche Seite, einen Wiki-Artikel oder einen geteilten Link öffnete, den es nicht mehr gab, sah einen Fehler ohne Erklärung. Jetzt sagt die Seite, dass es sie nicht mehr gibt, und der Link-Dialog warnt, solange die öffentlichen Seiten der Wache ausgeschaltet sind.
- **Neuigkeitenblöcke behalten ihre Neuigkeit.** Beim Speichern einer Seite oder eines Artikels ging die gewählte Neuigkeit eines Neuigkeitenblocks verloren. Der Block behält sie jetzt und zeigt sie aktuell, und die Suche bietet nur Neuigkeiten an, die alle Leser sehen dürfen.
- **Das Teilen eines Boards mit Partnern speichert wieder.** Das Speichern, welche Partnerwachen ein Board sehen, schlug fehl, sodass sich kein Board teilen ließ. Jetzt klappt es, und du wählst für jeden Partner, welcher Mitgliedstyp dort das Board sehen darf.
- **Verbandsmails in richtiger Sprache und Zeit.** Die Benachrichtigungs-E-Mail des Verbands war immer auf Englisch, und ihre Versandzeiten wurden in UTC statt in Ortszeit gelesen. Jetzt richtet sie sich nach Sprache und Zeitzone der Heimat-Wache des Verbands.
- **Benachrichtigungen kommen nicht mehr doppelt.** In manchen Fällen erschien dieselbe Benachrichtigung zweimal, wenn zwei Personen im selben Moment dasselbe taten. Jetzt erscheint sie einmal.
- **Frühere Betreuende werden nicht mehr benachrichtigt.** Wer eine Wache verlassen hatte, bekam teils weiter Benachrichtigungen und E-Mails über die Mitglieder, die er früher betreut hat. Damit ist Schluss.
- **Quizze von Partnern auf anderen Instanzen öffnen sich.** Ein Quiz, das eine Partnerwache auf einer anderen Instanz geteilt hat, ließ sich nicht öffnen, sobald es Fragen enthielt. Jetzt öffnet es sich mit allen Fragen.
- **Benannte Partner auf derselben Instanz bekommen Geteiltes.** Eine Neuigkeit oder ein Termin, die mit benannten Partnerwachen geteilt waren, erreichten einen benannten Partner auf derselben Instanz nie. Jetzt erreichen sie jeden Partner, den sie nennen.
- **Kommentare bei Partnern auf anderen Instanzen klappen.** Du konntest deinen eigenen Kommentar zu einer Neuigkeit oder einem Termin einer Partnerwache auf einer anderen Instanz nicht löschen. Manchmal ging auch kein neuer Kommentar zu so einem Termin, und jetzt klappt beides.
- **Eigene Boards erscheinen nicht mehr beim Partner.** In manchen Fällen tauchte ein Board, das eine Wache mit einem Partner auf derselben Instanz teilt, in ihrer eigenen Liste der Boards dieses Partners auf. Die Liste zeigt jetzt nur, was der Partner teilt.
- **Partner-Tickets anderer Instanzen lassen sich abbestellen.** Wer ein Ticket auf einem Board einer Partnerwache auf einer anderen Instanz beobachtete, konnte das Beobachten nicht beenden. Jetzt geht das.
- **Links auf der Netzwerkkarte finden die Wache.** Auf der Karte des Discovery-Netzes öffnete der Link zu einer Wache einer anderen Instanz eine Seite, die es nicht gab. Sobald beide Instanzen diese Version nutzen, öffnet er die öffentliche Seite der Wache.
- **Die Fußzeile verlinkt Discovery auch auf dem Handy.** Auf schmalen Bildschirmen fehlte in der Fußzeile der Link zum Wachen-Verzeichnis. Jetzt steht er bei jeder Breite da.
- **Gruppen eines Mitglieds ändern klappt sicher.** Die Auswahl von Gruppen auf der Bearbeitungsseite eines Mitglieds scheiterte für alle, die Mitglieder bearbeiten, aber keine Gruppen verwalten durften. Zwei Personen konnten sich außerdem gleichzeitige Änderungen an einer Gruppe zunichtemachen, darum speichert die Seite jetzt nur die Gruppen dieses einen Mitglieds.
- **Teilen mit ausgewählten Partnerwachen klappt.** Ein Termin oder eine Neuigkeit, die nur mit einigen Partnerwachen geteilt war, ließ sich nicht speichern. Jetzt wird mit den Partnern gespeichert, die du gewählt hast.
- **Termine bearbeiten lässt Fragen öffentlich.** Beim Speichern eines Termins wurde bei jeder seiner Fragen „öffentlich“ ausgeschaltet, sodass sie aus dem öffentlichen Kalender verschwanden. Fragen behalten jetzt ihre Einstellung.
- **Anwesenheitsfelder ohne Vorgabe bleiben ohne.** Im Vorlagen-Editor sah ein Anwesenheitsfeld ohne Vorgabewert aus, als hätte es einen, und beim Speichern landete ein leerer Text, eine Null oder ein Nein darin. Jetzt zeigt der Editor solche Felder ohne Vorgabe.
- **Der Anmeldeüberblick zeigt die Teilnehmergrenze.** Der Überblick über die Anmeldungen zeigte nicht, wie viele Plätze ein Termin hat. Jetzt steht die Grenze neben dem Termin.
- **Eingeschränkte Neuigkeiten zeigen ihr Schloss.** Eine Neuigkeit für nur einen Teil der Wache erschien ohne das Schloss, das sie kennzeichnet. Jetzt ist es in der Liste der Neuigkeiten und beim Eintrag zu sehen.
- **Neue Terminkategorien behalten alle Einstellungen.** Eine neue Kategorie wurde ohne die Zahl der angezeigten Termine und ohne „öffentlich“ gespeichert, sodass du beides danach noch einmal setzen musstest. Jetzt wird beides mit der Kategorie gespeichert.
- **Eine bearbeitete Terminkategorie behält ihren Platz.** Beim Speichern rückte eine Kategorie an den Anfang der Liste. Jetzt bleibt sie, wo sie war.
- **Eingeschränkte Formulare zeigen ihr Schloss.** In der Liste zum Ausfüllen erschien ein Formular für nur einen Teil der Wache ohne sein Schloss. Jetzt ist es da.
- **Gespeicherte Filter der Mitgliederliste bleiben.** Die aktuellen Filter der Mitgliederliste unter einem Namen zu speichern endete mit einem Fehler, und nichts blieb erhalten. Jetzt wird der Filter gespeichert und wie jeder andere wieder angeboten.
- **Schritte einer Ablaufvorlage können aufeinander warten.** Eine Abhängigkeit zwischen zwei Schritten einer Ablaufvorlage hinzuzufügen oder zu entfernen wurde abgelehnt, also hatte keine Vorlage je eine. Jetzt werden Abhängigkeiten gespeichert, und jeder Schritt zeigt, worauf er wartet.
- **Schritte einer Ablaufvorlage behalten ihre Reihenfolge.** Neue oder bearbeitete Schritte landeten alle am Anfang der Liste, sodass sich die Reihenfolge nach jeder Änderung verschieben konnte. Ein neuer Schritt kommt jetzt ans Ende, ein bearbeiteter bleibt an seinem Platz.
- **Eine geleerte Notiz bleibt leer.** Wer die Notiz eines Schritts in einem Ablauf leerte, behielt die alte Notiz. Jetzt entfernt Leeren sie.
- **KI-Quizfragen kommen vollständig an.** Neue Fragen für einen Katalog oder eine neue Fassung ausgewählter Fragen von der KI anzufordern schlug fehl, und Fragen, die doch ankamen, hatten keine Antworten. Jetzt kommen erzeugte Fragen an und werden vollständig gespeichert.
- **Freitext- und Bildfragen zeigen ihre Antworten.** In einem nur lesbar geöffneten Quizkatalog zeigten Freitext- und Bildfragen keine richtige Antwort. Jetzt nennen sie wie alle anderen die akzeptierten Antworten.
- **Korrekturen sind im Verlauf gekennzeichnet.** Stellte eine Kontrolle richtig, wer einen Gegenstand hat, sah das im Verlauf aus wie eine gewöhnliche Rückgabe. Jetzt sind solche Einträge als Korrektur markiert.
- **Ember nennt die Art, die im Weg steht.** Ließ sich eine Sammlung nicht in einen Bestand umstellen, weil darin noch Arten angelegt sind, erschien der Grund als unlesbarer Text. Jetzt steht dort die Art, die das verhindert.
- **Der Inventarschalter des Verbands zeigt seinen Stand.** Die Einstellung, dass ein Verband seine Ausrüstung in Ember führt, wirkte beim Öffnen der Inventareinstellungen immer ausgeschaltet. Jetzt zeigt sie, wie sie wirklich steht.
- **Abgewiesene unsignierte Mails nennen ihren Grund.** Im Importprotokoll eines Postfachs, das nur signierte Mails ablegt, erschien eine wegen fehlender, fremder oder ungültiger Signatur abgewiesene Nachricht als unlesbarer Text. Jetzt steht der Grund dort.
- **Die öffentliche URL-Kennung lässt sich entfernen.** Wer in den Föderations-Einstellungen die URL-Kennung der öffentlichen Seite leerte, behielt die alte, und nach dem Neuladen war sie wieder da. Jetzt entfernt ein leeres Feld sie.
- **Lesezeichen auf föderierten Boards erscheinen sofort.** Ein Lesezeichen auf der Seite der föderierten Boards blieb bis zum Neuladen unmarkiert, und ein zweiter Klick wollte das Board noch einmal merken. Jetzt erscheint das Lesezeichen sofort, und ein zweiter Klick entfernt es.
- **Erziehungsberechtigte sehen Ausrüstung wie das Mitglied.** Unter „Mein Inventar“ fehlten bei der Ausrüstung betreuter Mitglieder der Schritt eines Tauschs und das Bild, und für Stücke unterwegs gab es weiter Tausch und Verlustmeldung. Jetzt erscheint sie genau so, wie das Mitglied sie sieht.
- **Profilantworten landen bei der richtigen Frage.** Stellten eine Wache und ihr Verband je eine Frage unter derselben Nummer, konnte die eigene Profilseite eines Mitglieds eine Antwort für beide zeigen. Sie speicherte sie nur bei der Frage der Wache, und jetzt behält jede Frage ihre eigene.
- **Konten ohne Adresse zeigen kein „(null)“ mehr.** Ein Konto mit Benutzernamen und ohne E-Mail-Adresse erschien in der Kontoauswahl der Administration und beim Zurücksetzen des zweiten Faktors mit „(null)“ hinter dem Namen. Jetzt steht dort nur der Name.
- **Hilfebeispiele haken jede vergebene Berechtigung an.** In den Beispielen der Hilfe zu Mitglieder-, Mitgliedstyp- und Gruppenberechtigungen standen die Rechte für Anwesenheits- und Terminverwaltung als nicht vergeben da. Jetzt sind sie angehakt.
- **Die Mitgliederliste markiert unvollständige Profile.** Wer eine Pflichtfrage im Profil offen gelassen hatte, sah in der Mitgliederliste aus wie alle anderen. Jetzt steht „Unvollständig“ neben dem Namen, beurteilt wie bei der Erinnerung im eigenen Profil.
- **Ehemalige Mitglieder lassen keine Listen mehr scheitern.** In manchen Fällen ließ ein ehemaliges Mitglied ohne Konto Berichte, Exporte, Inventarprüfungen, die Liste betreuter Mitglieder oder den Änderungsverlauf eines Profils mit einem Fehler abbrechen. Jetzt erscheint es mit Namen oder Nummer.
- **„Im Browser öffnen“ im Feed führt ans Ziel.** In manchen Fällen trug eine Benachrichtigung im RSS- oder Atom-Feed einen Knopf „Im Browser öffnen“ ohne Ziel. Jetzt öffnet er dieselbe Seite wie der Eintrag.
- **Anmeldefragen aus der Vorlage kommen mit.** Ein Termin aus einer Terminvorlage übernahm deren Anmeldefragen nicht und manchmal stattdessen die einer anderen Vorlage. Jetzt übernimmt er die Fragen seiner eigenen Vorlage, und sie stehen schon vor dem Speichern im Editor.
- **Eine doppelte Meldung von Sweego sendet nicht doppelt.** In manchen Fällen, wenn Sweego dieselbe Meldung über eine unzustellbare Mail erneut schickte, ging die Mail zweimal hinaus oder wechselte zu früh zum nächsten Anbieter. Jetzt erkennt Ember die Wiederholung und zählt sie nur einmal.
- **Antworten an den Verband werden geprüft.** Eine Antwort auf eine Profilfrage des Verbands wurde gespeichert, was immer sie enthielt, etwa eine Auswahl, die es nicht gibt, oder ein Datum, das keines ist. Jetzt wird sie geprüft wie Antworten auf die eigenen Fragen der Wache.
- **Der Mitgliederimport lässt unpassende Antworten weg.** Beim Import wurde eine Zelle wie eine unbekannte Auswahl, ein Tag, der keiner ist, oder „vielleicht“ unter einer Ja/Nein-Frage so gespeichert, wie sie dastand. Jetzt wird sie ausgelassen, und Vorschau und Ergebnis nennen ihre Zeile.
- **Board-Felder weisen unpassende Werte ab.** Ein eigenes Feld an einem Board-Ticket ließ sich mit einem Datum, das keines ist, oder einer Auswahl speichern, die das Feld nicht anbietet. Jetzt werden solche Werte beim Speichern abgewiesen.
- **Datumsfelder von Anwesenheitslisten können mit heute beginnen.** Ein Feld einer Anwesenheitsvorlage, das mit dem heutigen Datum beginnen soll, wurde beim Speichern abgewiesen, weil es angeblich ein Datum erwarte. Jetzt lässt es sich wieder speichern, und neue Listen beginnen mit dem Tag, an dem sie angelegt werden.
- **Austragen aus einem Feld klappt immer.** Wer sich in ein Feld eines Termins eingetragen hatte, das auf eine Gruppe, einen Mitgliedstyp oder ein Tag beschränkt ist, wurde nach dem Austritt beim Austragen abgewiesen. Jetzt klappt das Austragen immer.
- **Ja-Antworten erscheinen als Ja.** In manchen Fällen erschien ein Ja aus einer älteren Version bei den Feldern eines Termins und in Antworten auf Anmeldefragen als Nein. Jetzt steht dort überall Ja.
- **Board-Zahlenfelder können Null halten.** Eine 0 in einem Zahlenfeld eines Tickets leerte das Feld. Jetzt bleibt die Null stehen.
- **Anmeldeantworten halten sich an die Grenzen.** In manchen Fällen nahm das Feld einer Zahlenfrage Zahlen außerhalb ihres Bereichs, und eine auf eine Gruppe oder ein Tag beschränkte Mitgliederfrage bot alle an, sodass das Speichern scheiterte. Jetzt hält sich das Feld an den Bereich und bietet nur die Mitglieder an, die die Frage annimmt.
- **Ausrüstung des Verbandslagers öffnet sich an der Wache.** Ein Stück, das der Verband in seinem Lager führt und einer Wache geschickt hat, stand in ihren Listen und fand sich beim Scannen, aber beim Öffnen hieß es, es sei nicht vorhanden. Jetzt öffnet, gibt aus und meldet die Wache es wie jedes andere Stück.
- **Die Wahl eines Ablaufs übersteht doppeltes Speichern.** In manchen Fällen, wenn der Ablauf für ein Inventar an zwei Stellen gleichzeitig gewählt wurde, scheiterte eine davon mit dem Hinweis, der Eintrag sei schon vorhanden. Jetzt gehen beide durch, und der letzte gilt.
- **Ausrüstung eines Mitglieds passt zur Liste der Bewegungen.** Ein Stück mit laufendem Tausch oder laufender Rückgabe nannte den Schritt, auf den noch gewartet wurde, sodass eine Jacke als zurückgenommen galt, obwohl das Mitglied sie noch hatte, und ein offener Ersatz gar nichts zeigte. Jetzt zeigt jedes Stück den letzten erledigten Schritt und wer an der Reihe ist, wie die Liste der Bewegungen.
- **Die Schnellprüfung zeigt den letzten echten Schritt.** Bei einer Inventarprüfung zeigte ein Stück mit laufendem Tausch oder laufender Rückgabe den Schritt, auf den noch gewartet wurde, sodass eine Jacke in der Hand als zurückgenommen galt und ein offener Ersatz einen Tausch anbot, der dann scheiterte. Jetzt zeigt die Schnellprüfung den letzten erledigten Schritt und wer an der Reihe ist, wie die Liste der Bewegungen.
- **Der Test des Verbandsspeichers verrät keine Netzdetails mehr.** Ein fehlgeschlagener Speichertest beim Verband zeigte den genauen Grund, etwa eine abgewiesene Verbindung oder eine Zeitüberschreitung, und verriet so zu viel über das Netz hinter der Adresse. Jetzt antwortet er so allgemein wie der Test einer Wache, und den genauen Grund findest du im Protokoll der Instanz.
- **Dokumente Gelöschter wurden zu Unterlagen der Wache.** Hast du ein Mitglied oder sein Konto gelöscht, blieben seine Dokumente ohne Namen zurück, offen für alle, die die eigenen Unterlagen der Wache lesen dürfen. Jetzt geht der Rest mit dem Mitglied, und was du aufbewahrst, behält seinen Namen.
- **Uploads übergingen die Grenzen deiner Wache.** Dokumente, die du von Hand oder über den Verband hochlädst, und Dateien zu einer Verlustmeldung übergingen die Größengrenze für eine Datei und den Speicherplatz der Wache. Jetzt gelten beide, genau wie für Dokumente, die per Mail ankommen.
- **Abgeschaltete Dokumente blieben erreichbar.** Eine Wache mit abgeschalteter Dokumentenablage gab jedes Dokument und sein Vorschaubild weiter an alle, die die Adresse kannten. Aus heißt jetzt aus, auch für den Verband.
- **Archivieren über den Verband ist jetzt vollständig.** Ein über die Mitgliederverwaltung des Verbands archiviertes Mitglied behielt Anmeldung, Rollen, Erziehungsberechtigte, Gruppen, Tags, Dokumente und Profilangaben. Jetzt tut das Archivieren dort genau dasselbe wie an der Wache und wird in denselben Fällen abgelehnt, etwa wenn noch Ausrüstung ausgegeben ist.
- **Links in Mails des Verbands führen richtig.** Der Knopf in der Benachrichtigungsmail des Verbands öffnete eine Seite, die es nicht gibt, und die meisten Hinweise darin öffneten die Startseite einer Wache statt der Seite des Verbands, um die es ging. Sie öffnen jetzt die Seiten des Verbands, und zwar in dem Verband, um den es in der Mail geht.
- **Manche Hinweise führten ins Leere.** Ein Hinweis auf eine Leihanfrage, eine Speicherwarnung oder importierte Post, die noch abgelegt werden muss, tat in der App beim Öffnen nichts und öffnete aus einer Mail oder einem Feed die Startseite. Er öffnet jetzt die Leihanfrage, die Speicherseite und die Mitgliederdokumente.
- **Benachrichtigungseinstellungen ließen sich nicht mehr speichern.** Sobald der Schalter für Tausch-Anfragen auf der Seite der Benachrichtigungseinstellungen einmal betätigt war, scheiterte jede weitere Änderung auf dieser Seite beim Speichern. Der Schalter ist entfernt, da diese Hinweise längst zu Hinweisen über Bewegungen geworden sind, und die Seite speichert wieder.
- **Antworten der Wache und des Verbands gerieten durcheinander.** Hatten eine Frage der Wache und eine des Verbands dieselbe Nummer, zeigten manche Profilseiten die eine Antwort unter der anderen und speicherten sie bei der falschen Frage. Jetzt hält jede Profilseite die beiden auseinander.
- **Verbandsverwalter ohne Wache konnten kein Profil speichern.** Verwaltest du die Mitglieder des Verbands, gehörst aber zu keiner seiner Wachen, scheiterte das Speichern eines Profils. Jetzt klappt es, und der Änderungsverlauf nennt dich.
- **Feldverwalter kamen nicht an die Fragen des Verbands.** Durftest du die Profilfragen des Verbands bearbeiten, aber nicht seine Mitglieder sehen, blieb die Liste der Fragen für dich zu. Jetzt öffnet sie sich.
- **Dem Verband ließen sich keine Abstände hinzufügen.** Ein Abstand verlangte einen Namen, den das Formular gar nicht zeigte. Abstände werden jetzt von selbst durchnummeriert, wie an der Wache.
- **Archivieren löscht auch Antworten an den Verband.** Hast du ein Mitglied archiviert, blieben seine Antworten auf die Fragen des Verbands erhalten, auch wenn eine Frage nicht zum Behalten markiert war. Jetzt werden sie gelöscht wie die der Wache.
- **Neue Fragen der Wache behalten „Bei Archivierung behalten“.** Wer eine Frage mit diesem Haken anlegte, bekam sie ohne die Einstellung gespeichert. Jetzt bleibt der Haken von Anfang an.
- **Du erfährst, wenn der Verband dein Profil ändert.** Der Verband konnte dein Profil über seine Mitgliederseite ausfüllen, ohne dass du davon hörtest. Jetzt bekommst du dann eine Benachrichtigung.
- **Neue Zugangsdaten des Verbandsspeichers gingen ins Leere.** Hast du nur das Passwort des Verbandsspeichers geändert, wurden Dateien in manchen Fällen bis zum Neustart von Ember noch mit dem alten geschrieben. Die neuen Zugangsdaten gelten jetzt sofort.
- **Alte Einstellungen eines Verbandsspeichers wurden nie gelöscht.** Zeigte ein Verband mit seinem Speicher woandershin oder gab ihn auf, blieben die alten Einstellungen samt Zugangsdaten für immer liegen. Sie werden jetzt gelöscht, sobald keine Wache mehr Dateien darauf hat.
- **Speichern konnte den Verband ohne Speicher lassen.** In seltenen Fällen ließ ein Speichern, das auf halbem Weg scheiterte, den Verband ganz ohne eigenen Speicher zurück. Der bisherige Speicher bleibt jetzt, bis der neue gespeichert ist.
- **Ein abgelehnter Umzug erschien als begonnen.** Verschob ein Verband eine Wache, deren Dateien schon am richtigen Ort lagen, stand der Umzug im Verlauf der Wache trotzdem als begonnen. Jetzt stehen dort nur Umzüge, die wirklich beginnen.
- **Gruppen des Verbands speicherten halbe Änderungen.** Wurde ein Teil einer Änderung an einer Mitgliedergruppe des Verbands abgelehnt, blieben die Teile davor trotzdem gespeichert, etwa ein neuer Name mit den alten Mitgliedern. Jetzt wird eine Änderung ganz gespeichert oder gar nicht.
- **Eine gelöschte Gruppe des Verbands sagte niemandem Bescheid.** Wer in einer Mitgliedergruppe des Verbands war, verlor beim Löschen der Gruppe ihre Rechte ohne ein Wort. Jetzt kommt dieselbe Nachricht wie beim Herausnehmen aus einer Gruppe.
- **Verbandsgruppen boten Knöpfe, die nichts taten.** Wenn du die Mitgliedergruppen des Verbands nur ansehen darfst, wurden dir trotzdem Anlegen, Löschen und das Ändern der Mitglieder angeboten, und der Server lehnte dann ab. Diese Knöpfe sehen jetzt nur noch die Verwalter des Verbands.
- **Doppelte Gruppennamen endeten in einem allgemeinen Fehler.** Gruppennamen einer Wache behielten Leerzeichen am Rand, und eine zweite Gruppe mit einem vergebenen Namen scheiterte, ohne zu sagen warum. Jetzt werden Namen bereinigt, und ein vergebener Name wird klar abgelehnt, egal wie er geschrieben ist.
- **Der Speicherverlauf ist sofort aktuell.** Eine gescheiterte Änderung am Speicher oder ein Test deines gespeicherten Speichers fehlte im Verlauf der Speicherseite, bis du die Seite neu geladen hast. Beides erscheint jetzt sofort, und deine Eingaben bleiben im Formular.
- **Mitgliederseiten im Verband zeigten keinen Namen.** Wenn du im Verband eine Person geöffnet hast, stand oben nur „Mitglied“ statt ihres Namens. Jetzt siehst du dort, um wen es geht.

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
