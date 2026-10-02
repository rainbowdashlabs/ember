# Changelog

## v26.20.0

### New Features

- **Expiry dates that remind you in time.** A profile field can now hold a date that runs out, like a first aid course or a driving licence. It turns yellow when the date is close and red once it has passed, and the member and their member management get a reminder ahead of time.
- **Forms with pages and branching.** You can split a form into pages and send each page on to the next one, to a page further down or straight to sending. A single-answer question can pick the next page, and the results show how many people saw each question.
- **Pick up a form where you left off.** A form you started but did not send is kept and reopens on the page where you stopped. Only you and whoever looks after you can see it, and it goes away when the form closes.
- **Cancel and restore single dates.** Managers can now call off just the date in front of them, while cancelling a whole series stays its own, final step. You can bring a cancelled date back as long as it lies ahead, and everyone still registered hears about it.
- **Background tasks at a glance.** A new page under Monitoring lists every job the server runs on its own, like sending mail, reminders and clean-ups. You see how often each one runs, when it last ran, how long that took and its last failure, counted since the last restart.
- **Group sets for levels and stages.** You can put groups into a set, like the levels of a training, and a member sits in only one group of a set. Sets live on the groups page, and picking another group of the set moves the member over.
### Improvements

- **Filter number columns by their values.** Besides the range, the filter of a number column like age now lists the values in its rows. You tick the ones you want.
- **Preview, duplicate and a thank-you message.** The form editor now shows the form just as people will fill it in, path through the pages included. You can duplicate forms and questions, and show your own message and link after sending.
- **Drag questions and pages into place.** You drag questions within a page or across pages, and move whole pages too. Leaving the editor with unsaved questions asks first, and reopening it offers them back.
- **Lending reaches partners on other instances.** Partner stations on different instances can now browse, request, lend and return gear just like stations on one instance. Both instances need this version, and a partner appears among the offers once it has updated.
- **Errors point right at the question.** When answers cannot be sent, the form jumps to the page with the problem. Every question that needs a look is marked.
- **Cancelled dates are marked everywhere.** The upcoming list, the month calendar and your personal calendar feed now cross out a cancelled date and mark it as cancelled. Signing up for it is no longer offered.
- **SFTP and SMB storage handle requests in parallel.** Pages full of pictures no longer load one file after another, and stations on the same cluster storage share their connections. In a test, 32 reads next to a large upload took half a second instead of four, and the help center's storage page describes the limits.
- **A clear message when storage is unreachable.** When a station's storage does not answer, readers now learn that it cannot be reached right now. They get `503 Service Unavailable` instead of a general error.
- **Pictures take less space and load faster.** The smaller sizes of avatars, logos and wiki, quiz and lost and found pictures are now stored as WebP. No size is kept that is as large as the original, and pictures uploaded before stay as they are.
- **Announcing an appointment gives you a ready entry.** "Announce as news" opens an entry with an event block for the chosen day and the appointment's description. A short text covers what the block leaves out, like the deadline and the number of places, and you can still change every part.
- **Event blocks in news entries.** A news entry or wiki article can now show an appointment as an event block, even on a chosen day of a repeating one. Readers outside the station, like partner stations or the public blog, see a short note for internal appointments instead.
- **Attendance templates take whole member types.** A template can now name member types as well as groups, and everyone of a chosen type lands on its sheets. Starting a sheet from a template shows its types and groups already ticked, ready to keep or change for that sheet.
- **Discovery lists stations of other instances.** The public discovery page at `/discovery` now also shows the public stations of the other Ember instances this one knows, each marked with its instance and linking to its public page there. A search field finds stations on every instance by name, place, association or instance.
- **Station setup explains the discovery listing.** The visibility step of a new station's setup explains each choice, including listing on this instance only. It shows exactly which details a public listing shares, and saving the preset choice unchanged counts as done.
- **Groups for chosen member types.** A group can now be limited to some member types, like team and manager, and takes nobody else. When a member's type changes, they leave the groups that no longer fit, and their edit page lists those first.
- **Groups and tags lead a member's permissions.** On a member's edit page, groups and tags are now compact chips above the permission list, and the groups of a set are a single choice. Groups meant for other member types show greyed out with the types they take.
- **Edited news comments say so.** A news comment its author changed is now marked as edited. Comments on appointments, wiki files and board tickets already did this.
- **Comment notices read the same everywhere.** A notice quoting a long comment on a news entry or wiki file now ends its excerpt with "…", like appointments and board tickets. When you are mentioned in a ticket comment, the notice names who wrote it instead of the ticket.
- **Exports print answers the same way.** The member list, registration table, attendance sheet, appointment list and member inventory lists now show yes and no as words in the station's language. Dates show as days and members by name, where some exports used to print raw values like "true" or a member's number.
- **One name for each field type.** Every screen where a station sets up its own fields, from profile questions to waiting lists, now offers the types under the same names. Answers read alike everywhere too, now also on attendance sheets, board tickets and the public waiting list status page.
- **Board fields can be required.** You can mark a board field as required, so its value can change but not be cleared. Choice field answers are entered one per line, like everywhere else.
- **Templates carry registration questions.** The appointment template editor now sets up the questions asked at registration. It is the same editor an appointment uses.
- **Gear number fields take steps below one.** In the field editor you can set a gear number field's step to a fraction like 0.5. The field then takes decimal numbers.
- **Configuration help lists every setting.** The help center's list of settings and environment variables now comes straight from the server. It also shows the device sign-in limits, the older mail fallbacks and the encrypted storage credentials, each with key, variable and default.
- **Association notifications in the app.** Association → Notifications now lists what the association told you, and opening a notice takes you to its page and marks it read. The bell in the association's menu counts the unread ones, and reminders held back while an earlier one was unread come again once you read it here.
- **Refusals name the value at stake.** When something is refused, the message now shows the value it is about, in your language. That might be how much association storage is left, how many things are still limited to a group, or which question an answer did not fit.
- **Movement chains can skip the member's receipt.** Each chain on the movement chains page has a switch that confirms receipt for the member as soon as a piece gets there. The movement's history marks that step as confirmed automatically, and other chains keep asking.
- **Your association's storage keeps a history.** The association's storage screen now lists every change: the storage you set, what you decided, each move and each refused change. A move you make for a station shows up in that station's history too.
- **Every storage screen asks before files move.** Moving a station's files or giving up your association's storage now waits for one confirmation, just like the station and instance screens already did. You see the same summary card and the same question on all three screens.
- **Refused storage changes are written down.** When a storage change is refused, the history of the station, the association or the instance now says so and gives the reason you were shown.
- **Clear out the paperwork of people who left.** A new switch on the documents page shows only documents about people who have all left, archived or deleted. Tick a few, or every one the filter finds, and delete them in one go after a single question.
- **The association sees documents like the station.** On a person's page in the association, documents are tiles you open and preview, just like at the station. A document you file there names you as the one who filed it.

### Security

- **Entries for named people stay with them.** A form, appointment, blog entry or quiz meant for a list of named members was shown to the whole station. Now only the people named and those who manage it can see it.
- **Hidden appointments stay hidden.** A member could open a hidden appointment's details, registrations and comments by asking for its number directly, and registration counts included hidden appointments. Ember now answers such requests as if the appointment did not exist, unless the member may see or edit it.
- **Partners on one instance see only shared content.** A partner station on the same instance could open quizzes, test protocols and wiki articles, and read and write comments, that were never shared with it. It now sees exactly what a partner on another instance sees.
- **Partners stay on the board shared with them.** A partner allowed to edit one shared board could change checklist items, move tickets and attach labels on other boards of the same instance. Its changes now stay on the board it was given.
- **Notices about restricted entries reach the right people.** In some cases the notice about a new form, appointment or blog entry went to members outside its audience and showed its title. Now it only reaches members who may open the entry.
- **Joining a group needs the rights it grants.** Someone who could manage groups could put anyone, themselves included, into a group with permissions they did not hold. Now that takes every permission the group grants, plus a fresh confirmation where it grants any.
- **Groups only take members of their station.** A group's member list accepted people from other stations when they were sent to the server directly. Ember now refuses them.
- **Registration codes stay with their station.** An instance administrator working in one station could open, delete or change the groups of another station's registration codes by their number. A code is now only reachable from its own station.
- **Instance drafts stay with instance administrators.** A station's news managers saw the instance's draft news in their list before it was published. Drafts now stay with the instance administrators until they go out.
- **Station applications need the confirmation mail.** Applying for a new station returned the code that confirms the applicant's address, so the address could be confirmed without the mail. The code now only arrives in that mail.
- **News managers stay within their own station.** A news manager could remove a comment on another station's news entry by its number. A comment can now only be removed from its own station, which for instance-wide news is the station its author wrote from.
- **Station files are for station members only.** Anyone signed in on the instance could open a station's media files, pictures and documents, list its media and see its cluster just by naming the station. Now only members of the station get an answer.
- **Authenticator setup codes work only once.** The code you typed to confirm a new authenticator app was still accepted by the next sign-in or confirmation within its short validity window. It now counts as used the moment the app is set up.
- **Templates of other stations cannot be copied.** Someone allowed to create appointments could copy the registration questions of another station's template by giving its number. Only the station's own templates are used now, and naming another creates no appointment.
- **Procedure steps stay in their procedure.** Someone working on one procedure could tick off, edit, delete or annotate a step of any other procedure, even in another station, and the same held for procedure templates. A step is now only reachable through the procedure or template it belongs to.
- **Only procedure runners write step notes.** Any member of the station could write or replace the note on a step of any procedure by sending it to the server directly. Now only those who run procedures can, as the procedure page already suggested.
- **Profiles stay with those allowed to see them.** Any member of the station could read and overwrite other members' profile answers by sending the request to the server directly. Now only the member, their guardian and those who may read or edit members can.
- **Association questions respect their locks.** A station could answer its association's profile questions past their locks, even ones kept from the station or not asked of that member. The association's member management could also answer questions of any station, and now every answer passes the same locks as the station's own.
- **Removing a group no longer opens content.** An appointment, template, news entry, form, quiz or wiki entry limited only to a group became visible to the whole station once that group was removed or turned into a tag. Both now wait until nothing is limited to the group any more.
- **Instance storage is checked like a station's.** Saving it now refuses incomplete credentials and addresses the instance may not reach. If your files sit on a server in your own network, set `federation.allowPrivateHosts` before you switch.
- **Hidden station papers stay hidden.** A hidden document that names nobody could be opened by anyone allowed to read the station's own papers, as long as they knew its number. Now only those who may read member documents open it.
- **Tags and keeping stay with document managers.** A member allowed only to put documents on themselves could add tags to the station's list and mark a document to outlast the membership. Both now stay with the people who manage documents.
- **Files are taken for what they really are.** A document could claim to be a picture or a PDF while being something else, and was shown as one. Now its contents decide, and a file whose name says otherwise is turned away.
- **Removing a group needs the rights it grants.** Anyone allowed to manage groups could delete a group, or turn it into a tag, and so take permissions they did not hold themselves from everybody in it. Now you need every permission the group grants, and a fresh confirmation if it grants any.

### Changes

- **Question settings move into a menu.** Everything beyond "required" now sits in the menu in the corner of each question. Changed settings show as labels under its title, and one button at the end of each page adds a new question.
- **Sending a form shows a confirmation.** After you send a station form, a screen tells you it arrived. It leads back to the forms and, where allowed, lets you change your answer.
- **The Jugendflamme template offers "None".** The quick template now adds one choice field with "None" and the three levels, so every member holds exactly one level. The date the level was reached comes along as before.
- **Minimum registrations count days before each date.** An appointment with a minimum number of registrations now says how many days before each date it must be reached, instead of one fixed day. One-time appointments keep their deadline, but on repeating ones the old deadline is removed and needs setting again in the editor.
- **The backend gets thirty seconds to shut down.** The shipped compose files and the installer give the backend container a `stop_grace_period` of 30 seconds to finish running requests and save buffered statistics and log lines. If you run your own compose files, set the same on the backend.
- **You switch on association mail yourself.** The association's notification mail now only reaches people who turn it on under Association → Notifications, where it starts off. Until then, its notifications stay in the app.
- **New stations are listed publicly.** A station founded from now on shows up on the public discovery page and at other Ember instances from the start. It can switch this off during setup or under Federation → Settings, and existing, imported and transferred stations keep their setting.
- **A fresh installation asks for its first station.** A new instance no longer creates a station called "default". After the first sign-in, the administrator names and founds the first station, becomes its manager and goes straight into its setup, while existing instances keep their stations.
- **Your comments sit together in the data export.** When you ask for your data, all your comments now sit in one section, whether on appointments, news entries, wiki files or board tickets. Each one says what it was written on, and notifications pointing at a comment still open it.
- **Board comments belong to their authors.** A ticket comment can now be changed only by its author and removed only by its author or a board manager, no longer by anyone who could edit the ticket. A comment can no longer be saved empty.
- **Clearer notices on watched tickets.** A notice about a new comment on a ticket you watch names who wrote it, in your language. It follows your setting for comment notices rather than ticket updates, and nobody hears about their own comment any more.
- **Number fields take whole numbers.** Number fields on attendance sheets, waiting lists, gear and board tickets take whole numbers, as their input boxes already suggested. A gear field with a step below one still takes fractions, and numbers saved before stay as they are.
- **Member fields keep to their limits.** A member field on an attendance sheet or appointment that is limited to a group, member type or tag now refuses anyone outside it. That holds when the sheet, appointment or template is saved and when a registration question is answered, and members it already names stay.
### Fixes

- **Late declines reach an open attendance sheet.** A member who declined or gave back their place after the sheet was opened still stood on it as undecided until someone synced it. Now they show as declined right away, while hand-marked entries and closed sheets stay as they are.
- **Saving storage twice no longer eats your files.** Applying a storage you were already on, say to change just the password, deleted the files stored there. Ember now notices it's the same place and leaves your files alone.
- **Headings no longer become member list columns.** Headings and spacers of the profile form showed up as empty columns in the member list and its export. Now they are left out.
- **You can edit your comments on partner boards.** Changing or removing your own comment on a board a partner station shares with you did nothing. It works now, and boards shared across two instances pause until both run this version.
- **Comment delete buttons show for the right people.** News and wiki managers did not get the button to remove other people's comments, while appointment managers saw it in places where removing then failed. Now it shows for the author and for whoever manages that kind of content.
- **Addresses in group and tag lists line up.** In the member lists of groups and tags, the address of someone with a profile picture sat beside the picture instead of under the name. It now lines up under the name for everyone.
- **Reordering options keeps answers as they were.** Reordering or renaming the options of a question with answers could change what those answers said. Answers now stay with the option that was picked, and removing a chosen option asks first.
- **Optional questions no longer block sending.** In some cases a form was refused when an optional choice or rating question was left empty. The same happened to a choice answered only in your own words, and both are now accepted.
- **Shuffling questions and options works.** The shuffle settings for questions and options were saved but ignored when the form was filled in. Now they shuffle, with questions mixed within each page.
- **Exported answers are easy to read.** The spreadsheet and PDF of a form's answers showed each answer in its stored form. They now show the text of the chosen options.
- **Group questions show up on every profile.** In some cases a question a station asks one group was missing from a member's profile when the association asks questions too. Such questions now always appear and can be answered.
- **Members who withdrew can be added again.** After someone withdrew or declined, the list for adding members to an appointment no longer offered them. Now managers can put them back on.
- **Members who withdrew can sign up again anywhere.** In some cases a member who gave their place back still showed as withdrawn, with no way to sign up again. Both the upcoming list and the appointment's page now let them back in.
- **Repeating appointments keep their dates apart.** On a repeating appointment, the registrations tab could show answers, lists and counts from another date than the one open, and signing off could give up the place on that other date. Now the tab sticks to the date you opened.
- **Event links on pages lead somewhere.** The button of an event block on a station page led to a page that does not exist. It now opens the station's public calendar.
- **Event blocks on pages remember their appointment.** Saving a station page could drop the appointment chosen in an event block, which then showed as no longer available. The blocks now keep what you picked.
- **The profile field list fits medium screens.** Between phone and wide desktop, the profile field list squeezed its names until the rows overlapped. It now switches to tiles as soon as the table no longer fits.
- **Sign-up is only offered where it is allowed.** A member could be offered a sign-up for an appointment meant for part of the station, and only learned it was closed after pressing it. Now only those who may register see the button, and everyone else gets a short note why.
- **Repeating appointments say how often they repeat.** The page of a monthly, quarterly or yearly appointment called it weekly. It now names the real rhythm, just like the list of appointments.
- **Cancelling one date no longer hits the series.** Cancelling a repeating appointment, by hand or for too few registrations, called off every date and told everyone registered for any of them. Now only the date concerned is cancelled, and the automatic check counts that date's registrations alone.
- **The public calendar marks cancelled appointments.** The station's public page and public calendar feed listed cancelled appointments as if nothing had changed. They now show them as cancelled.
- **Statistics no longer dip after a restart.** Each restart lost up to an hour of page visits, plus the latest traffic counts, request timings and log lines. They are now saved before the server stops, as long as it gets its time to shut down.
- **WebP pictures upload everywhere.** Uploading a WebP picture as a station logo or as a wiki folder icon or image failed, and sometimes removed the picture already there. WebP is now taken like any other format.
- **Instance storage survives a station moving back.** When a station with its own storage moved back to the instance storage, every instance file on SFTP, SMB or S3 could fail until Ember was restarted. The instance storage now stays open.
- **Attendance sheets remember who they are for.** A sheet started for chosen member types and groups still listed the template's groups, and filling it from its appointment added their members. Now a sheet keeps its people on screen, when it is filled again and in its PDF.
- **The attendance report uses proper type names.** The report heading and its preview showed member types as "TEAM" or "GUARDIAN". It now uses the names the rest of the page uses.
- **SMB storage recovers from a dropped connection.** In some cases, once the connection to an SMB server was lost, every file on it failed until Ember was restarted. Ember now signs in again on the new connection.
- **Changed storage settings close old connections.** Every change to a station's or a cluster's storage left the connection to the old server open until Ember was restarted. Replaced connections are now closed.
- **Deleting on SFTP storage no longer fails.** In some cases removing a file that was already gone from SFTP storage returned an error. It now succeeds quietly, as on every other storage.
- **Animated logos stood still.** An animated GIF uploaded as a station logo, and a GIF's tile in the wiki, showed only the first frame. Now they keep moving.
- **The page at `/pitch` fits phones right away.** In some cases the attendance buttons in its example stayed desktop-wide on a phone until the screen was turned or resized. The page now switches to the phone layout as soon as it loads.
- **An unreadable message no longer stops imports.** In some cases a single message the mail server could not hand over made the import fail every time, until the mailbox was suspended. Ember now skips that message and imports the rest.
- **Pages that are gone say so.** Opening a public page, wiki article or shared link that was no longer available showed an error that explained nothing. The page now says it is gone, and the link dialog warns you while the station's public pages are switched off.
- **News blocks remember their news entry.** Saving a page or article dropped the entry chosen in a news block. The block now keeps it and shows it as it is today, and its search only offers entries every reader may see.
- **Sharing a board with partners saves again.** Saving which partner stations see a board failed, so no board could be shared at all. It saves now, and you choose for each partner which member type there may see it.
- **Association mail uses the right language and time.** The association's notification mail was always in English, and its send times were read in UTC instead of local time. It now follows the language and time zone of the association's home station.
- **Notifications no longer arrive twice.** In some cases the same notification showed up twice when two people did the same thing at the same moment. Now it appears once.
- **Former guardians are no longer notified.** People who had left a station could still get notifications and mails about the members they used to look after. That has stopped.
- **Quizzes from partners on other instances open.** A quiz shared by a partner station on another instance would not open once it held any questions. It now opens with all of them.
- **Shares reach named partners on the same instance.** A news entry or appointment shared with named partner stations never reached a named partner on the same instance. It now reaches every partner it names.
- **Comments at partners on other instances work.** You could not delete your own comment on a news entry or appointment of a partner on another instance. Sometimes you could not start a new comment on such an appointment either, and both work now.
- **Own boards no longer appear among a partner's.** In some cases a board a station shared with a partner on the same instance showed up in its own list of that partner's boards. The list now shows only what the partner shares.
- **Unwatching partner tickets across instances works.** A member watching a ticket on a board shared by a partner on another instance could not stop watching it. Now they can.
- **Network map links reach the right station.** On the map of the discovery network, the link to a station on another instance opened a page that did not exist. Once both instances run this version, it opens the station's public page.
- **The footer links to discovery on phones.** On narrow screens the footer left out its link to the station directory. It now shows at every width.
- **Changing a member's groups is safe now.** Choosing groups on a member's edit page failed for people who could edit members but not manage groups. Two people changing one group at once could also undo each other's work, so the page now saves only that member's groups.
- **Sharing with chosen partner stations works.** Saving an appointment or news entry shared with only some partner stations was refused. It now saves with the partners you picked.
- **Editing an appointment keeps questions public.** Saving an appointment switched off the public setting of each of its questions, so they vanished from the public calendar. Questions now keep their setting.
- **Attendance fields without a default stay that way.** The template editor showed an attendance field without a default as if it had one, and saving stored an empty text, a zero or a no. The editor now shows such fields without a default.
- **The registrations overview shows the limit.** The overview of registrations did not show how many places an appointment has. The limit now sits beside the appointment.
- **Restricted news entries show their lock.** A news entry meant for only part of the station appeared without the lock that marks it. The lock now shows in the news list and on the entry.
- **New appointment categories keep all their settings.** A new category was saved without its limit of shown appointments and without its public setting, so both had to be set again. Both are now saved with the category.
- **Editing a category keeps its place.** Saving an appointment category moved it to the top of the list. Now it stays where it was.
- **Restricted forms show their lock.** In the list of forms to answer, a form meant for only part of the station appeared without its lock. The lock now shows.
- **Saved member list filters stick.** Saving the current member list filters under a name ended in an error, so nothing was kept. The filter now saves and is offered again like any other.
- **Procedure template steps can depend on each other.** Adding or removing a dependency between two template steps was refused, so no template ever had one. Dependencies now save, and each step shows what it waits for.
- **Procedure template steps keep their order.** Steps added or edited in a procedure template all landed at the top, so the order could shift after every edit. A new step now goes to the end, and an edited one stays put.
- **A cleared step note stays cleared.** Emptying the note on a procedure step kept the old note. Clearing it now removes it.
- **AI quiz questions arrive complete.** Asking the AI for new catalog questions, or a new version of selected ones, failed, and questions that did arrive lacked their answers. Generated questions now arrive and are saved complete.
- **Free-text and picture questions show answers.** In a quiz catalog opened read-only, free-text and picture questions showed no correct answer. They now list the accepted answers like every other kind.
- **Corrections are marked in a piece's history.** When a check set right who holds a piece, its history showed that like an ordinary hand-back. Such entries are now marked as a correction.
- **Ember names the kind that blocks a change.** When a collection could not become a stock because kinds are still defined in it, the reason showed as unreadable text. It now names the kind in the way.
- **The association's inventory switch shows its state.** The setting that an association keeps its gear in Ember always looked switched off when its inventory settings were opened. It now shows how it is really set.
- **Refused unsigned mail names its reason.** In the import log of a mailbox that only files signed mail, a message refused for a missing, unrelated or broken signature showed as unreadable text. It now names the reason.
- **A station's public web address can be removed.** Emptying the public page's web address in the federation settings kept the old one, and it came back on reload. An empty field now removes it.
- **Bookmarks on federated boards show at once.** Bookmarking a board on the federated boards page left it unmarked until reload, and a second click tried to bookmark it again. The bookmark now shows right away, and a second click removes it.
- **Guardians see gear just like the member does.** Under My inventory, the gear of members in their care showed no exchange step or picture, and still offered an exchange or loss report for pieces on their way. It now shows exactly as the member sees it.
- **Profile answers land on the right question.** When a station and its association each asked a question under the same number, a member's own profile page could show one answer for both. It saved that answer to the station's question only, and now each question keeps its own.
- **Accounts without an address drop "(null)".** An account that signs in with a username and has no email address showed as its name plus "(null)" in the administration's account picker and second-factor reset. Now it shows just the name.
- **Help examples tick every granted permission.** In the help center's examples for member, user type and group permissions, the attendance and appointment management rights showed as not granted. The examples now show them ticked.
- **The member list marks incomplete profiles.** Members who left a required profile question open looked like everyone else in the member list. They now carry "Incomplete" beside their name, judged the same way as the reminder on their own profile.
- **Former members no longer break lists.** In some cases a former member whose account was removed made reports, exports, inventory checks, a guardian's member list or a profile's history fail. Such a member now shows by name or number.
- **"Open in browser" in feeds leads somewhere.** In some cases a notification in the RSS or Atom feed had an "Open in browser" button with no destination. It now opens the same page as the entry.
- **Template registration questions come along.** An appointment created from a template did not take the template's registration questions, and sometimes took another template's instead. It now takes its own template's questions, and they show in the editor before you save.
- **A repeated Sweego report no longer sends twice.** In some cases, when Sweego sent the same bounce report again, the mail went out twice or moved to the next provider too early. A repeated report is now recognised and counted once.
- **Association profile answers are checked.** An answer to an association's profile question was saved whatever it said, like a choice the question does not offer or a date that is no date. It is now checked like answers to the station's own questions.
- **Member import skips answers that do not fit.** Importing members stored cells like an unknown choice, a day that is no day or "maybe" under a yes or no question as written. Such a cell is now left out, and the preview and result name its row.
- **Board fields refuse values that do not fit.** A custom field on a board ticket could be saved with a date that is not a date or a choice the field does not offer. Such values are now refused on save.
- **Attendance date fields can start today.** Saving an attendance template field set to start at today's date was refused with a message that it expects a date. It saves again, and new sheets start on the day they are made.
- **You can always take yourself off a field.** A member who had put themselves into an appointment field limited to a group, member type or tag was refused when leaving it after no longer belonging. Taking yourself out now always works.
- **Yes answers show as yes.** In some cases a yes saved by an older version showed as no in an appointment's fields and in registration answers. Such answers now show as yes everywhere.
- **Board number fields can hold zero.** Entering 0 into a ticket's number field emptied the field. Zero now stays.
- **Registration answers respect the question's limits.** In some cases the box for a number question took numbers outside its range, and a member question limited to a group or tag offered every member, so saving failed. The box now keeps to the range and offers only the members the question takes.
- **Association gear opens at the station.** A piece the association lists in its own store and sent to a station showed in the station's lists and scanner, but opening it said it was not found. The station now opens, hands out and reports it like any other piece it holds.
- **Choosing a movement chain survives double saves.** In some cases, when an inventory's chain was chosen from two places at the same moment, one save failed with an error that the entry already existed. Both saves now go through, and the last one applies.
- **A member's gear matches the movement list.** A piece with a running exchange or return showed the step still awaited, so a jacket read as taken back while the member still had it and a pending replacement showed nothing. Each piece now shows the last step that happened and whose turn it is, like the movement list.
- **The quick check shows the last real step.** During an inventory check, a piece with a running exchange or return showed the step still awaited, so a jacket in hand read as taken back and a pending replacement offered a swap that then failed. The quick check now shows the last step that happened and whose turn it is, like the movement list.
- **Association storage tests keep network details private.** A failed test on the association's storage screen showed the exact reason, like a refused connection or a timeout, which reveals too much about the network behind the address. It now gives the same general answer as a station's test and keeps the details in the instance log.
- **A deleted member's documents turned into station paperwork.** When you deleted a member or their account, their documents stayed behind naming nobody, open to everybody who may read the station's own papers. Now the rest goes with the member, and what you keep for the record keeps their name.
- **Uploads ignored your station's limits.** Documents uploaded by hand or from the association, and files attached to a loss report, skipped your station's size limit for one file and its storage space. They now meet both, just like documents that arrive by mail.
- **Switching documents off left the files reachable.** A station with the document store switched off still handed out each document and its picture to anyone who knew its address. Off now means off, for the association too.
- **Archiving from the association is complete.** A member archived from the association's member management kept their login, roles, guardians, groups, tags, documents and profile answers. Archiving there now does exactly what it does at the station, and is refused in the same cases, like gear still handed out.
- **Association mail links open the right page.** The button in the association's notification mail opened a page that does not exist, and most notices in it opened a station's start page instead of the association's page they were about. They now open the association's own pages, in the association the mail is about.
- **Some notices led nowhere.** Opening a notice about a lending request, a storage warning or imported mail waiting to be filed did nothing in the app, and in a mail or a feed opened the start page instead. They now open the lending request, the storage page and the member documents.
- **Notification settings could stop saving.** Once the switch for exchange requests on the notification settings page had been touched, every later change on that page failed to save. The switch is gone, since those notices became movement notices long ago, and the page saves again.
- **Station and association answers got mixed up.** Where a station question and an association question shared a number, some profile screens showed one answer under the other and saved it to the wrong question. Every profile screen now keeps the two apart.
- **Association managers without a station can save profiles.** If you manage the association's members but belong to none of its stations, saving a member's profile failed. It saves now, and the change history names you.
- **Field managers could not open the association's questions.** If you may edit the association's profile questions but not read its members, the list of questions stayed closed to you. It opens for you now.
- **Spacers can be added to association questions.** Adding a spacer asked for a name the form never showed. Spacers are now numbered on their own, just like at the station.
- **Archiving kept a member's answers to the association.** When you archived a member, their answers to the association's questions stayed, even where a question was not marked to be kept. They are now cleared like the station's own.
- **New station questions keep "Keep when archived".** Creating a question with this box ticked saved it without the setting. It now sticks from the start.
- **You hear when the association changes your profile.** The association could fill in your profile from its member screen without you hearing about it. You now get a notification when it does.
- **New association storage credentials could be ignored.** In some cases, after you changed only the password of your association's storage, files were still written with the old one until Ember was restarted. The new credentials now apply at once.
- **Old association storage settings were never deleted.** When an association pointed its storage somewhere new or gave it up, the old settings and their credentials stayed behind for good. They are now deleted as soon as no station's files are on them.
- **Saving association storage could leave none behind.** In rare cases a save that failed halfway left the association without any storage of its own. The storage it had now stays until the new one is saved.
- **A refused move showed up as started.** When an association moved a station whose files were already in place, the station's history still listed the move as started. Only moves that really start are listed now.
- **Association groups could save half a change.** When one part of a change to an association's member group was refused, the parts before it stayed saved, such as a new name with the old members. Now a change is saved as a whole or not at all.
- **Closing an association group told nobody.** People in an association's member group lost what it granted without a word when the group was deleted. They now get the same notice as when they are taken out of a group.
- **Association groups offered buttons that did nothing.** If you may only look at the association's member groups, you were still offered creating, deleting and changing members, and the server then refused. Only the association's administrators see those controls now.
- **Duplicate group names ended in a general error.** A station's group names kept stray spaces, and a second group with a name already in use failed without saying why. Names are now trimmed, and a name that is taken is refused with a clear message, however it is capitalised.
- **The storage history keeps up with you.** A storage change that failed, or a test of your saved storage, was missing from the history on the storage screen until you reloaded the page. Both now appear right away, and what you typed stays in the form.
- **Association member pages showed no name.** When you opened a person in the association, the page title read only "Member" instead of who it was about. It now shows the person's name.

## v26.19.5

### New Features

- **Ember can keep itself up to date.** If you like, the installer sets up a job that fetches the newest version every hour, restarts Ember on it and clears away the old image. The install page offers the same as a switch, and the hosting help shows the line to set it up by hand.

### Security

- **Daily figures no longer give away their sender.** The counts sent to a beacon were signed with your instance's key, so the beacon could tell where they came from. They now go out unsigned, just as the beacon settings always promised.

### Fixes

- **Search finds every word again after a database upgrade.** In some cases, after PostgreSQL moved to a new major version, search in the wiki, documents and boards missed words spelled with "ae", "oe" or "ue". Ember now rebuilds its search indexes on the first start after such an upgrade.
- **Editing a form keeps its answers.** In some cases, saving a form that already had answers deleted them. Now saving only removes answers to questions you removed, and it asks you first.
- **Changed dates in the history are easy to read.** When a date in a member's profile changed, the history showed both dates as stored, such as 2026-03-31. You now see them as 31.03.2026, just like in the profile.
- **Team members and managers see the members in their care.** Their relations tab was named after guardians and offered to assign one, which cannot be done for them. It now lists the members they look after, as it does for guardians.
- **Filling in a form again keeps your earlier answer.** A form for a member in your care always started empty, and sending a form twice could overwrite the first answer, even where answers cannot be changed. The earlier answer now opens for editing where the form allows it, and stays untouched where it does not.
- **Hiding the age next to a birth date saves again.** Switching off the age shown next to a date of birth field made saving the field fail. The setting now saves and is respected.
- **Quick templates for profile fields add their fields.** Most quick templates in the member field settings, such as the address or the date of birth, failed to add anything. They now add their fields, with the right ones marked as required or as editable only by the member management.

## v26.19.4

### Improvements

- **Appointments over several days show both ends.** The Appointments page and the list for managing appointments now show such an appointment from its first day and time to its last. It no longer reads as running from eight to four on every single day.

### Fixes

- **Appointments over several days end on the right day.** Their own page showed the end on the day they start. It now shows the day they actually end.

## v26.19.3

### Improvements

- **An exchange shows both sizes at a glance.** In the movements list, a swap shows the size handed in and the size asked for side by side, with an arrow between them. You see what is being exchanged without opening it.
- **Picking the replacement points at the right size.** The size asked for stands above the piece you pick or write down. Pieces on the shelf in that size are highlighted and offered first.

### Changes

- **Answering per date only on repeating appointments.** The switch to answer a question separately for each date no longer shows on a one-off appointment. There is only one date to answer for there.
- **Today's appointments moved to the Appointments page.** Managing appointments no longer lists them on top. You find them, and attendance for them, on the Appointments page in the sidebar.

### Fixes

- **Attendance sheets no longer assume everyone came.** A sheet made from an appointment marked everybody who had accepted as present, so there was nothing left to check. Those rows now stay open for you to mark.
- **Required forms and tests ask only their audience.** A form or test limited to certain groups still asked every member of the station. Now only the people it is meant for are asked, and guardians are asked for the members in their care.
- **Mentions added while editing a comment now notify.** Only mentions in the first version of a comment reached anybody, so a name added later went unnoticed. Mentions added by an edit now notify, and the ones already there are not sent again.

## v26.19.2

### Improvements

- **When something goes wrong, it says what.** Instead of "that did not work", a failure now names what happened, whose it is to fix and what to do next. If it looks like a fault in Ember, you can report it right there, and the people running your installation get everything they need.
- **Rules no longer ask you to report a bug.** Something you may not do, a name already taken, a file too large: each now says so plainly and offers no report. That way the reports that do arrive are the ones worth reading.
- **Things that worked no longer report a failure.** Saving, deleting, inviting and handing over said they had failed when only the list behind them would not refresh, so many people did it twice. Ember now tells the two apart, and a screen that is merely out of date says so.
- **Every failure carries a short code.** Error messages now show a code such as F-021, short enough to read out over the phone. It is part of the report too, and points straight at the one place the problem came from.
- **Failure messages are in German.** Messages coming from the server itself used to reach you in English. They are now German throughout, and a message not yet translated still shows instead of vanishing.

- **Past appointments have a tab of their own.** Both appointment lists now keep what is coming apart from what is done, so you no longer scroll through years of history to find next week. A repeating appointment stays with the coming ones while it still comes round, shown with its next date.
- **Search, filter and page through appointment lists.** Every list on both pages takes a search, a category and a date range, and loads page by page. Your choices stay in the address, so you can bookmark the list or pass it on.
- **The planner reads by date.** One-off appointments stand in one list in date order, each with its category, and repeating appointments get a block of their own. Picking a category is now a filter instead of a heading to scroll to.
- **Closed surveys can be reopened.** The menu on a closed survey now offers to open it again. Last season's survey can run again without being written a second time.
- **Clear a survey's answers and start over.** The menu can clear every answer a survey has collected, while the survey and its questions stay. A trial run or a round sent to the wrong people can be cleared, and everyone may answer again.
- **An expired survey says when it ended.** Its tile in the station's list shows the date it stopped. The page behind its link says it has taken no answers since that date, not just that it is closed.
- **Survey settings save as you change them.** The name, dates, reach and switches are saved the moment you change them, so you can no longer forget to save a survey that reaches too far. The questions still wait for Save, and the page tells you so.

### Security

- **Signing in no longer reveals who has an account.** A wrong address, a wrong password and an account that signs in another way now get exactly the same answer. Nobody can use the sign-in form to find out which addresses are registered, and the same now holds for confirmation links, passkey sign-in, device sign-in codes and the second-factor steps.

### Changes

- **No more hint about passwordless accounts.** The sign-in page no longer says when an account signs in without a password, since that difference let the form be read as a list of accounts. If you use a passkey, the passkey button is where it always was.

### Fixes

- **Confirming who you are names the real reason.** The extra confirmation before a sensitive change always said "wrong password" or "invalid code", so too many tries or an expired confirmation looked like a typo. It now says what actually went wrong.
- **No more English failures for German readers.** Messages from the server itself were shown as they came, so German readers met English sentences. Every one of them is now translated.

- **Headings between profile questions are headings again.** The form for a new member, and the one guardians fill in, showed each of the station's headings as an empty question to type into. Both now show headings as headings, with the questions in the order the station arranged them.
- **Guardians can no longer answer reserved questions.** A question the station kept for its member management, readable but not writable, was offered to guardians like any other. It now shows filled in and locked.
- **Monthly and quarterly appointments open on the right day.** Opened without a day in the address, such an appointment showed the next matching weekday, so sign-ups, attendance and questions belonged to a day it does not happen. It now opens on the day it next falls on, respecting the station's breaks and the end of the series.
- **Public survey links open the survey again.** The link offered for a public survey used the station's readable name, but only the address with its internal identifier was accepted. Every link copied from the survey's page led to "not found", and those links now open the survey.
- **A public survey shows even when it is all you share.** A public survey sits inside the station's frame, and a station with no public pages, wiki, calendar, waiting list or blog answered nothing about itself. The survey's page stayed empty for everybody, and it now shows the survey.
- **"Exactly" on choice questions saves again.** Setting "exactly" on a question that takes several answers was refused on save, and the survey came back unchanged without a word. The setting now saves.
- **Survey tiles know when a survey has closed.** A tile showed a survey as open after its closing date, while its page told visitors it had closed, because the tile only checked for a manual close. It now reads the dates too, and says when a survey has not started yet.

## v26.19.1

### Improvements

- **Open list entries in a new tab.** Rows and cards across the station now behave like the links they are. Middle-click to open a new tab, right-click to copy the address, reach them by keyboard and hear them announced as links.
- **The public wiki looks like the real wiki.** Public folders and articles now look the way members see them, with the same entries, search results, folder pictures and file previews. The switch between tiles and a one-line list is there too.
- **Shared public links say what they show.** The calendar, blog entries, wiki articles and the waiting list each carry their own name and a short description. A link pasted into a chat now shows a proper preview with the station's logo.
- **Public pages arrive complete.** Pages, the calendar, blog entries and wiki articles now come from the server ready to read, with no spinner while the browser fetches them. Search engines can now see what is on them, too.
- **Public dates follow the station's clock.** Appointments, blog entries and wiki articles show date and time in the station's time zone, wherever they are read. An appointment at seven in the evening says seven to a reader abroad as well.

### Fixes

- **The public wiki keeps its readable addresses.** Opening a folder or article in a public wiki switched to an address with the station's internal identifier instead of its name. Readers lost the readable address and shared the wrong link, and every public link now uses the readable name where the station has one.

## v26.19.0

### New Features

- **Send a survey or contact form by link.** Every survey and contact form now has its own link, opening it on a clean page with just the station's name around it. Anyone with the link can answer, it appears in no menu and search engines skip it, and a fresh link ends every copy of the old one.
- **Public forms can answer at their link alone.** Surveys and contact forms are publicly reachable by default, so they can sit on a public page. Switch that off and only the link works, so a fresh link really does close every way in.
- **Pages reachable by link alone.** Besides draft and public, a page can now open for anyone holding its link. It stays out of the menu and the sitemap, stands on its own without parent or child pages, and its link can be replaced the same way.
- **Member fields can be filled in per date.** A member field on a repeating appointment can hold its own entry for each date. Now it can say who drives this week and who drives next, and without the setting one entry covers the whole series as before.

### Improvements

- **A member field puts you on the list.** Whoever is entered in a member field of an appointment now takes part: they are on the registration list, counted, and see it in their calendar and subscription. The place is confirmed at once and is given back by removing the name from the field.
- **The calendar subscription reaches a year back.** It used to keep only the last week, so last autumn was out of reach. It now covers a year in both directions.
- **Limiting a survey to its link names affected pages.** A survey on a page stops working there once it answers only at its link. Switching it over now names those pages, and they show a note in place of the survey until someone removes it.
- **Ratings, rankings and scales work on public surveys.** Public surveys offered these three question types in the editor, but showed nothing for them when someone came to answer. All six question types now work wherever a survey is answered.

### Fixes

- **Unopened or closed public surveys say so.** A survey on a public page before opening or after closing showed its questions and a send button that ended in "try again". It now plainly says it is not open yet, or has closed, and offers nothing to fill in.
- **Links to other pages lead somewhere again.** A card pointing at another page lost its target on saving, so it showed a stand-in title and went nowhere. Cards now keep their target and follow it when a page is renamed or moved.
- **Switched-off public pages stay switched off.** Turning off public pages removed them from the menu and sitemap, but anyone with the address could still open them. The setting is now checked on every request, by address or by shared link.
- **Repeating appointments show all their dates.** The upcoming list kept only one entry per appointment, so a weekly drill was a single row however far you paged. The list now runs date by date, ten at a time.
- **Public forms no longer crowd the internal list.** The list under `/station/forms` showed surveys and contact forms meant for public pages next to the station's own. It now shows only the station's own surveys.
- **Public forms no longer offer useless settings.** Forms answered without signing in still offered "answers may be changed" and "an answer is expected", which need to know who answered. Both now show only where people sign in to answer.
- **Public forms no longer pretend to limit who answers.** Public surveys and contact forms offered the same picker of member types, groups, tags and people as internal surveys, but nothing used it. The picker now shows only on surveys the station's own members answer.
- **Leaving public survey results leads back correctly.** The way back from a public survey's results went to the internal survey list, which does not hold it. It now returns to the list you came from.
- **Pages under unpublished pages stay private.** A published page below an unpublished one could still be opened by address and appeared in the sitemap. A page is now public only when everything above it is.
- **Public forms no longer notify the whole station.** Opening a contact form or a public survey told every member about a new form, which then refused them. Only internal surveys are announced now.

## v26.18.7

### Improvements

- **Appointments and attendance sheets link up.** The menu on an appointment opens the sheet for the date shown, or starts one if there is none yet. The menu on a sheet leads back to the appointment on that same date.
- **Device sign-in asks you to match a number.** The device that wants in shows a two-digit number, and the approving device picks it out of six. Someone who only got a picture of the code cannot see that number, so a forwarded code is no longer enough.
- **Approving a device is just a scan.** The QR code now carries the sign-in code, so your phone opens straight onto what it is about to approve. No more typing eight characters, though the code still shows for anyone who cannot scan.
- **New settings for shared internet connections.** If your members reach the internet through one shared address, you can widen the per-address limits for device sign-in. The settings live under `auth.deviceHandshake`.

### Security

- **Sign-in codes now belong to one account.** Until now any signed-in member could approve any open code, so a code passed round a group handed over the account to whoever answered it. You now give your address or username before the code is made, and only that account can approve it.

### Fixes

- **Take attendance on any date.** The attendance entry only appeared while today was the date on screen, so a list written the morning after or prepared the evening before had no way in. It now works for whichever date the page shows, and the sheet belongs to that date.
- **Rare appointments reach the upcoming list.** The list only looked four weeks ahead, so quarterly or yearly appointments never showed up there. It now looks as far ahead as it needs to fill the page.
- **Each repeating date gets its own attendance sheet.** Attendance for a weekly appointment was dated to the first date of the series, and that one sheet reopened on every later date. Each date now gets its own sheet, dated to the day it covers.
- **Several devices can sign in behind one address.** In an office or hall sharing one connection, the second device waiting for approval was refused and then waited forever without a word. Every device and every account now has its own allowance.
- **Being asked to slow down says so.** A screen told it was trying too often showed a generic error and suggested a new code, which used up another attempt. It now says too many attempts were made and waits before trying again.
- **Device sign-in shows in every browser.** The link on the sign-in page only appeared in browsers that can hold a passkey, though this way of signing in never needs one. It now shows for everybody.

## v26.18.6

### New Features

- **See survey results by who answered.** You can filter and group an internal survey's results by member type, groups, tags, age and profile answers. Compare the youth group with the active members, or look at the over-40s on their own, side by side in their own colours and ready to bookmark.

### Improvements

- **Survey exports say who answered.** The spreadsheet or printout of an internal survey now shows each member's type, groups and age next to their name.
- **Pictures open large with a click.** Pictures and gallery pictures on pages and page-editor articles, and lost and found photos, now open full screen, uncropped and with their caption. Escape or a click beside the picture closes it.
- **Callouts, quotes and captions stand out in PDFs.** In a wiki PDF, callouts and quotes print in a shaded box with a coloured bar at their side. The line under a picture prints small and centred as its caption.

### Changes

- **Markdown files in the wiki are now articles.** The New menu and the type shown on a tile now say "Article" instead of "Markdown file".

### Fixes

- **Wiki PDFs include their pictures.** Saving an article as a PDF left out every picture, showing at most its alt text. The station's own pictures now print, fitted to the page or at the width they were given.
- **Wiki PDFs keep their formatting.** Coloured text, highlights and underlining printed as plain text, and highlights kept their equals signs. They now print just as they look in the article.
- **Page-editor pictures show their stored description.** When a picture block had no text of its own, the alt text and description from the media library were missing in articles and news. They now show there and in search, previews and PDFs, as they already did on pages.
- **Tiles stay a sensible size on wide screens.** On a large monitor the wiki kept four columns and lost and found three, so tiles stretched huge. The number of columns now grows with the window.
- **Lost and found photos show the whole item.** Photos were cropped to fill their card, which could hide the item itself. The whole photo now shows, scaled to fit.

## v26.18.5

### New Features

- **Favourites in the wiki.** Star any file or folder, even one a partner station shares, on its tile or at the top of an open file. It then waits for you in the Favourites folder at the start of the wiki, and only you see your favourites.
- **The wiki shows what its files are.** Pictures and PDFs now preview on their tile, the PDF by its first page, so you can tell a folder of sheets apart without opening each one. Existing files get their preview the first time someone opens their folder.

### Improvements

- **Download wiki files straight from their tile.** Uploaded files now have a download button on their tile, just like articles offer their PDF. You get the file exactly as it was uploaded.
- **Page pictures load at the size shown.** Banners, galleries and pictures on pages now fetch a copy sized for the page instead of the original photo. Pages open faster and use less data on your phone.

### Fixes

- **Saving a PDF in the installed app works.** On Android, saving a PDF from Ember installed through Firefox left an empty page and saved nothing, and other files reported a failure although they had saved. PDFs now open in the browser's own viewer for saving, and saved files no longer report a failure.
- **Saved attendance report filters work again.** Saving a filter under a name ended in a general error, and older saved filters could lose their user types or most of their groups. Saved filters now keep every user type and group, applied to the current week, month, quarter or year.

## v26.18.4

### New Features

- **We keep your unsaved writing for you.** Leave a page, a news entry or a wiki article without saving, and your text stays in your browser. Come back and Ember offers it to you again, until you save or a week has passed.

### Improvements

- **Files open on your phone instead of vanishing.** Documents, pictures and recordings now open right in the app on a phone or tablet, with a button to save them. Slow downloads used to end in nothing at all, and now they arrive.
- **Exports carry a name that says what's inside.** A report arrives as something like `Attendance - January 2026.pdf` instead of the same word every time. The name follows the language of your station's documents.
- **Spreadsheets open cleanly in your software.** You pick a semicolon or a comma when you export. Umlauts now open correctly every time.
- **Attendance reports by quarter.** Next to week, month and year, you can now pick a quarter.
- **The attendance report comes as a spreadsheet.** You can export the hours per name as a table, not only print them. That helps anyone who adds them up elsewhere.
- **You can print the answers to a form.** Before, you could only take them away as a spreadsheet.
- **The attendance report skips people who weren't there.** Members with no hours and no appointments no longer fill the summary with empty rows. The monthly tables already worked this way.
- **Pictures keep their alt text and caption.** When a file has an alt text or a caption, a page uses it. A tile with its own text still wins.
- **Lists are easier to work through on a phone.** Row actions now sit at the foot of each card, every button full width. The columns on a card get more breathing room, too.
- **Appointment texts read nicely in the lists.** Under Appointments → Upcoming and when starting an attendance sheet, descriptions used to show raw markup or push everything else off screen. Now they're formatted as written and cut after a few lines, just like news.
- **Twenty new pictures for your gear.** Clothing gains trousers, boots, trainers, a t-shirt, a jumper, a cap and socks, and equipment gains thirteen more, from an axe to a map. The old stand-ins stay under plainer names, such as Soles for the footprints.

### Changes

- **Documents are German unless you ask for English.** A station with no language set used to get its reports and sheets in English, and now gets them in German. Set your station's language to English to keep things as they were.

### Fixes

- **Tables in a tile no longer get lost.** A table written in the text editor kept only its words, so the rows were gone after saving. Ember now keeps a table as a table.
- **Table columns fit their content.** Columns grew to match the longest word, and dragging an edge fought back. They now share the width evenly, in the editor and on the published page.
- **The save button stays within reach.** Long text pushed it past the bottom of the dialog. The editor now applies what you write as you go, so it needs no button.
- **Searches find things once the list has loaded.** Typing into a picker before its list had loaded could leave it stuck on no results. It now searches again as soon as the list arrives.
- **Attendance sheets arrive already marked.** A sheet for an appointment started with every name open, so you had to look up who was coming. Now everyone who accepted is marked present, and on appointments that need registration, everyone else is marked declined.
- **Adding someone by hand reads the right day.** On a repeating appointment, Ember read today's answers instead of those for the sheet's evening, so people could show up as declined. Now the sheet's own day decides.
- **Loss report documents save on an iPhone again.** This one download button skipped the share sheet, so the file could go unsaved on an iPhone or iPad. It now works like all the others.
- **Exports work on Android phones.** A download button could leave a blank tab and no file, especially in browsers inside other apps. Reports and sheets now open on screen, ready to read, save or share.

## v26.18.3

### Improvements

- **The column list closes on its own.** It used to stay open until you pressed its button again. Now a press anywhere else or Escape closes it, and opening one row's menu closes the other.
- **Show all columns or none in one go.** Two buttons at the top of the column list do it for you. A long list now spreads over several columns instead of running off the screen.
- **Table filters fit what a column holds.** The member list, inventory pieces and appointment sign-ups filter dates by day, birth dates by age, numbers by range and choices by name. Sorting follows suit, so numbers and days come out in order.
- **Tables remember your columns.** If you allowed conveniences, your chosen columns are still there next time in the same browser. The privacy notice now lists this once for all tables, so it asks for your consent one more time.
- **Sort and filter on your phone too.** On a small screen, rows become cards showing your chosen columns. Sort and filter controls sit right above them.
- **More station lists sort and filter by every column.** The movement queue, borrowed gear, inventory checks, waiting lists, former members, the board backlog and more now work like the member list. Every column header sorts and filters, and longer tables let you pick their columns.
- **Association and administration lists join in.** The association's members, storage, movements and gear, and the administration's applications, logs and beacon figures sort and filter from every column header. You can choose their columns, too.
- **Unconfirmed applications are easy to spot.** An application whose address isn't confirmed yet no longer looks like any other waiting one. You can tell them apart and filter for them.
- **Beacon figures show accounts and stations.** These two columns were collected but never shown. You can now switch them on in the column list.

### Changes

- **Sorting and filtering live in the column headers.** The separate sort and filter buttons in the movement queue, borrowed gear and inventory checks are gone, and the column headers do the job. The inventory member list forgets its earlier column choice once.

### Fixes

- **Sign-ups sort by date correctly.** A date column in an appointment's sign-ups sorted days as text, so 2 January came before 15 December of the year before. It now sorts by the actual day.
- **Saved member filters keep open questions.** A saved filter for people who hadn't answered a question lost that condition when used again. It now comes back complete.
- **Grouped inventory follows the table controls.** When pieces were grouped by kind, choosing columns, sorting and filtering did nothing. They now apply to every group.
- **Guardians can open an event's files.** A guardian could see a restricted event through their child but was refused its files. Now they get the files whenever one of their children may see the event.
- **Downloads work on an iPhone.** A download button on an iPhone or iPad could leave the file unsaved without a word, especially in browsers inside other apps. The file now opens the share sheet, so you can save it to Files or send it on.

## v26.18.2

### New Features

- **People lists with the columns you choose.** The guest list of an appointment and the register show whatever your station wants next to each name. Save a column choice under a name and take it away as a printed sheet or a table, always limited to what you may see.

### Fixes

- **Yearly appointments land on the right day.** For stations whose clock runs ahead of the server's, a yearly appointment could fall a day early. Ember now reads the day on your station's own clock.
- **Registration no longer closes a day early.** The deadline check asked the server what day it was, not the station, so stations ahead of the server closed their lists a day too soon. It now asks the station.

## v26.18.1

### New Features

- **Give a partner station places of its own.** A shared appointment can set aside places for another station, with or without a limit. That station then picks its own people for them, no confirmation needed.
- **Choose when your notifications are mailed.** Under Station → Mailing you set the times your gathered notifications go out, say seven in the morning and two in the afternoon, or every hour. Want one mail a day? Now you get exactly one.

### Improvements

- **Changed your mind? Take your sign-off back.** For five minutes, the message after giving up a place lets you undo it, and you keep your old spot in the queue. The last two buttons that gave a place away in one press now ask first, like all the others.
- **Signing up too late tells you who can help.** After the list has closed, you used to get a bare failure. Now Ember says the list is closed and that the organisers can still add you.
- **Partner station guests are treated like members.** Their sign-up is accepted at once where no confirmation is needed, and refused where sign-ups are closed or the appointment is off. Signing off keeps the record, so the host can tell who left from who never answered.
- **Three more pictures for your gear.** Headphones, a key and a stapler join the choices under Equipment and General.
- **Following a cluster brings its partners' news.** Cluster notifications were gathered but never mailed, so followers heard nothing. They now go out with the same times and settings as a station's.
- **Appointment files open right where they are.** Press a file to see it, so finding the map among four sheets no longer means saving all four. Saving still has its own button.
- **Files show what's inside.** Pictures and the first page of a PDF now appear next to each file, in an appointment's list and in the media library. No more rows of identical icons.
- **Appointment and waiting list choices get one row each.** Both used to take the whole list in one box, so a choice containing a comma quietly became two. They now use the familiar editor, with one reorderable row per choice.
- **The delete button on appointment fields stays put.** It used to wander into the middle of the panel when its row wrapped. Now it sits at the top right of its field.
- **The news list reads like a list again.** Long entries are cut to a few lines with an invitation to read on. The full text waits on the entry's own page.

### Changes

- **Notification mail goes out on the hour at the earliest.** Installations set to gather notifications for less than an hour now mail at the top of the hour. Operators who relied on a shorter wait should take note.

### Fixes

- **News from the Ember team now carries its mark in the list.** The Ember logo showed only once you opened such an entry, so the list couldn't tell it apart from your station's own news.
- **Deleting an account no longer reports phantom problems.** It listed data it couldn't clear up, even though nothing was left behind. Ember now checks that list against the database itself, so it stays accurate.
- **People who give up a place stay visible.** An unconfirmed place given back used to vanish from the list without a trace, even though a notification went out. Every returned place now stays on the list as withdrawn, and signing up again works as before.
- **Late-evening sheets open for the right day.** Starting an attendance from a repeating appointment read the server's clock, so shortly before midnight it opened the previous evening. Ember now uses the station's day throughout.

## v26.18.0

### New Features

- **Add a picture of the page to a problem report.** Share this tab or attach your own screenshot, then paint over anything private before sending. Nothing is ever captured on its own, and password fields are covered before you even see the picture.
- **Call members by the name everyone actually uses.** Maximilian sets the nickname Max on his profile, and the board, comments, notifications and mails all say Max, while member lists show `Maximilian "Max" Hoffmann`. Members, their guardians and the member management can set it, and a station that prefers register names can switch the feature off without losing any nickname.
- **Documents keep the register name.** Attendance sheets, test protocols, exports and data requests say Maximilian Hoffmann, whatever the screens show. Members who leave keep the name the station knew them by in old entries.
- **Start an attendance without a fitting template.** Next to the templates there is now an empty attendance. It asks which member types and groups to add, then opens the usual pre-filled sheet.

### Improvements

- **Templates tell you what they bring.** Each tile on the new attendance screen names the groups it adds and the questions it asks. No more choosing from memory.
- **The changelog shows each release date.** Every version carries the day it came out, with the exact time on hover. A link at the end shows its changes on GitHub.
- **Show a birth date without the age.** The field has a new switch, on by default. Stations that ask for the age separately no longer show it twice.
- **Calculated fields show only settings that matter.** Nobody types into them, so they no longer ask about required answers, write access, change reports or starting values.
- **Attendance sheets stay clean and readable.** Open items no longer spread out under every row; a row shows their count next to any birthday, and one press opens them. Forty members with a swap each no longer turn the sheet into a to-do list.
- **Call off a swap on the spot.** Whoever runs the check can drop a swap from the sheet after a quick confirmation naming the piece. Only swaps where nothing has changed hands are offered, so nothing needs putting back.
- **Check report pictures before they reach a beacon.** A report with a picture waits in the admin list, where you can paint out more or drop the picture before sending. A switch in the beacon settings sends them straight away instead.
- **Problem reports tidy themselves up.** Thirty days after a report is marked as dealt with, it is deleted along with its picture. Until now they were kept forever.

### Fixes

- **Choosing a profile photo no longer freezes the page.** The page stopped responding while the photo was shrunk, with no sign of progress, so people pressed again. Now a waiting mark appears right away, and the work runs in the background where the browser allows.
- **Saving a profile no longer reports phantom changes.** Unanswered questions and automatic ages showed up as changes nobody made. They are no longer recorded or put up for confirmation.
- **Guardians can read their child's documents.** Consent forms, medical notes and other documents were refused to the very person who signs them. They now appear under the guardian's own documents, read-only, and anything hidden from the member stays hidden from them too.
- **Nobody turns a year older a day early.** On devices in a timezone behind UTC, ages were a day ahead. A birth date now means the same day wherever you are.
- **Ages calculated from a date stay current.** The profile and the answer form showed a number written once and never updated. Ember now works it out wherever it is shown, and nobody can type over it.
- **Members can open their own movement without errors.** The page showed a member four refusals and a form they couldn't use. Choosing the replacement is now offered only to those who may see that store, with a note that the station handles it otherwise.
- **Only the right person can refuse a step.** Every open movement offered the refuse button to anyone looking, so an onlooker could close it by mistake. The panel now shows only for the party whose turn it is, and for those who may override.
- **Late-evening appointments show their sign-ups.** An appointment ending after midnight in your timezone could show an empty registration list, with no way to start a checklist or survey. Ember now reads the day from the station's clock throughout.
- **Renaming a date question keeps its ages.** Age fields pointed at the question's name, so renaming it left the column blank. They now follow the question itself, and existing age fields carry over.

### Changes

- **Names are written the same way everywhere.** Ember used to work out each name in about 160 places on its own, so a list and a picker could disagree. Now there is one rule for every screen.

## v26.17.3

### Fixes

- **Appointment times follow your station's clock.** An evening from 09:00 to 14:00 showed up in the notification feed as 07:00 to 12:00, because the station's timezone was never asked. Feeds, exported checklists and calendar files now use the station's time, and stations without a timezone still read in UTC.
- **Reminders for just after midnight arrive on time.** Ember worked out the days on the server's clock, so half past midnight at the station counted as the evening before. Reminders for such appointments went out a day early, and now they don't.

## v26.17.2

### Improvements

- **Beacons learn what a fault actually was.** A fault used to arrive as just a source, a level and a count, so different failures merged into one nameless row. Its logged details now travel along, with mail addresses removed.
- **Forwarded problem reports include the screen behind them.** The browser, window size, the writer's permissions and the screen's recent calls now come along, with addresses stripped of their query. The writer's name stays at their station, since "The button does nothing" rarely names a defect on its own.

### Fixes

- **Links to other Ember screens work again.** Links written as a path, which is how Ember links to itself, arrived as underlined words that went nowhere. The link in the post-update news entry was one of them, while links to other sites were never affected.
- **System entries no longer show buttons that fail.** Editing, deleting and checking who has read an entry from Ember itself all answered "not found". These buttons are gone, and such entries now carry the Ember logo instead of initials nobody has.
- **Notifications show dates the way you write them.** Reminders and other notifications with a day showed it as 2026-09-19. They now say 19.09.2026.

## v26.17.1

### Fixes

- **Beacon deliveries finally arrive.** Every fault and report was refused as unsigned because a delivery lacked the key to check it, and nothing reached the log. Deliveries now carry that key, and the log says when a beacon turns one away or can't be reached.
- **Send problem reports to a beacon by hand.** Reports written before the switch was turned on had no way to get there. The list now lets you see exactly what would leave and send it, just like the error log.

## v26.17.0

### New Features

- **Hand out files with an event.** Pick or upload the route sheet, a form to bring or the evening's plan, and it appears on the event page ready to download. Each file is either for everyone who may see the event, partner stations included, or just for the organisers.
- **The changelog speaks German and ships with Ember.** The page now reads its entries from your own instance instead of fetching them from GitHub. It works without internet access, and nobody leaves a trace at GitHub to read it.
- **After an update, the news tells you what changed.** Ember writes one entry for the managers with exactly that version's changes. A link leads to the full changelog.

### Changes

- **A new permission for an event's internal details.** It opens the material an event needs and the files kept back for organisers, without allowing changes. Everyone who may edit events has it already, so grant it to helpers who run evenings but don't keep inventory.
- **Write a profile question once and choose who answers.** Members → Configuration no longer has a tab per member type, so each question exists once and you pick any member types and groups to ask it. Required answers, field width and write access can differ per audience, and each form keeps its own order.
- **New questions wait until you choose an audience.** A question reaches no profile until you say who it's for, and the list flags it right on the row. Trial members are now a type of their own, so you can ask them less.
- **Arrange a form right in its preview.** Drag a field into place and pull its right edge to set its width. Drop in a spacer to line up questions just the way you want.
- **Assign several questions to an audience at once.** Tick them in the list and choose a member type or group. Done in one go.

### Fixes

- **Duplicate questions are merged into one.** A question written once per member type showed up twice for people who belong to both, collecting two different answers. On update, same-named questions of the same type are merged, keeping the filled-in answer, and a backup is first written to the data volume.
- **The missing-gear button shows only to those who may use it.** Asking the association for missing gear refused anyone without that right. Now only people who may request gear see the button, while everyone still sees what's missing.
- **Past sheets carry the evening's own date.** A sheet filled in weeks later was listed under the day it was typed, so a July evening sat at the top showing September. Past sheets now show their own date, newest evening first.
- **Problem reports now reach the beacon.** Turning on "Pass problem reports on automatically" saved the choice but sent nothing. Reports now go out as they are written, without the page's query string.
- **Beacons accept what is reported to them.** Unless an instance reported to itself, a beacon refused every delivery as meant for someone else, with no hint why. A beacon now checks deliveries against its own address, the one in `api.baseUrl`.

## v26.16.0

### New Features

- **Sign in a new device from one you hold.** The new device shows a short code and a QR code. You type the code under `Account → Security → Unlock a new device` on a device you're already signed in on, and the new one is in without a passkey, until the session ends.
- **Guardians can sign in and confirm for their members.** The same screen asks whom the sign-in is for, so a child without a password can get onto the machine in the hall. When that child later has to confirm something, the guardian's device answers.
- **Confirm a sensitive action on another device.** If you have no password or passkey on the machine in front of you, the confirmation dialog now lets you confirm on a device you're signed in on. That device shows what you are confirming and answers with its own passkey or password.
- **Print attendance sheets for signing by hand.** The export now asks how the sheet should look: a signature column, a heading or a blank line for one, and spare lines for people not on the list. A sheet for signing leaves the recorded status off, because the paper is what counts.
- **Start any kind of movement, not only swaps.** A wizard asks what should happen, with whom, with which piece and why. It then draws the chain your answers lead to, and tells you plainly when the station has no chain for that combination.
- **One queue for everything under way.** Inventory → Movements lists every movement with its state, whose turn it is and since when. The button on each row names the next step, and a manager can correct a movement when the gear is really somewhere else.
- **Give your gear a picture.** An inventory, and each kind inside a collection, can have an icon and a colour. Every picker, list and queue then shows that picture next to the piece, with its size and identifier.
- **Plan a hand-out instead of doing it now.** Every screen that assigns gear lets you hand the piece over now or promise it. A promised piece stays on the shelf, marked as spoken for, until the hand-over is confirmed.
- **An arriving order starts the hand-out.** Marking an order as arrived no longer puts the piece straight onto the member. It records what turned up and keeps the piece spoken for until somebody actually hands it over.
- **Run a waiting list without any mail.** One switch in the list's settings stops everything it would send, including the "still interested?" reminders. Public registrations arrive without a confirmation link, and nobody is removed for an unanswered reminder.

### Improvements

- **Open items name the step they're on.** On the attendance list and in the quick check, the button next to a member now names its step, the piece and its size. Only what concerns the member in the room is shown, not gear travelling between station and association.
- **Leave the installation's address off PDFs.** A switch in the station settings drops it from the footer of every export, and the attendance export can override it per document. The logo, the station name, the author and the page number stay.
- **Search and walk the gear pickers by keyboard.** Arrow keys move, Enter picks and Escape closes, so a few letters and Enter usually do it. The picked entry looks just like the list: picture, name, size and identifier.
- **Choosing a member works the same everywhere.** One menu shows a face, the name in its group's colour, and a search that is always there. It replaces the dropdowns with hundreds of lines, and three letters and Enter usually do it.
- **See who you've chosen at a glance.** When a screen takes several people, each one appears as a removable chip above the search. Past five they fold away, so a group of fifty stays a tidy field.
- **Movements name whose gear it is.** The queue, the movement page and its chain drawing name the owning station or association instead of saying "owner". A swap also names both pieces: the one going back and the one asked for, with its size.
- **Movements show when they last moved.** Each row now says when somebody last moved it on, next to the day it started. That's how you tell a waiting movement from a forgotten one.
- **Replacements come only from pieces you can give.** The search for the arriving piece now offers only free pieces of the same inventory, owner and kind. Nothing held by a member, missing, or promised to another movement shows up.
- **Names in the inventory lead to their gear.** The gear lists, the queue and the overview all link a member's name to their inventory. Several of them didn't before.
- **Tags are simply called tags now.** The old German word for them is gone from the interface. The tag column only appears on an inventory where something actually has a tag.
- **Register publicly without an e-mail address.** On a waiting list that sends no mail, the form no longer insists on an address. The registration reaches the station right away and waits to be accepted as before.

### Changes

- **Movements replace the exchange page.** Everything the exchange list did now lives on Inventory → Movements, for every kind of movement. The five old swap statuses are gone too, because a movement's step shows where the pieces really are.
- **The exchange right becomes the movement right.** Everyone who had the old right keeps the new one, for stations and associations alike. Nothing has to be granted again.
- **The inventory menu is regrouped.** Daily work is at the top, the checks sit together, and the settings pages share one heading. Lending has its own, and the pages themselves are unchanged.

### Fixes

- **Creating a member now offers guardians first.** The manager step promised existing managers but listed every member as unsearchable cards, which was unusable on a real station. It now opens on the guardians, with every other kind one filter away.
- **Inventory columns line up with their headings again.** The tags column had a heading but no cells, so every value slid one column left and the holder showed up as a tag. The columns line up again, and a new tag shows without leaving the page.

## v26.15.2

### Fixes

- **Switched inventories accept their own exchanges again.** After an inventory was switched between the station's own gear and the gear of the body above, exchanges could be refused, and pieces sent back outside Ember could linger as stock. Switching now updates the owner of every piece it holds except borrowed gear, and the upgrade tidies existing records and clears that dead stock.

## v26.15.1

### Improvements

- **Missing answers show up after you sign in.** When an appointment gains a question after you registered, the page you see after signing in now lists it, for you and everyone you answer for. The button next to it opens the appointment, where you give the answer.

## v26.15.0

### New Features

- **Report faults to a beacon.** A beacon is an Ember instance that others send their faults to, so you see whether thirty installations hit a fault or just one. You switch it on under Administration, it points at `https://ember-panel.de` by default, and while it's off nothing leaves your instance.
- **Forward errors by hand or automatically.** Each entry in the error log has a send button, you can tick several at once, and one switch forwards new ones as they appear. You always see the exact contents first, since an error can quote an address or a name.
- **A daily count of what your instance holds.** Once switched on, your instance reports daily how many accounts, stations, members and pieces of equipment it has. The counts travel as ranges like `10-50` with identifiers used for nothing else, so they say how much without saying whose.
- **Run a beacon yourself.** Switched on, your instance collects what others report under Administration. Each fault shows how many installations met it and in which versions, you can write back to the sender, and the daily figures build a picture over time.
- **Documents get their own rights.** Reading and editing documents can now be granted separately from the member list. Whoever keeps the test certificates gets the store without seeing the people, and documents about a member stay behind their own rights.
- **The document store becomes a module.** A station that doesn't use it can switch it off. That hides the page and closes its addresses, just like the other modules.
- **Paperwork that arrives by mail files itself.** Under `Manage → Mail import` you connect a mailbox once, name the senders you trust and the file types you want, and every matching attachment becomes a document. The message text is never read, stored or searched, only the sender, the subject and the files.
- **A log of what became of every attachment.** Each attachment is listed with its outcome, from "filed" and "already there" to "sender not allowed" or "no room left". If a rule quietly files nothing, this is where you find out why.
- **Documents show where they came from.** A document that arrived by mail shows the sender's address and the day it arrived. That replaces the uploader it never had.

### Improvements

- **Move a movement onto the right flow.** A movement on a flow meant for someone else's gear walks steps that make no sense for its piece. Inventory managers can now switch it over from the movement's page and pick the step it continues on.
- **Reset a flow to its prepared version.** An edited or outdated flow can go back to the prepared version. Movements under way move to the matching step, and where that isn't clear, the page asks you instead of guessing.
- **Flows are drawn, not just listed.** Each flow now shows the piece's journey in columns for owner, post, station and member. Outgoing and arriving pieces get a line each, so you finally see both halves of an exchange.
- **Moving an exchange on starts at the next step.** The step it would take anyway is already filled in, so usually one press does it. Later steps are still there for an exchange that skips one.
- **Show just the station's own paperwork.** A switch on the documents page narrows the list to documents that belong to nobody in particular, like a test certificate or a service agreement. Before, they were mixed in with every member's documents.

### Changes

- **Each flow says what it is for.** The table that pointed every combination of owner, purpose and counterparty at a flow is gone. Each flow now carries that line itself, so a flow can no longer be pointed at the wrong case.

### Fixes

- **The station's own gear takes the right flow.** In an inventory holding both the station's gear and that of the body above, the station's own pieces followed the other flow, with steps like sending them away. The flow now follows the piece instead of the inventory.
- **Labels on the exchange list line up when wrapping.** On a narrow screen, the second of two labels sat slightly indented under the first, with no space between them. They now line up properly.
- **The error log is readable on a phone.** Messages were cut off at the screen's width, often after a single word, and only a hover revealed the rest. Messages now wrap onto the next line instead.
- **Probing bots no longer flood the error log.** Instances on the internet get asked for files and admin pages of other software, and each refusal was logged as a fault. They are still refused, but no longer bury the faults worth reading.
- **Empty error entries no longer offer to open.** Entries with a single message and no stack trace still showed an arrow and opened an empty panel. They don't invite a click anymore.
- **Deleting a member now tidies up their documents.** Their documents stayed in the store, tied to nobody, where no one could reach or remove them. Deleting now follows the same rule as marking a member former: what was marked to keep stays, the rest goes.
- **Mistyped addresses no longer send you to sign in.** Visitors who weren't signed in and followed a broken link landed on the sign-in page instead of being told the page doesn't exist. The "not found" page now stays put.
- **Exchanges can always be moved on again.** When the step asking which piece arrived wasn't the last one, the screen had no field for that piece, so every attempt ended in an error. The field now appears whenever the exchange stands on that step.

### Security

- **Unlocking a device asks for proof every time.** Approving a device now asks for a fresh passkey, second factor or password, and each answer covers exactly one approval. Operators set how long it counts with `auth.twoFactor.localProofFreshnessSeconds`.
- **A device let in by another can't let in a third.** Confirming on another device never satisfies the approval screen, and a session signed in that way can never approve anything. Without both rules, two devices could vouch for each other in a circle forever.
- **Ending all sessions also stops pending approvals.** Up to ten minutes can pass between approving a device and that device picking up its access. Ending every session, changing your password or an administrator reset now cancels anything still waiting.
- **Confirmations count only for what was shown.** The approving screen names the kind of action in Ember's own words, and the answer counts for that kind alone. Being talked into confirming something small never confirms something large.
- **Every sign-in from another device is recorded and announced.** The record names the account, the device and who approved it, and outlives the request itself. You also get a mail saying a device was signed in and that nothing was stored on it.
- **Calendar feed keys stay out of the statistics.** A personal calendar feed's address contains the key that opens it, and the endpoint statistics recorded it as is. That key is now replaced by a placeholder, just like numbers and identifiers already were.
- **Nothing leaves your instance unasked.** Every beacon switch stays off until an operator turns it on under Administration. Forwarded reports carry no names, members, stations or query strings, only the operator's own contact so the beacon can write back.
- **Mailbox passwords are encrypted, and only trusted senders file.** Mail import needs `storage.credentialEncryptionKey`, and without it a mailbox can't be saved. A rule accepts one address or one whole domain, never anything wider, and a rule without a sender accepts nothing.
- **A mailbox can insist on signed mail.** Anyone can write any sender address, so a mailbox can be set to file only mail with a valid signature from that domain. It's off by default, because senders who don't sign would be refused along with the forgeries.
- **File types are read from the file itself.** An attachment's type comes from its first bytes, not its name or the mail's claim, and a mismatch is refused. Operators can bound the feature with `mailImport.enabled`, `mailImport.minimumIntervalMinutes`, `mailImport.maxAttachmentsPerCycle`, `mailImport.timeoutSeconds` and `mailImport.logRetentionDays`.

## v26.14.4

### Improvements

- **Assign members from the guardian's own page.** A guardian's page used to list their members without letting you add any. Now you can link them from either side, and naming a guardian on a member's page works as before.
- **Two more ready-made sets of member fields.** The member settings now offer a first aid course, with its date and hours. There is also the portable pump operator qualification with its date.

### Changes

- **Both member pages handle guardians the same way.** The guardians tab in the member editor had its own layout and name. It now looks and reads like the one on the member's page, naming the people instead of the pairing.

## v26.14.3

### Improvements

- **Giving back a place is now recorded.** Someone who gives back a confirmed place now shows as withdrawn, next to those who were turned down, instead of vanishing from the list. A registration still waiting for an answer is removed as before, since no place was given yet.
- **Confirmed places are withdrawn, not declined.** Once you're confirmed, answering no records a withdrawal. The list keeps apart those who never had a place and those who gave one up.
- **Registrations show when they were made.** The date next to someone on the registration list is now when they signed up or said no. Before, it showed the appointment's date, the same for everybody.
- **Registration notices name the member.** The notice on the dashboard and in the feed now says who signed up or gave their place back. Organisers used to see only the appointment and the new answer.
- **Attach several files to a ticket at once.** The attachment picker on a ticket now takes more than one file at a time. It used to take a single file each time you opened it.

### Fixes

- **Missing pages now show Ember's own error page.** A mistyped or outdated link ended on a bare page without Ember's navigation. Missing addresses and other errors now get Ember's own page, with the usual header, footer and a way back to the start.
- **The file picker works for board-only members.** Members who only work on boards got a permissions error when adding a picture to a ticket, and saw not even their own files. The picker now asks only for what they may read, so it opens with their files in it.
- **Withdrawals no longer show untranslated text.** The dashboard notice about someone giving back their place printed an internal word instead of a German one. It now shows a proper translation.

## v26.14.2

### New Features

- **Give an attendance sheet its own worth.** When hours stand in for pay, the clock isn't always what's owed, so a sheet can carry its own figure. A weekend from Friday evening to Sunday afternoon can count as sixteen hours instead of forty-six, and partial attendance counts its share.
- **Record a sheet over several days.** Choosing a template now asks when the sheet runs, with a date on both ends, prefilled from now and the template's last length. You can finally record a camp or a weekend duty without an appointment behind it.

### Improvements

- **Sheets own their start and end times.** You can correct both on any sheet, even one opened from an appointment. Where they differ, the appointment's times show alongside, ready to take back with one click.
- **Multi-day sheets show the day beside every time.** Arrivals and departures carry their day in the sheet, its export and the report. The report also names both days of the sheet.
- **Appointments can't end before they begin.** The editor points it out next to the end, and such an appointment is refused. Running past midnight or over several days still works, because that's what a camp is.

### Changes

- **New releases are checked for hourly.** Your instance used to ask every six hours, so a release could go unnoticed for most of a day. `updates.checkIntervalHours` still sets any span between an hour and a week.

### Fixes

- **Times on older sheets stay on their day.** Entering an arrival or departure on an earlier sheet put it on today, so the hours came out as zero or several days. The time now belongs to the sheet's own day.
- **Correcting an older sheet no longer moves it.** A corrected start or end landed on today, dragging the sheet along, sometimes into another month of the report. Both ends now keep their date and stay put.
- **Sheets from repeating appointments get the right date.** Such a sheet took the day the series was first set up, so it counted in a long-gone month. It now runs on the day it is opened for, with the appointment's time and length.
- **Sheets from templates now have a length.** They began and ended the moment they were opened, so everyone counted zero hours unless times were typed by hand. Opening one now asks when it runs.

## v26.14.1

### New Features

- **Appointments ask for missing answers.** When a question is added after you signed up, you and whoever answers for you are told once, and the registration is marked on the dashboard and the organiser's list. You can answer on the appointment's page, even after registration has closed.

### Improvements

- **Every question uses the same answer field.** Dates open a calendar, times a clock, long answers get several lines, and member questions a searchable picker. This now works the same on the profile, attendance sheet, appointment, waiting list and equipment fields.
- **Answer choices get one line each.** Wherever a question offers choices, you now add, reorder and remove them in a list instead of one text box. It's the same list on appointments, attendance sheets, member settings, surveys, quizzes and equipment fields.
- **Correct your answers from the appointment.** A button next to your answer opens the questions again, for you and whoever answers for you. Before, only the organiser could fix a wrong answer.
- **Member pages show whom they look after.** For someone who looks after others, the guardians tab now reads Managed Members and lists them, each linking to their page. It used to ask a question that didn't apply to them.

### Changes

- **Answers must fit their question everywhere.** A date field takes a date, a choice one of its options, and a number stays in its printed range, on every screen that asks questions. Only what a save changes is checked, so older answers stay exactly as they are.
- **Organiser-only questions are now asked at sign-up.** That setting used to hide the question from the sign-up form too, so the person who could answer never saw it. The answers stay readable only to the organisers and the household they concern.

### Fixes

- **Attendance fields offer more than one choice again.** The settings asked for a comma-separated list but saved by line, so "A, B, C" became one single choice. Choices now go one per line, and existing fields keep theirs.
- **Default values are checked where you set them.** A choice could get a default outside its options, or a number outside its range, and someone else got the error later. Such a value is now picked from the choices and refused right where it is set.
- **Appointment fields only take answers they offer.** A field offering red or blue accepted yellow, and a date field accepted a word, and both spread into every list and export. Appointment fields now check what goes into them.
- **Editing appointment questions keeps the answers.** Moving, adding or correcting a question rewrote the whole set, and every registration lost its answers. A question that keeps its name now keeps its answers too.
- **No more empty answer boxes on appointments.** When nobody in the household had answered and registration had closed, an empty block sat there with nothing to press. It's now left out when there's nothing to show or give.
- **Attendance checks name only swaps you can settle.** Next to a name stood every running swap, even ones with the piece on the shelf or in the post. Now only swaps where the member holds the piece, or is about to receive one, are named.
- **Sheets from appointments include the named people.** People named in an appointment's answers, for a field that adds them to attendance, only appeared once someone filled the sheet from the appointment. They are now marked present as soon as the sheet opens.
- **Trial evenings on waiting lists count correctly.** The count only rose when someone pressed present by hand, and removing a mark didn't lower it. It now reads straight from the attendance sheets.
- **Tabs scroll on narrow screens.** On a phone, page tabs squeezed together until labels overlapped and the last ones were out of reach. The row now scrolls sideways.
- **The storage overview names every file type.** The media library, member documents, inventory receipts and station logos showed as internal codes in the same grey. They now have proper names and colours, and the media library's quota is labelled for what it limits.

## v26.14.0

### New Features

- **Attendance shows what is still open per person.** Next to each name you now see that member's running swap, a found item waiting for them, or a birthday in the last six days. If you hold the rights, you can move the swap on or hand over the found item right there, and anyone who only ticks names sees the list as before.
- **The footer tells you about new versions.** Station administrators see the new version number and a link, so no instance gets stuck on an old release. The check runs every few hours in the background, and `updates.enabled`, `updates.checkIntervalHours` and `updates.repository` let operators switch it off or tune it.
- **Attendance sheets close once the evening is old.** After seven days, or whatever span the operator sets, a sheet takes no more changes. Whoever manages attendance can reopen it or close it early, so a late correction is a deliberate choice.
- **Connect two instances with a single code.** An invite code knows the instance that made it, so entering it sets up the connection on both sides. Each code works once, and if the instances cannot reach each other or do not fit together, the dialog tells you which.
- **Appointments say which equipment they need.** Next to a date's sign-ups you list the gear it takes, by piece, by kind or by count, and Ember checks it against what your station really has. A weekly date is planned once for every evening, and each line says how many hours before and after the gear is away.
- **Borrow what a date is short of from partners.** A button on a missing line shows what partner stations offer, counted by kind, and lets you build one list across several of them. Everything is counted again just before sending, and the button says how many requests go out or why it cannot send.
- **Turn the people coming into a list.** From an appointment's sign-ups you can make a checklist or a survey for exactly the people booked on that evening. The dialog tells you how many come across, and anyone signing up later is added by hand.
- **Every piece in a mixed drawer has a kind.** In an inventory of blue radios, green radios and a charger, each piece can carry a kind next to its name, so "the blue ones" can be counted and requested. A tidying screen under Inventory lists the names in use with counts, and merging two spellings fixes them everywhere.
- **Tie a checklist to one evening of an appointment.** Instead of roles, groups or names, a list can point at an appointment and a date, and Refresh brings in everyone who booked since. People who cancel keep their row, marked as no longer belonging, and you can change what a list is made of later.
- **You decide what partner stations may borrow.** Offer a whole inventory, one kind in it or a single piece, to every partner or only named ones, and the narrower choice wins. Gear that belongs to the body above your station is never offered, and Inventory, Lending shows everything you offer and keep.
- **Members check their own equipment from home.** Members go through their recorded gear piece by piece: still have it, no longer fits, broken, missing, or something nobody wrote down, with real sizes picked from a list. Guardians answer for those in their care, and the station reviews each line before the result becomes a proper check signed with both names.
- **Waiting list invitations name a real evening.** An invitation now picks an upcoming appointment and date, adds an arrival time and goes out as a proper mail, without putting anyone on the roster. People answer from the link without signing in, and you see their answer next to their entry.
- **Mark several wiki entries and act at once.** A folder now has a marking mode with a box per entry, and shift marks a whole range. You can move the selection, change tags or send it to the trash, and Ember tells you afterwards which entries had to stay and why.
- **Deleted wiki entries wait in a trash.** A deleted folder or article goes to a trash first, and anyone allowed to delete it can bring it back with its references, tags and history. The trash shows the storage it still takes and can be emptied, and it clears itself after `trashRetentionDays`, thirty days by default.

### Improvements

- **Date columns filter by year, month and day.** Instead of one checkbox per date, the filter offers years and months that open down to the day. You can also set a range, in the member list and the inventory tables alike.
- **Birth dates show the age and filter by it.** Birth date columns now show the current age next to the date. The filter can bound the current age or the age at the end of this year.
- **Step back one piece in a quick check.** A wrong tap during an equipment check used to stick until you closed and reopened the whole check. Now one step back returns to the last piece and clears what you said about it.
- **Set setup link lifetimes in the browser.** How long a setup link stays valid used to live only in the server's config file. You now find it under Settings, Security, Tokens, from one to thirty days.
- **Add members now, invite them later.** Adding a member always sent the setup mail at once, which is wrong for a year group entered before term starts. Single entry, batch entry and spreadsheet import can now hold the mail back, and the member list sends it with a fresh link when you are ready.
- **The member list shows expired setup links.** A member never written to and one whose link expired looked the same. An expired link now has its own symbol and label, and the resend button turns red.
- **Borrowed gear counts towards an appointment's needs.** Fourteen needed, ten your own and four borrowed now reads as covered, not as a shortfall. If two dates plan the same trailer for one weekend, Ember shows the clash and names the other date.
- **Partner offers are counted by kind.** A partner's offer used to show one number per drawer, which said little for a drawer of radios, chargers and cases. It now shows a row per kind, so you can ask for exactly four blue ones.
- **Requests say which date they are for.** A lending request now carries the name of the date it was collected for. Nothing else about the appointment travels with it.
- **Inventories are either uniform or a collection.** Requirements, procurements and exchanges only make sense for many copies of one thing, so they no longer show up on a drawer of odds and ends. Every inventory starts as uniform, and nothing changes until you mark one as a collection.
- **See at a glance how much of a size is free.** The size table now draws a thin strip under each size, split into free, held by members, and on loan or missing. The counts are written in its label too, so you do not need to tell the colours apart.
- **Announce an appointment from its own page.** The menu next to a date opens the news editor with a ready draft, so you never type a weekly date out twice. If only some members may see the appointment, only they can read the entry.
- **Split an inventory and keep the history.** You can now move pieces into another inventory of your station, with their number, holder and history intact. Before, you had to delete and re-enter them and lose all of that.
- **Custom fields for one kind or one piece.** A field used to apply to a whole inventory, and a call sign makes no sense on a charger. A field can now belong to one kind or one piece, with values still kept per piece.
- **Start a procedure from who is coming.** The sign-ups menu can now prepare one shared procedure for everyone booked on that evening, with steps from a template and the evening as due date. It links back to the appointment, and pressing the entry again opens the existing one.
- **Borrowed equipment lives in your inventory.** Gear a partner lends you appears under Inventory as soon as the handover is recorded, on its own shelf. You can put it in a container, hand it out and check it, and it disappears again once returned.
- **Lent-out gear names the partner holding it.** Equipment on loan only said it was with a partner, so you had to open the request to find out which. The partner's name now sits on the equipment itself.
- **Give equipment keywords across inventories.** A radio, its charger and an antenna stored elsewhere can now share one keyword. You pick keywords from those your station already uses, and the stock list gains a column to filter by them.
- **Keywords find gear at partner stations too.** A keyword search now also asks every partner that lends to you, regardless of capital letters, and each station keeps its own spelling. An association can recommend keywords, which sit beside your own and never replace them.
- **Take attendance questions straight into an appointment.** When an appointment names a sheet, the sheet's fields are offered above the appointment's questions, one at a time or all at once. They stay linked, so answers land on the sheet, and the appointment templates offer the same.
- **Filter and sort exchange requests.** The list now has a member search plus filters for inventory and status, and it opens on the requests still running. You can sort by member, inventory, status or date, and the export takes exactly the filtered rows.
- **Partner requests keep the days you searched.** The free count next to a partner's inventory depends on the days you asked about. The request form now starts with those days and counts again when you change them.
- **Empty partner searches explain themselves.** The screen looked the same whether no partner shares anything or everything shared is booked. It now tells you which, without naming inventories a partner keeps back.
- **Take back a claim on a found item.** A claim made by mistake used to stick until someone deleted the entry. You can now release your own claims and those for people in your care, and the lost and found team can release any.
- **Claim a found item for someone in your care.** A parent picking up a glove for their child had to claim it in their own name. The dialog now asks who it is for and records that name.
- **Add a picture to a find later.** An entry reported without a photo could never get one. Anyone who may report a find can now add a picture to it.
- **Move wiki folders and articles.** The menu next to an entry now offers Move, to any folder at any level, and a folder takes its contents along. Links to a moved article keep working.
- **Moves tell you who will see the entry.** The target folder decides who can read an entry, so the dialog shows its reach now and after the move. A move that would clash with a name, go into itself or share further than allowed is refused with the reason.
- **Deleting a wiki article names the pages using it.** Public pages can show a wiki article, and deleting it used to leave a placeholder there unnoticed. The dialog now lists those pages first and warns more clearly if one is published.
- **Wiki articles show what links to them.** Below an article's further reading, a second list now shows the articles that link to it. Pages you may not open are left out.
- **Further reading searches the whole wiki.** The picker used to offer only top-level articles, matched by title and description. It now searches every article including its text and shows its folder, so same-named articles are easy to tell apart.
- **Comment notices open on the comment.** A reply or mention used to land at the top of the page, leaving you to hunt through the thread. It now scrolls to the comment and highlights it, or tells you if it is gone.

### Security

- **Waiting list links are no longer cached.** The page a family opens from their mail shows a name, an address and their place on the list, yet any cache on the way could keep it for an hour. It is now stored nowhere, so it also always shows the latest answer.

### Changes

- **Tickets go only to people who can work on them.** The assignee list offered every station member, even those the board keeps out. It now offers people who may write on the board and board administrators, and existing assignments stay as they are.
- **The first start asks for a real address.** New installations used to get an administrator with a made-up address, so password resets and security notices went nowhere. The account is now called `admin` with no address, and the first sign-in asks for a real one right after the new password.
- **Running instances ask for that address once.** An administrator still carrying the made-up address from an earlier first start is asked for a real one at the next sign-in. As soon as it is set, they are in.
- **No mail set up means no waiting for confirmations.** Without mail, new accounts stayed unverified, address changes never applied and stations could not be deleted. Without a mail provider these confirmations now count as given, while public demos and password links are unaffected.
- **Fixing an unreadable address needs one confirmation.** An address change asks both the old and the new address, which never finishes if the old one is made up. In that case only the new address is asked now.
- **Administrators can correct each other's addresses.** Instance administrators can open any account's details without joining that station, so a mistyped address can be fixed. Your own address still needs a confirmation, so a stolen session cannot move your account.
- **Attendance expects only the groups on its sheet.** People an appointment was not open to used to show up on the sheet already marked off. Who is expected now follows the sheet alone, while sign-ups and refusals still show next to each name.
- **Two first steps now show rather than ask.** The appointment step now explains the two kinds, the ones you sign up for and the ones you are simply expected at, without signing you up. The profile step is offered even when nothing is missing, because a full profile is still worth a read.
- **Busy button rows become one button and a menu.** Attendance sheets, wiki files, question catalogues and test sheets showed up to seven buttons, filling a phone screen. Each now shows its main action and keeps the rest in a menu beside it.
- **Partners see nothing until you offer it.** Partner stations used to browse every inventory you had. From this version nothing is shared until you offer it, by inventory, kind or piece.
- **The start page takes you back where you were.** When signed in, it opens the area you last worked in: your station, your association or the administration. The Ember logo and `/?home` still open the start page itself.

### Fixes

- **Yearly appointments near midnight export on the right day.** The export read the repeat day off a different clock than every other date, so such an appointment could land a day off. It now uses the station's own clock, like the rest.
- **Wiki search no longer comes back short.** Search fetched only as many articles as it showed, then removed restricted ones, so results could be thin or empty. It now looks further ahead and fills the page, in member search, public wiki and partner searches alike.
- **Quick ticket edits are no longer lost.** Moving straight from one field to the next could silently drop the second change, mistaken for a double click. The later change now goes out as soon as the first one is saved.
- **Members without an entry can be marked again.** Someone who joined a group after the sheet opened showed "no entry" with nothing to press. Their row now has the usual buttons, and nobody is offered for evenings before they joined.
- **Members without a login no longer get a fake address.** Adding someone who never signs in, usually a child, gave them a made-up address ending in `.local` that showed in every list and export. Such members now have no address, and anything about them goes to their guardians.
- **Guardians added with a member can sign in.** A guardian added from a member's page or during member entry was saved as an ordinary member and could not sign in. They are now recorded as guardians, with the right to sign in and act for those in their care.
- **Anyone with someone in their care can act for them.** Only the guardian member kind could act for others, so a helper or manager given a child could not, and there was no way to grant it. It now comes with having someone in your care and ends with the last one.
- **Quick checks no longer offer swaps that cannot happen.** A piece already on its way could not be swapped, but you only learned that after filling in the swap. The check now shows what is running and offers the swap once it is done.
- **Missing equipment stays missing when the record is corrected.** A piece reported missing went quietly back on the shelf when a check corrected the record, so the station counted a jacket nobody could find. It now stays missing with its note until someone says it turned up.
- **Gear the association took back leaves your stock.** After a correction, a piece owned by the body above your station went home but still showed in your stock and counts. Both now check who owns a piece first, like the availability figures already did.
- **Failed answers for a household now stay visible.** When answering for several children at once and one failed, the next answer wiped the error, so everyone looked signed up. The error now stays until the next answer.
- **Checks finish when two of the same are missing.** If a member should have had two of something and had neither, recording both gaps was refused and the check stayed open. Both gaps can now be recorded.
- **Requests for four now get four.** Each piece set aside for a line replaced the one before, so asking for four radios returned one. Every piece now counts, and the inventory overview follows.
- **Trial attendance counts actually go up.** The waiting list compares a trial member's evenings with the station's target, but the count stayed at zero. Being marked present now counts, while taking someone on remains the station's decision.
- **Deleting a wiki folder frees its storage.** The folder vanished, but its uploaded files stayed in storage, so the space used never went down. Deleting a folder now removes those files too.
- **Narrowing a shared news entry now withdraws it.** An entry narrowed to a few groups after sharing kept travelling to partners. Partners are now checked against the entry's own audience, as appointments already were.
- **Gear owned above your station is not lent on.** Your station only holds such gear, so lending it sent it where its owner never agreed. Only your station's own gear is offered now, and a body running its own station still lends freely.
- **Switching off lending for a partner works.** The setting was ignored, so the partner kept browsing and requesting. Lending now respects it, like shared wikis, quizzes and boards do.
- **Requests can only name the lender's own gear.** A request's inventory and piece were taken on trust, so approving it could reserve gear on another station's shelf. Ember now checks them against the lender's own stock first.
- **Gear already lent cannot be promised twice.** Approving a request for one piece reserved it without checking where it was, even if it was already out or missing. Only gear truly at hand for those days is now reserved.
- **Deleting a station group explains what blocks it.** When a stock requirement counted at that group, deleting it failed with a bare error. It now names what is in the way.
- **Procedure due dates are saved again.** Picking a due date made the whole save fail silently, so the procedure never became overdue. The date now saves, and shows again when you edit.
- **Procedure notices open the procedure.** Being added, a ticked step, closing or reopening all sent links to the dashboard. Each notice now opens the procedure it is about.
- **A guide for notifications on your phone.** The help used to list a few reader apps to choose from. It now walks you through one route to the end, with Feeder on Android and NetNewsWire on iPhone, and says why it is worth it.
- **Attendance checks reach everyone the sheet expects.** The check skipped people who joined a group after the sheet opened, and was missing when that was everyone. Filling the sheet from its appointment now adds them too.
- **Late appointment answers still reach the sheet.** Filling a sheet from its appointment now picks up answers given after the sheet was opened. Fields the sheet already answers stay as they are.
- **Dates and times are written properly everywhere.** Sign-ups, the dashboard, planner breaks, the attendance report and date answers showed 2026-10-12 instead of 12.10.2026. They now match the rest of Ember.
- **Late evening appointments show the right day.** The page took the day from London's clock and the time from yours, so half past midnight showed on the evening before. Both now come from the same moment, in the calendar, dashboard and day-only deadlines too.
- **Weekly appointments show today on their own day.** Opening one on the day it happens showed next week's date. Today now counts as long as this evening is still ahead.
- **Form, test and waiting list times stay put.** Start and end were edited on London's clock instead of yours, so they shifted a little more with every save. They now open and save on your own clock.
- **Quick check exchanges ask about the handover.** The exchange now asks whether the piece was handed over, which decides whether the old piece is already back. Either way the check moves on to the next piece.
- **Pairs of the same piece are counted off.** If someone is owed two shirts, the rows read 1/2 and 2/2, in the list and the quick check. You always know which one you are marking.
- **Save an inventory check before you finish.** Only what you marked is recorded. Pieces nobody looked at keep their last result.
- **Attendance made from an appointment includes everyone.** When the appointment answered one of the sheet's questions, the sheet opened without a single name. Both the answer and the expected members now arrive.
- **First steps no longer point off screen on phones.** On a narrow screen the guide circled a menu hidden past the left edge, so you saw nothing. It now points at the menu button first with a dashed ring, then moves on once the menu opens.
- **Exchange list names are readable again.** Names were always white, which nearly vanished in the light theme. Uncoloured names now match the page, and station colours stay.
- **Notifications no longer outlive their subject.** Deleting a news entry, appointment or form left its notices in the feed, leading to missing pages. They are now removed with it, read or unread.
- **Ticket mentions open the ticket.** A mention in a ticket comment used a number the board pages do not know, so nothing happened. It now opens the ticket at that comment.
- **Deleted comments vanish from notices too.** Deleting a comment left its excerpt readable in unopened notices. Those notices now go with it, and notices about other comments stay.
- **Coloured labels pick readable letters.** Labels in the exchange list always used white text, which was hard to read on pale colours. They now choose dark or light text to match, and update when you switch theme.
- **Admin charts follow the theme switch.** Some chart axis labels kept the colours of the theme they were first drawn in and became hard to read. They now change with the theme.
- **Walked first steps now count as done.** Steps like finding absences, opening an article or trying a training ended by saying the task was not done yet. Walking such a step now completes it, and data-based steps work as before.
- **The training step shows the way again.** The step meant to show where training lives jumped there instead when its navigation group was folded. It now points at the group, like the other steps.
- **Four notification types now have proper titles.** Sign-up closing reminders, pieces sent on by the cluster, missing pieces and cancelled movements reached feed readers titled in capital letters. They are now titled and filed in German and English, like all others.
- **Member answers show names, not numbers.** When an appointment asks who drives or supervises, notices and calendar entries showed an internal number. They now name the members.
- **Setting a password completes your account.** The member list waited for a first sign-in, so people who had set a password got chased with more setup mails. Choosing a password now settles it, and only an administrator-set password still counts as open.
- **Connection codes explain the real refusal.** These codes never expire, yet every refusal claimed they had. Ember now names the real reason, and a station with a pending request can use the code it receives.
- **The second factor window opens in front.** When connecting to a partner, the authenticator window opened behind the one that asked for it. Windows now stack in the order they open.
- **Phone photos work for found items.** Camera photos exceed the five megabyte limit and use formats the server does not keep, and the error said nothing. Pictures are now shrunk and converted in the browser for found items, profile pictures and quiz questions, and any refusal shows the server's reason.
- **Failed uploads no longer file a find twice.** When the picture failed, the entry was already saved, and pressing again created a second one. Ember now remembers the entry, sends only the picture again, and tells you the entry is safe.
- **Handed-out finds take their picture along.** Handing over an item kept its picture in storage for good, counting against the quota. Handing over and deleting now remove everything, notices included.
- **Claiming a find withdraws its announcement.** Everyone kept an unread notice about an item already claimed. The withdrawal now names the exact entry it is about.
- **Station administrators hear about claimed finds.** The notice went only to members holding that one specific right. Broader rights that include it now count for every such notice.
- **Other stations' finds are out of reach.** Claiming or adding a picture to a find did not check its station, unlike opening or deleting it. Both now answer `404` for another station's item.
- **Broken pictures show a placeholder.** A picture that failed to load left an empty card. It now shows a placeholder saying the picture is unavailable.
- **Small print keeps its line breaks.** In the legal sections dialog and the help on importing a question catalogue, two lines ran together without even a space. Each now has its own line.

## v26.13.12

### New Features

- **Put a stuck exchange back on track.** Whoever manages exchanges can now move one to the right status by hand, forwards or backwards, with a reason kept in its history. The gear moves along with it, quietly and without notifying anyone.

### Security

- **Mail secrets no longer land in the log.** At startup Ember recorded its whole configuration, mail password, API key and webhook secrets included, in a log readable from the administration pages. They now show only as set or not set, and you should replace any of these values your instance has used.
- **The container keeps your secrets to itself.** On every start the container printed all its settings into its log, and a restart loop repeated them again and again. Nothing is printed there now, and you should replace any database password, token pepper, mail credential, storage key or second-factor key an affected instance has used.
- **Wiki search shows only what you may open.** Search returned the title and an excerpt of every article in the station, even those kept for the leadership, for one group, or not shared with a partner. It now answers only with articles the reader may actually open.

### Fixes

- **Naming a new manager now hands the station over.** The new manager got full administrator rights, but the station itself stayed with the old one. Now it really changes hands, and the previous manager keeps their rights and membership.
- **Handing over a station finds its managers again.** The list of people to hand a station to always came back empty, even with two managers in place. It now offers every manager except the current owner, and says so plainly if the list can't be loaded.
- **A taken address now gets a clear answer.** Naming a manager with an address that already belonged to someone failed with a bare server error. Ember now says the address is taken, wherever an account is handed out.
- **Calling off an exchange no longer looks like finishing it.** A cancelled or declined exchange jumped straight to Done. It now says Cancelled or Declined, leaves the open lists and can no longer be advanced.
- **One piece of gear, one exchange at a time.** The same piece could be sent out on two exchanges at once, and a step on one made the other seem to move by itself. Ember now refuses the second one and names the exchange that already has the piece.

## v26.13.11

### New Features

- **Sign in with a passkey.** Your device asks for your fingerprint, your face or its PIN, and you're in without a password. Your password keeps working as before until you switch it off under Account → Security.
- **Run your instance fully passwordless.** New accounts then start without a password, and invitations, self-registration and the very first start hand out a passkey instead. You can choose this only after a test mail has gone through, because mail is every member's way back in.

### Security

- **Sensitive actions always ask for fresh proof.** Accounts without a second factor used to pass these checks without being asked; now every account gives what it has, be it second factor, passkey or password. A password sign-in counts as proof for a few minutes, so everyday work asks nothing extra.
- **Member editors can no longer take over administrators.** Whoever could edit members was able to reset an instance administrator's password and change their mail address. Both actions now refuse administrators and ask for fresh proof.

### Changes

- **New operator setting `auth.passkeys.mode`.** It has five steps from off to fully passwordless and starts at optional, so an upgraded instance behaves as before until you move it under Admin → Settings → Security. Ember won't lower it below passkey sign-in while any account depends on one.
- **WebAuthn settings now live under `auth.webauthn`.** Passkeys and security keys share them, and the old place under `auth.twoFactor.webauthn` is still read for one release. The resident-key switch is gone: passkeys always need one, security keys never do.
- **A console rescue for locked-out administrators.** Set `auth.passkeys.printAdminEnrollmentLink`, and the next start prints a one-time passkey link into the log, replacing any earlier one. The link lasts an hour and works once.
- **Attendance expects only the groups its sheet names.** People an appointment wasn't open to used to appear on the attendance already marked off. Now only the sheet decides who is expected, while a sign-up or refusal still shows beside a name.
- **Two first steps now ask you to look.** The step about answering appointments now explains the two kinds, the ones you sign up for and the ones you're simply expected at, without ending in an unwanted sign-up. The profile step is offered even when nothing is missing, because a complete-looking profile is the one worth reading over.

### Improvements

- **Appointments say who may answer them.** When more people may see an appointment than answer it, the upcoming list says so ("Registration only for: ..."). A missing button now reads as intended, not as a bug.
- **The dashboard shows each appointment's category.** Every appointment tile on the dashboard now carries the same coloured category badge as the appointment pages. That covers upcoming appointments, registrations and answers you still owe.
- **Let a signed-in device welcome a new one.** On a device without a passkey, the login screen shows a short code. Enter it under Account → Security on a signed-in device, and the new one creates its own passkey and signs in.
- **See where passkeys stand.** Under Admin → Settings → Security, three figures show who has a working passkey, who still has a password and who can't move yet. Before the passwordless switch, a report counts who it would leave behind.
- **A guide to notifications on your phone.** The help used to list a few reader apps; now it walks one route to the end, with Feeder on Android and NetNewsWire on iPhone. It also says why it's worth it: a notification left inside Ember waits until you next open Ember.
- **Take sheet questions straight into an appointment.** When an appointment names an attendance sheet, its fields are offered above the appointment's questions, one by one or all at once, here and in appointment templates. They arrive tied to their field, so the answers land right on the sheet.
- **Attendance checks reach everyone the sheet expects.** Checking used to cover only names with something already recorded, so people who joined a group later were skipped. Filling the sheet from its appointment now adds them too.
- **Late answers still reach the sheet.** Filling an attendance from its appointment now takes over answers given after the sheet was opened. A field the sheet already answers stays as it is.
- **Quick-check exchanges ask about the handover.** An exchange raised during a quick check asks whether the piece was already handed over. Either way, the check then moves on to the next piece.
- **Two of the same piece count as 1/2 and 2/2.** When someone is owed two shirts, each row in the inventory check shows its number. You always know which one you're marking, in the list and the quick check alike.
- **Save an inventory check part way.** You no longer have to mark every piece before saving. Only what you marked is recorded, and everything else keeps its last result.

### Fixes

- **Attendance from an appointment arrives with its names.** When the appointment answered one of the sheet's questions, creating the attendance broke off halfway and left the sheet empty. Now both the answers and the expected members arrive.
- **First steps point at the menu on phones.** On a narrow screen the guide drew its ring beyond the left edge, so you saw nothing at all. It now points at the menu button first and moves on once the menu is open.
- **Finished first steps now count as done.** Some steps, like opening an article or trying a training, ended by saying the task wasn't settled yet. Walking such a step to the end now settles it.
- **The training step shows the way again.** When its navigation group was folded away, the step jumped straight to training instead of showing where it lives. It now points at the group, like the other steps.
- **Four notification kinds now have real titles.** Feed readers showed four kinds, such as a closing sign-up or a missing piece, under a capitalised internal name. They now have proper titles in German and English.
- **Member questions show names, not numbers.** When an appointment asks who drives or supervises, notifications and the subscribed calendar showed an internal number. Both now show the members' names.
- **The waiting-list confirmation link works.** The button in the waiting-list confirmation mail opened a page that doesn't exist, so the registration could never finish. It now opens the confirmation page.
- **Choosing a password completes the setup.** The member list waited for a first sign-in, so people who had already set a password got another setup mail. Choosing a password now settles the account, and only one set by an administrator still counts as outstanding.

## v26.13.10

### New Features

- **Explainer videos in the help centre.** A new page under Basics plays the whole series in order, from the invitation to the parts the team and the leadership use. It uses YouTube's cookie-free address, so nothing is stored on your device until you press play.

### Changes

- **Forms are now called surveys.** "Form" made a quick two-question feedback round sound like paperwork. Everything under `/station/forms` now says survey, with the address and all answers unchanged, while the contact forms on public pages keep their name.

### Improvements

- **Setting a password signs you straight in.** Invitations and resets used to end at the sign-in form, asking for the new password once more. The link now takes you right into your account.
- **Correct an answer for a member.** Anyone who may edit an appointment can now fix an answer from the list of sign-ups, like a mistyped shirt size. Members can still change their own answers as before.

### Security

- **Password links still ask for the second factor.** Otherwise a link would turn an account guarded by an authenticator into one guarded by a mailbox. Where a second factor is set up it is asked for as usual, and a password change revokes every remembered device.

### Fixes

- **See who signed off from expected appointments.** Appointments without sign-ups only collect refusals, yet their page had nowhere to read them. The page now has an Attendance tab listing who isn't coming.
- **Member questions offer the member picker again.** When answering for a whole household, a question asking for a member showed a plain text box, and lists later showed a stray number. That window now offers the usual picker and shows the question's starting value.
- **Group questions now count as missing.** Someone could skip a question required of their group and still be told their profile was complete. Missing answers now match exactly what the profile screen asks, in the task list and on the dashboard too.

## v26.13.9

### New Features

- **See who subscribes to your calendar and notifications.** A new Monitor entry lists which members have set up a subscription, since when, and when it was last fetched. The key itself is never shown, and only the member can withdraw it.

### Changes

- **Narrowing an appointment limits sign-ups, not visibility.** Appointments for one group used to vanish from everyone else's calendar, so a drill night looked like a free evening. They now stay visible to all and only the group can answer, which also makes earlier narrowed appointments visible to the whole station.
- **Hiding an appointment is its own setting.** A second audience under Restrictions decides who may see an appointment at all, and for everyone else it is gone from calendar, search and notifications. Hidden appointments can't be published or shared with partner stations, and templates carry both audiences.
- **Unanswered appointments leave the calendar after closing.** A subscribed calendar kept showing appointments you never answered, which looked like a held place. Once the closing date passes, no answer counts as a refusal, so only places actually taken stay in your calendar.
- **Procurement belongs to the gap, not the lost piece.** Marking something lost used to offer a replacement order, mixing up two separate questions. The offer now sits at the empty place in the stock-taking, and marking a piece lost only records that it's gone.
- **Your account opens on the profile.** The settings entry used to land on appearance. It now opens on your profile, with appearance one click below.
- **Traffic, statistics and storage moved to Monitor.** They sat under Manage, though you look in on them regularly. All three now live under Monitor at `/station/monitoring`, beside the new feed list, while setting up storage stays under Manage.

### Improvements

- **Upcoming appointments lead with their date.** Date and time now stand above the name, because you read such a list by date. Appointments that need a sign-up say so on the line too.
- **Add notes during the quick stock-taking.** Each piece in the quick walk now has a line for a note. It's the same note the long list and the finished stock-taking show.
- **Stock-taking exchanges ask why.** There's now one exchange button instead of two fixed reasons. Its window offers too small, damaged or your own words, and the suggested size can still be corrected.
- **Attendance sheets start with the times filled in.** Every line now shows the attendance's own times faintly, so nobody types them per member. Only a time you correct is saved.
- **A member's page shows what's still missing.** The equipment tab now names every requirement that isn't covered. It offers a free piece from the store or a new one on the spot, asking the size where needed.
- **Handing out gear offers a search.** Both hand-out dialogs now use the usual picker, with name search and a filter by kind of member. Every such picker is now in alphabetical order.
- **The member list says where a setup mail goes.** Members without their own address are written to through their guardians, which used to look like a mistake. The button now names where the mail lands, and the hourglass explains what to do when nobody can be reached.
- **Setup links now last a month.** Invitation links expired after three days, so one sent before a holiday was dead before anyone read it. The new `auth.setupTokenDays` setting starts at 30 days and can't go higher, while the reset link stays short.

### Security

- **Calendar feeds show only what you may see.** The personal calendar feed carried every appointment of the station, restricted ones included. It now carries only what the household may see.
- **Restricted appointments never go public.** An appointment in a public category became public too, even when restricted. A restricted appointment is now never public, whatever its category says.

### Fixes

- **Expired setup links explain themselves.** An expired link showed the password form and only called itself invalid after you had typed twice. It now says up front that the account is still there and the administration can send a new mail, and a reset link offers to resend itself.
- **Absent members no longer get attendance hours.** Exports and reports gave every member the session's times, so someone absent seemed to stay all evening. Only members who were there get those times now, and the counted hours were always right.
- **Two notification kinds now read as sentences.** A closing-registration reminder and a cancelled movement reached the feed as bare details strung together. Both now read as proper sentences in German and English.

## v26.13.8

### Improvements

- **Answer an appointment from its own page.** Signing up and refusing used to live only in the list of what's coming up. The same answer, with any questions, now sits on the appointment's page too.
- **Answer dialogs from the keyboard.** A dialog now puts the cursor on its confirm button, so Enter is enough. Inside a text field, Shift and Enter confirm instead.
- **Hold Shift to skip the safety question.** Answering the same question row after row teaches nobody to read it. Hold Shift while pressing delete, or any other action that asks first, and it happens at once.
- **Row menus no longer get cut off.** On wide tables a row's menu was clipped by the scrolling list. It now floats over the page, opens upwards when needed and stays inside the window.

### Fixes

- **Group questions reach the member's own profile.** The last version showed such questions in the editing screen, but members never saw them in their own profile. The profile now shows them, and managers also see the questions put to the team.
- **Member names can be corrected again.** The editing screen guessed first and last name by splitting at the first space, so "Millie Jo Harnack" got the surname "Jo Harnack". Your correction was saved but the guess came back on the next visit, and now the correction stays.
- **Appointment totals count only people coming.** Answers kept counting after someone was turned away or called off, which threw off the catering. Only people with a place count now, just like for deciding whether it takes place.

## v26.13.7

### Improvements

- **Take plain pieces into stock with a tick.** When an inventory has no sizes, no fields and no numbers, a stock-taking row had nothing to fill in and was skipped. A tick in the first column now takes the piece anyway and hands it to the member on that line.

### Fixes

- **Signing in no longer freezes the page.** When the terms had changed and the session had been idle for an hour, two checks kept sending each other back and forth. The sign-in had worked all along, and now the page lets you through.
- **Group questions now reach the group.** A profile field set up for a group was saved but never appeared on its members' profiles. It now shows there, beside the questions for their kind of member.
- **Date-of-birth fields show their type again.** A station can ask for a date of birth per kind of member, but the settings only ever read the first one, so the others looked empty. Each tab now reads the date of birth for its own members.
- **The footer shows the new version right away.** After an update the footer kept naming the old version for up to an hour. It is now checked on every visit.

## v26.13.6

### Security

- **News entries are now formatted by the server.** Formatted text sent by a browser was stored and shown to every reader unchecked, so someone reaching the interface directly could inject any markup. The server now builds and cleans the formatted text itself and ignores what the browser sends.

### Improvements

- **Sort the stock-taking table by either name.** The member column now sorts by first name or surname, and a second press reverses the order. The table can match whatever list lies on the desk beside you.

### Fixes

- **Upcoming appointments are in date order again.** Every appointment spanning several days was pulled to the top, so one months away stood above tomorrow's drill. The list now runs from nearest to furthest, and by time within a day.
- **Formatted text keeps its formatting.** Lists lost their bullets, headings looked like plain text and paragraphs ran together. News entries, ticket descriptions, appointment details, help articles and the public calendar now read as they were written.
- **Setup mails are offered only where they can arrive.** Members entered without an address still got a send button, which produced an error. It now appears only when the mail reaches the member or a guardian, and a note says when the current link runs out.

## v26.13.5

### New Features

- **Record a whole inventory in one pass.** A new page opens a table with one row per member, picked by kind of member, by group or all at once. One save records every piece and hands each to the member on its line, and empty rows are simply skipped.
- **Gear checks can correct what they find.** When a member holds something other than what's on record, a correction button records what they really have, from the store or as a new piece. Nothing changes hands, and the piece coming off the record goes back to its owner.

### Improvements

- **New containers are named after their kind.** Choosing a container's kind fills an empty name with it, so a shelf is called "Shelf" without typing it twice. A name you already wrote stays as it is.

### Changes

- **Gear checks no longer change a piece's size.** The two loose fields on every item, for swapping a piece or making one in another size, give way to the correction window. A piece in the wrong size is the wrong piece, so it is replaced rather than resized.

### Fixes

- **Changing a member's email address works.** Saving a new address only sent confirmation links, one of them to the old address, so a dead address could never be fixed. Whoever may edit members now sets it directly, which signs the member out everywhere and informs both addresses.
- **Replacement pieces in a gear check get made.** On an inventory holding the association's gear, the new piece was made as the station's own and refused with an error. It now belongs to whoever owns the inventory.

## v26.13.4

### New Features

- **Give a repeating appointment an end.** A series can now run forever, stop on a day you name, or stop after a set number of dates. After the last one it leaves the calendar, the reminders and the subscribed calendar, and partner stations learn the end too.

### Improvements

- **Template questions can start with an answer.** Every question in a template takes a default, and new appointments open with it filled in and still changeable. A weekly drill with the same meeting point is written down once, not on every date.
- **Picking someone shows faces, not just names.** Adding a member to an appointment or attendance sheet now shows a proper list with pictures, the way groups show their members. It opens as you type, stays out of the way otherwise, and one pick adds the person right away.
- **Editing a chain no longer reloads the page.** Saving, adding, retiring or moving a step now updates only that chain. Everything else stays open and in place.
- **The dashboard shows your answer and takes a refusal.** Upcoming appointments show the answer you gave instead of a reminder, and appointments that expect everybody carry a small refusal button. If you answer for others, you're asked who the refusal is for.
- **The appointment list shows each appointment's kind.** A badge in the category's colour sits beside the name. You can tell a drill from an open day at a glance.
- **Sidebar groups no longer show empty halves.** Someone who can neither write nor sit a test sees the quiz entries alone, without an empty tests heading. A member whose only inventory management entry was the exchange now reaches it directly, beside their own gear.
- **Chains explain problems right where you work.** What a chain is missing shows on the chain itself, and so does the reason a change was refused. Both now read in plain German instead of English.

### Changes

- **Household answers get one line each.** In the appointment list, everyone you answer for has their own row with their answer and a button to take it back. Nothing wraps across one crowded line anymore.
- **An appointment's page leads with what it is.** The category sits as a badge beside the name, and start and end always stand side by side. Which attendance sheet it writes into is shown only to those who may edit it.
- **Sharing with partners pauses until both sides update.** Shared appointments now include when their repetition ends, which both instances must understand. Everything else keeps working, and sharing resumes by itself once the partner has updated.

### Fixes

- **Answering for one child no longer leaves a blank button.** After answering for one person, the choice stayed on them, so the button went blank and could answer for them twice. The choice now resets, and the button waits until you pick someone.
- **The chains page loads the first time.** A new station wrote its starting chains twice at once, and the second attempt left the page empty with an error. Chains and their assignments are now written once, whatever the timing.
- **Two starting chains can be finished again.** The return to the association ended with the gear still in the post, and the issue from the association never confirmed arrival. Both are completed on upgrade, and chains a station changed itself stay untouched.
- **Picking a size from the quick selection works.** Clicking a size picked it and dropped it again, and the list closed as soon as the pointer moved off. A press now picks the size, and the list stays open until you use it, cancel or click away.
- **Questions tie only to the sheet in use.** Every sheet's fields were offered, so two fields called "Beginner instructor" were indistinguishable and answers landed on a sheet nobody opens. Only fields of the sheet in use are offered now, and other ties are dropped on save.
- **The attendance field dropdown fits its box.** It took its width from its longest entry and ran into the field beside it. It now takes the width of the box it sits in.

## v26.13.3

### Improvements

- **Members and trials start without sign-in rights.** Signing in is something a guardian hands to a child, so imports and returns from the former list leave it off. Team members, guardians and managers are unaffected.
- **No more made-up addresses.** Members entered without an address used to get an invented one ending in `.local`, which looked real but reached nobody. The member list, the setup mail and every other place now know plainly whether someone can be reached.
- **Search and filter long member lists.** Groups, tags, the attendance sheet and manual registration now offer a name search above the picker. A filter by kind of member sits right beside it.
- **Event managers can add people after the deadline.** Whoever runs the event keeps its list rather than answering it. Someone who rang up late can still be entered.
- **Attendance skips people the event wasn't open to.** They couldn't see the event, so they now start marked off instead of undecided. Anyone who could see it and said nothing stays undecided.
- **Delete an attendance sheet again.** Whoever may take an attendance may also throw one away. Everything recorded on it goes along.

### Fixes

- **Add people by hand before anyone signs up.** The registration section only appeared after the first sign-up. Now the person allowed to add names has a place to start.
- **The sign-up button disappears after the deadline.** It used to stay, and pressing it only produced an error. Now it's gone once the deadline passes.
- **Appointments offer only the answer they take.** Sign-up appointments offer signing up and, once you have a place, giving it back; appointments that expect everyone offer only a refusal and taking it back. Taking an answer back now removes it, so nobody is recorded as refusing something they never signed up for.
- **Declined members are no longer asked again.** Someone who had declined was still offered a place or a refusal. They aren't asking to come, so there's nothing to decide.
- **Descriptions in the appointment list show clean text.** Asterisks and hashes from the formatting appeared as typed. The opening words now read as the text they were meant to be.
- **Finished onboarding lists leave the dashboard.** A list where everything was done or dismissed kept hanging around. It now goes away.
- **The instance-wide storage overview loads again.** Stations with files from before the media library broke the page with an error. Those files now count toward the media library, and anything that can't be placed is left out.

## v26.13.2

### Improvements

- **Event templates know who they're for.** A template can now name groups, tags and individual members, not just kinds of member, and passes that audience on to each appointment. You pick the youngest group once instead of on every date of the year.

### Fixes

- **Profile headings read as headings.** When reading a profile, every heading showed as an unanswered question with a dash beside it. Headings now look like headings, and short questions sit side by side as on the form.
- **Profile dates read like dates.** A birthday showed as 2019-11-03 wherever a profile was read rather than filled in. It now reads 03.11.2019.
- **Date fields let you type again.** Every click reopened the calendar and sent your typing back to the start. The calendar now opens once, and typing carries on from there.
- **Templates pass on their attendance sheet.** A template naming an attendance sheet didn't hand it to its appointments, so it had to be set by hand every time. It now comes along.
- **New appointments ask each answer once.** A sheet field already filled by one of the appointment's questions was offered again under prefill, and the last one set quietly won. That field is no longer offered there.
- **Prefill names field kinds properly.** The prefill section showed English shorthand for each field's kind. It now uses the same German names as the rest of the product.
- **The width hint no longer crowds its neighbour.** In the event template editor, the sentence under a question's width pushed the next setting out of line. It now sits behind an information icon beside the label.
- **Managers' edits skip their own confirmation.** Changing a watched question on someone else's profile put the change on a list only the manager themselves ever saw. Only members' changes to themselves wait there now, and the history keeps both.

## v26.13.1

### Improvements

- **Map import values to real answers.** When you say what a value in the file becomes, the import now offers the answers the question allows, instead of asking you to type them. Group columns can be mapped too, so a differently named group no longer gets a duplicate.
- **Map each distinct answer once.** The editor reads the whole file and lists every answer once. Thirty identical answers are one line to fill in, and nothing far down the file is missed.
- **Leave single rows out of an import.** Each row in the preview can be struck out and put back. That beats editing the file when one line belongs to someone who has left.

### Fixes

- **Imports with profile questions run through.** Mapping a column onto a profile question, like a phone number or shoe size, stopped the whole import with an error. Those columns now arrive, with dates, numbers and yes or no read correctly.
- **Mapped answers reach the imported members.** Even when the import ran through, answers from those columns were dropped. Members now arrive with their profiles filled in.
- **A parent's phone number no longer stops the import.** Such a column ended the import part way, with some members added and the rest left out. Contact columns now arrive with everything else.
- **Parents without an email address are kept.** They were silently dropped and never linked to their child. They now get a made-up address, just like members who arrive without one.
- **Parents' surnames no longer appear twice.** A column with a contact's full name produced names like "Rita Sommer Sommer". The last word of a full name is now read as the surname.
- **Importing the same list twice makes no duplicates.** Rows are matched by address, or by name within the station when there is none. New people are added, existing ones are skipped and listed in the result.

## v26.13.0

### New Features

- **Ember walks you through your first steps.** When the introduction tour ends, a short list of real tasks begins: your profile, your notifications, the next event and the calendar subscription. Ember points at the button to press and the page stays usable, so you learn the way by doing it.
- **Guided setup for stations and the instance.** Managers see what their station still needs beyond the setup wizard, and administrators see what the instance needs, each on their own start page. Both lists are shared, so a step one manager settles is settled for everyone, with a note of who did it.
- **Your station gets a media library.** Everything your station has uploaded lives in one place at `/station/media`, with folders, tags and search, and every editor can reach into it. Anyone who may sign in can upload and insert their own files, so a picture fits into a board ticket as easily as onto a public page.
- **News entries can hand you a file.** Authors pick attachments from the library, name and order them, and they show up as downloads under the text. They travel with the blog feed and to partner stations, so readers there get the same file.
- **Write a news entry with the page editor.** You can switch an entry from the plain text field to rows and columns, with images beside text, callouts, galleries and code blocks. The switch is one way: your text moves into a single block and nothing is lost, but the plain field does not come back.
- **Tell every station something at once.** Under Stations → System news an administrator writes a notice that appears in every station's news list, from Ember and marked with a System badge. It can be limited to certain member types and notifies only when asked, and a correction or withdrawal applies everywhere at once.
- **The instance keeps its own library.** Pictures and files in a system notice belong to the instance, so a station tidying up its own files can never break them. You upload and pick them while writing the notice, and every station that reads it gets them.
- **Wiki articles get the page editor too.** A markdown article can switch to the page editor just like a news entry, which suits a training document with a diagram beside its explanation. Search, the PDF export and the version history keep working, though old versions can be read but not restored.
- **Your station decides how gear moves.** Under Inventory → Chains there is a chain for every way gear travels, from issuing and handing back to exchanging and requesting, and you shape each one step by step. The gear's owner and the person at the other end decide which chain applies, so one inventory can follow different chains for different rows.
- **Every handover ends with a confirmed receipt.** Each chain starts with a request and ends with the person holding the gear confirming it under Inventory → My equipment. If somebody never answers, a manager can still complete the step with a note.
- **Ask for everything a member holds at once.** One button on their equipment page starts a return for every piece, each on the chain that fits it. The station's own gear goes back to its store and the association's goes into the post.
- **Stations can belong to an association.** An administrator creates one under Admin → Associations. Whoever runs it gets their own area at `/cluster`, with rights kept separate from any station.
- **Stations apply, and associations answer.** Only a station's owner can apply, under Manage → Association, and the association approves or refuses with a reason both sides can read. An association can also create stations of its own, which belong to it from day one.
- **Stations in an association connect on their own.** Joining links a station to its association and, unless the association turns this off, to every other station under it, with calendars, knowledge and gear shared both ways. These connections belong to the association, so no station can end them or pause the one carrying the association's content.
- **An association shapes what its stations use.** It can lock modules without deleting anything in them, hand down or lock colours, theme, shape and logo, and share out storage from a pool the instance grants. It can also keep files on storage of its own, and moves them one station at a time, never behind anyone's back.
- **An association has members of its own.** Who acts for it is set there, by role, by their own grants or through a group, with the same permission picker a station uses, and nothing held at a station grants it. A trusted member can search and edit the people at every station below, except their own membership and any station's owner.
- **Association questions in every member's profile.** The fields an association adds appear in the station's own profile form, marked as the association's and read-only at the station unless it says otherwise. Changes land in the profile's change history, and when a station leaves the answers are cleared while the history stays.
- **An association keeps track of its own gear.** It sees every piece it owns and where it is, and steps only it can confirm wait in a list of its own. An association that does not keep its gear here says so, and its stations carry on as before.
- **An association can talk to its stations.** News, appointments and wiki articles it writes reach every member station with the association as sender, written on the same screens stations already know. Nothing is copied: stations read what was written once, over the connection they already have.
- **Sign in with a username.** Under Account → Profile you can pick a name to sign in with, and your email address keeps working too. A guardian can give one to a child under Profile → Managed profiles, so a child without an address can sign in while all mail, the password invitation included, goes to the guardians.
- **The directory groups stations by association.** Stations under the same association appear under its name instead of scattered through the list, with everything else below. An association has no page of its own, only its stations do.
- **Install Ember like an app.** Where your browser supports it, Ember goes onto your home screen or dock and opens in its own window, offered with one button among your first steps. Other browsers keep the written instructions for a bookmark.

### Improvements

- **Date and time fields open their picker.** Reaching such a field opens the browser's calendar or clock right away. You no longer have to hunt for the small icon at the field's edge.
- **Guardians set a child's password directly.** Under Profile → Managed profiles the password now sits beside the username, so nobody waits for an invitation that lands in the guardian's own inbox anyway. Members with an address of their own keep setting it themselves.
- **An association's wiki can go public.** A switch above the wiki puts it on the public web, with the same three states a station has and the public address right beside it. Until now an association had no way to publish anything at all.
- **Choose which stations a wiki entry is for.** An association's entries still go to all its stations unless it names some, and a choice on a folder applies to everything inside. An entry within can narrow that further but never reach past it.
- **Association wiki folders arrive as folders.** What an association sorted into folders used to reach its stations as a loose list of articles. The structure now survives the trip and opens where you expect it.
- **Wiki visibility gets its own place.** Visibility is now its own point in the menu of every folder and article, right beside Edit, instead of sitting at the foot of the edit dialog. Editing keeps the name, the description and the tags.
- **A wiki tile shows how far it reaches.** A green eye marks an entry on the public web, a blue one an entry every connected station reads, and a yellow one an entry shared more narrowly. No eye means it stays here, and hovering over an eye explains it.
- **Share a wiki entry with some partners only.** Sharing used to be all or nothing, and holding back meant not sharing at all. Not passing an entry on is still the default and the normal case.
- **Find your way out of a shared folder.** Opening a folder someone shared showed its contents with no trail back, so only the browser's back button helped. The path now names every shared folder up to the wiki itself.
- **The visibility picker names the standard.** Where it used to offer just "standard", it now says what that means for this wiki. That is either public unless an entry says otherwise, or not public unless an entry says so.
- **Roles on shared wiki entries mean yours.** An entry marked for the leadership now reaches the leadership of every station it is shared with. Before, the role meant nothing once the entry crossed to another station.
- **The public wiki setting stays in sight.** When the wiki is not public, its setting no longer vanishes. It says the public wiki is off and where to switch it on.
- **Ask each kind of member their date of birth.** Asking the team and asking the guardians are separate questions nobody answers twice, so both are now allowed. A date of birth asked of a group still blocks every other, since a member can belong to many groups.
- **Reordering profile questions saves in one go.** Dragging a question in a list of twenty used to send twenty saves, slow enough to notice. Now the whole order is written at once.
- **A reminder before event registration closes.** Three days and one day beforehand, everyone who has neither accepted nor declined hears about it, along with whoever answers for them. The notice names whose answer is missing, so a guardian can tell their children apart.
- **The dashboard shows what still needs an answer.** Events closing soon that nobody in your household has answered get their own section, soonest first. You can decline them right there.
- **Answer an event for the whole household.** If you answer for several people, say yourself and your children, you tick who the answer is for instead of repeating the same screen. Where the event asks questions, each person gets their own tile, because the answers are theirs.
- **Change your answer until registration closes.** If you accepted and then can't come, you can now say so. After the deadline only the organiser can change it, and coming back after declining means signing up again.
- **Questions under a system notice reach the instance.** Anyone may comment on a notice from the instance, and each station sees the comments written by its own people. The administrator sees all of them with each station named, so questions get answered.
- **Every text editor can insert a picture.** Editors that only took a pasted address now open the media library to browse, search, upload and insert. News, board tickets, event descriptions, the wiki and the page editor all gain it at once.
- **Cleaning up keeps what members uploaded.** A file nothing points at is still offered for removal, but one somebody uploaded themselves is kept. A picture can outlive the first place it was used.
- **Gear held for the body above finds its owner.** When a station joins an association, gear already recorded as the municipality's or the association's keeps its place, size and holder, and the association sees it in its list. Nothing is moved or recreated, and a running exchange carries on.
- **The demo instance now has an association.** The demo station answers to one, beside a station the association made itself, with people in each association role, gear in every state, two profile questions and its own news, article and appointment. A neighbouring station's request to join is waiting too, so the screen for answering it has something to show.
- **An exchange opens as the chain it follows.** The history button now shows the whole run, every step with its party and the finished ones stamped with who acknowledged them and how. The current step shows either the one button you may press or who is being waited on.
- **Members report their own gear missing.** Under Profile → My inventory you can say you cannot find something assigned to you, and a guardian can say it for the person they look after. A station can ask for a short note with the report under Inventory → Configuration, shown beside the item afterwards.
- **Ask the association to replace lost gear.** Marking a piece missing stays the station's own business, and asking for a replacement is a separate step on the item's page, with the manager's note beside the member's. The association sends a replacement or refuses with a reason, and the piece stays recorded as missing either way.
- **Associations take people on at their stations.** The member list across the stations now offers it, starting with which station the person joins. Someone who is not meant to sign in is recorded the same way a station records them.
- **Associations order gear into their own store.** The Inventory → Procurement tab records orders without asking who they are for, because an association buys for its store and hands out later. Marking an order arrived puts the piece in the store, ready to go to a station.
- **Association requirements count at the station.** A requirement the association writes now stands on the station's requirements page, named after the association and locked there, and the association's gear counts towards it. It can apply to some stations only, so what a water rescue station must hold does not become everyone's rule.
- **Association figures grouped by kind of gear.** The Inventory → Statistics tab lists jackets, helmets and boots as blocks of their own, each with its sizes and what is still in store. Only what the association owns counts, and what a station bought itself stays the station's business.
- **Associations read and add member documents.** The page an association opens on one of its stations' members now shows the documents filed about them and lets it add one. Labelling, linking further members and removing stay with the station, which keeps the document if it leaves.
- **A station asks the association for gear.** Under Inventory → Overview it names what it needs and in which size, and the association sends a piece or refuses with a reason. An association opening Inventory → Configuration for the first time finds the usual chains ready, one per purpose.
- **Associations send gear out in one consignment.** Under Inventory → Stock the association picks a station, then the pieces from its own store, and everything counts as on its way. The station confirms one arrival instead of one per piece.
- **Associations decide what a loss report needs.** Under Inventory → Settings it asks for nothing, a note, or a note and a document, and a report short of that is refused before it is raised. Everything arrives in one place: both notes with their authors and the file beside them.
- **Movements notify whoever's turn it is.** When a step is acknowledged, only the party of the next step hears about it, so the message itself says something is waiting. A refused movement tells both ends, with the reason where the member asked.
- **Members hear about access their guardian grants.** Switching sign-in on or off now tells the member by mail, naming the station, and an account not yet set up gets the password invitation instead. The mail waits a few minutes, set by `auth.managedLoginNoticeMinutes`, so a switch flicked straight back reaches nobody.
- **Ember runs on 64-bit ARM machines.** Ember is now built for ARM as well as x86, so a Raspberry Pi 4 or newer can serve an instance with no extra setup. It needs a 64-bit system, which on a Raspberry Pi means the 64-bit Raspberry Pi OS.
- **Exported catalogs say where questions came from.** The file carries their language, source, author and terms of use. The station importing it keeps all four beside the catalog.
- **Catalog files bring only the categories they use.** Importing one no longer drags across the exporting station's whole category list. A category you already have under the same name is reused instead of duplicated.
- **Build a whole catalog from a spreadsheet.** The import on the catalog list now takes a table as well as a catalog file, asks for a name and creates the catalog. Adding to an existing catalog accepts both kinds of file too.
- **Every import shows a preview first.** Questions from a catalog file now go through the same preview a table did, where each can be corrected or left out. Nothing is written until you confirm.
- **Tables carry more about each question.** Beside question, answer, category, type and points, the import now reads a hint, an image address and wrong answers. It also reads the points per answer, and how many answers an enumeration asks for and in which order.
- **Wrong answers turn a blank into a choice.** Naming wrong answers in their own column turns a fill-in-the-blank into a list to pick from instead of an empty box. In a multiple-choice question they are kept apart from the right ones.
- **Invented wrong answers appear in the preview.** When the import thinks up wrong answers for a multiple-choice question, you now see them in the preview. You can correct or drop them before they land in the catalog.
- **The import explains the file format.** A panel lists every table column and catalog field, what each one means and whether it is required, plus what each question type expects. The same reference is in the help centre.
- **Example files to start from.** The import offers a table and a catalog file with one question of every type, both ready to import as they are. You build your own by editing an example instead of reading a description.
- **Report a question while training.** You say in your own words what is wrong, such as an outdated answer, a question that reads two ways or two answers that both fit. The note lands on the question itself.
- **Reported questions show their notes in the catalog.** Each note sits at its question, with who wrote it and when. Marking it done removes it, so what is left is only what is still open.
- **Event templates can be copied.** Duplicating one takes its settings, questions, reminders and audience, and opens the copy right away. Your second variation of an evening starts from the first instead of an empty screen.
- **Reorder an event template's questions.** Arrows on each question move it up or down. No more deleting everything below a misplaced question and typing it in again.
- **Gear lists show what is really there.** A piece handed in for an exchange leaves the member's equipment right away, and one on its way to the association leaves the station's stock and figures. Both show up under running exchanges instead, by name, with the step they wait on.
- **Members can withdraw their own exchange.** Asked for a bigger jacket and it fits after all? You can withdraw the request under Profile → My equipment while the piece is still with you, and once it is handed in, the station calls it off.
- **Calling off an exchange keeps the record honest.** A piece already in the post stays where it got to instead of reappearing on the member. Everyone involved hears what happened and where it stayed.
- **Gear in an exchange still counts as present.** The equipment check asks whether somebody is equipped, so a jacket in the post no longer shows as a gap for weeks. The line says how many pieces are away in an exchange.
- **Every sortable list sorts the same way.** Each row has up and down arrows, plus a grip to drag with on a device with a mouse. Lists that could only be dragged now sort on a phone, and lists with only arrows can be dragged.
- **Questions can take half or a third of a row.** Event templates and attendance sheets now offer the widths profile questions already had, with a drawing of the form's layout. A sheet of short questions no longer runs to one line each.

### Changes

- **Files move out from under Pages.** What was Pages → Files is now Media in the sidebar, at `/station/media`. It is the same library with the same contents, now belonging to the station rather than its website.
- **Station media moves to a new folder on disk.** On the first start after the upgrade, media moves one station at a time, and each station is read-only while its own move runs. An interrupted move picks up where it stopped on the next start, with nothing to redo by hand.
- **Stations no longer edit gear they don't own.** A station cannot rename, delete or lend out gear it does not own, but handing out, shelving, checking and reporting it missing still work. What the gear is and who may borrow it stays with the owner.
- **Stations in an association stay on their instance.** Moving such a station to another instance would leave the association holding a station that is gone. Leave the association first, and the transfer works as before.
- **Every item says who owns it.** Each piece belongs either to the station or to the body above it, the municipality or the district association, replacing the old internal and external labels. Members are no longer offered as owners, since gear a member bought was never tracked here.
- **Every item also says who has it.** A piece now records where it really is: in its owner's store, at the station, with a member, lent to a partner or missing. Its page shows owner and holder, and a station's lists follow what it has rather than what it owns.
- **Exchanges become a chain of steps.** Gear moving between a station and the body above now walks a named chain instead of five fixed statuses, and every step is one somebody at the station actually saw. You can rename or reshape the chain under Inventory, and finished exchanges keep the wording they were walked under.

### Security

- **News entries could be read from other stations.** The lists never offered them, but anyone signed in anywhere could open an entry and its comments by its address. An entry is now readable only in its own station, by the people it is addressed to.
- **Mentions could reach into another station.** Mentioning a group, event or member used a number shared across the instance, so a comment in one station could notify people in another and show them its text. A mention now stays within its station, and the old form without a station notifies nobody.
- **Waiting-list sign-ups could mail strangers.** Both public sign-up routes accepted unlimited registrations and mailed every address given, which could flood someone and use up the daily mail allowance, password resets included. Sign-ups are now limited per sender and per link, with `429 Too Many Requests` beyond that.
- **Another station could collect your generated questions.** Anyone on the instance could fetch a running question generation by its address, and fetching it cleared the result, so the station that started it never saw its questions. A job now answers only to the station that started it.
- **Probing for records no longer confirms they exist.** Asking for another station's record used to answer "not allowed" differently from "not there", which gave away that the record existed. Both now answer the same way: not found.
- **Pages declare what they may load.** Every page now carries a content security policy, so only Ember's own scripts with the page's one-time marker may run. It only observes until an operator sets `NUXT_CSP_MODE` to `enforce`, so an upgrade cannot break an embedded map or video.
- **Lending conversations were readable by other partners.** Any partner station could read the messages about a borrowing request by asking for them by address. A partner now only sees the requests it is part of.
- **Partners could rewrite any board comment.** Editing or deleting a comment on a shared board checked the board but not the comment, so a partner could change or remove any board comment on the instance. A comment now has to belong to the ticket it is addressed on.
- **Partners could read what was never shared.** Asking for a wiki article, quiz catalogue or test protocol by address returned anything the paired station held, catalogues with their correct answers included. A partner now gets only what the station actually shares.
- **Board attachments were not tied to their ticket.** Downloading or deleting an attachment checked the board in the address but not the attachment, so a deletion could remove another station's attachment record. An attachment now has to belong to the ticket named.
- **Requests could pull in another station's records.** Adding someone to an attendance list, linking a ticket or wiki article to a ticket, and swapping a test question each accepted a record from anywhere. All four now accept only records of your own station.
- **Reviewing profile changes reached other stations.** A reviewer of member data could read and acknowledge changes of members in another station, which cleared them from that station's own review. Reviewing now covers only the reviewer's station.
- **Event template reminders were open to other stations.** The reminder days of any template on the instance could be read and quietly changed from elsewhere. Reminders now belong to the template's own station.
- **Found items, lending blocks and bookmarks were exposed.** Lost-and-found entries could be read and deleted from any station, lending blocks lifted by another station and bookmarked partner boards removed from someone else's list. Each now belongs to the station or member that made it.
- **Member lists were readable from other stations.** Anyone allowed to manage groups or tags could fetch the members, group rights and a member's groups and tags from any station. These now answer only within your own station.
- **Event discussions were open to every station.** Anyone signed in anywhere could read and write the comments under any event, author names included, and event managers could delete another station's comments. Comments now stay within the event's station.
- **Test protocols could be changed from other stations.** Anyone with a test protocol right could open, rewrite or delete another station's protocols, runs and results by addressing them directly. Protocols now answer only for their own station, and a run holds only that station's members.
- **A failed storage test said too much.** Testing a storage connection showed whatever the machine reported, which could be used to look around the server's network. The test now reports a plain failure, and the detail goes to the server log for operators.
- **Server answers carry browser protections.** Every response tells the browser not to guess content types, refuses to be framed by other sites and keeps the full page address out of referrers, and over HTTPS it asks to stay on HTTPS. Guessing content types was how a download could be treated as a web page and run.
- **Saved links could make the server fetch anything.** Saving a wiki link without a name made the server open that address, internal ones too, and store what it found where the member could read it. The lookup now goes to public addresses only, at every redirect step.
- **Formatted text could carry a script.** Formatted text such as descriptions, wiki articles, page blocks or profile fields was shown as written, so a hidden script ran for every reader, public visitors included. Formatted text is now cleaned and keeps nothing but its formatting.

### Fixes

- **Importing a question catalog file works now.** Any file that actually held questions was turned away with a general error, so catalogs could not move between stations at all. Importing works, and a faulty file now names every problem and creates nothing until they are sorted out.
- **The fairness ranking works for uncategorised events.** For an event in no category, the list of how often each member was registered, accepted and turned away could not load at all. It now covers everything the station has done, the only fair comparison when there is no category.
- **Partnerships now follow a station that moves.** When a station moved to another instance, the step that points its partnerships at the new home failed outright. Partnerships now follow the station and keep working without anyone touching them.
- **The demo instance shows its logo and files again.** The rule that blocks uploads on a demo also refused to hand back files already stored there, so the logo and file library looked empty. Reading works again, and uploading stays off.
- **Group questions on event templates can name the group.** A template question asking for a member of a group had no way to pick the group, so it was saved without one and answered with nobody. The groups are now offered there as everywhere else.
- **Attendance question kinds show their real names.** The list of questions on a sheet printed the raw wording behind each kind instead of its name. Each one now reads as it does in the form that sets it.
- **No more login button once you are signed in.** A page rendered by the server cannot tell whether your browser has a session, so it offered the login until the session came back. It now waits until it knows, then shows the account menu as before.
- **Signing out now clears the previous person's stations.** The list of stations and associations an account may act for outlived its session. The next person signing in on the same browser saw them until the page reloaded, and signing out now clears them.
- **Shared articles are listed once.** An article reachable on its own and through a shared folder appeared once for each way in. It now shows once, however many shares reach it.
- **Gear lent to a partner is held back.** Equipment lent to a partner station could be handed to a member or lent again while away, because only the assignment was checked. It now stays reserved until the partner gives it back.
- **Exchanged gear goes back to its real owner.** In an inventory holding gear of both owners, a finished exchange put the returned item into the station's free stock even when the station never owned it. The exchange now follows the owner recorded on the item.
- **The installer checks the machine first.** On a machine Ember is not built for, the installer set everything up and started containers that kept stopping with only a format error to go on. It now checks the machine before writing anything and says plainly what is wrong.
- **Second-factor confirmations now survive a session renewal.** Your session renews itself in the background, and the renewal forgot both a fresh confirmation and any device you asked it to remember. It now carries them over.
- **A refused security confirmation asks again.** When the confirmation was accepted but the action was still turned down, no new prompt appeared and only a general error showed. Now you are asked once more, and a message says plainly when the instance keeps refusing.
- **Staying signed in now lasts longer.** Ticking the box on your own machine gave half an hour, while leaving it unticked gave a full hour. A device you vouch for now keeps its session for thirty days, and an instance that set `auth.sessionMinutes` keeps its own value.
- **A missing station no longer signs you out.** In some cases the station your browser had selected was gone, and every request looked like your sign-in had ended, sending you back to the login screen. Whether a station can be found no longer decides whether you are signed in.
- **The sidebar marks where you are.** On several pages the section you were in stayed unmarked, and elsewhere one section was marked on every page. Now exactly the section you are in is marked.
- **The forced rainbow flag shows everywhere.** Switching it on only affected the pages after signing in, while the landing page, login page and public station pages kept the plain wordmark. It now appears everywhere.
- **The forced pride flag matches June's.** Switching it on under Settings → General coloured the logo's letters instead of showing the flag behind them, unlike the flag that appears by itself in June and July. It now looks the same however it was switched on.
- **The sitemap points search engines to the right address.** Its addresses were built from an internal host name that only exists inside the deployment, and that answer was kept for six hours. It now uses the instance's configured public address, so search engines can follow it.
- **The help centre search finds things again.** An ordinary word returned no results on any page but one, so the only way through was clicking down the tree. Search covers the whole help centre again, the association's own pages included.

## v26.12.0

### New Features

- **Every member gets a document store.** Each profile has a tab for the files about that member, and Members → Documents holds the whole store. A document can belong to several members or to none, and images, text files and PDFs open right in Ember with a preview on their tile.
- **Search documents by what they say.** Search reads the title and the text inside the file, so you find a PDF by a word in it. Free-text labels help you sort the store, and you write them as you need them.
- **Your data export includes your documents.** When somebody asks for their data, they now get the documents held about them as well. That includes the files themselves, withheld ones too.
- **Keep a document beyond a membership.** A document marked to be kept stays when its members become former members, as a legally binding document must. You can also hide a document from its own members, so only those who may read other members see it.
- **Arrange profile fields side by side.** Each field takes a full row, a half or a third, so short fields sit next to each other. A new heading field structures the form, and a preview under Members → Configuration shows how it will look.

### Fixes

- **Roles now hand out all their rights.** In some cases a member held only part of what their role allows, so some of their pages refused to open until a restart. A role now grants all of its rights every time.
- **Field templates land in the chosen group.** Fields added from a template on the group tab belonged to no group and had to be assigned by hand. A template now goes straight into the group you are configuring.

## v26.11.12

### New Features

- **Install Ember with a single command.** `curl -fsSL https://ember-panel.de/install.sh | bash` asks a handful of questions, writes the compose file, starts everything and shows your new login. You choose a plain port or an existing Traefik, a bundled or existing PostgreSQL, and where your configuration, files and database live.
- **Plan your installation in the browser.** Under `/install` you answer the same questions and get a six-character code back, short enough to read out to whoever runs the server. The code lasts two hours and never contains the database password.
- **Waiting lists can work with ages.** A date field of the type date of birth lets the list refuse registrations below a minimum age and mark everyone still too young to join. You can hide those entries while you work through the list, and an existing date field keeps its answers when it becomes the date of birth.

### Improvements

- **Send stalled mail again with one click.** The overview now lists each message left behind by a stopped delivery instead of only counting them. You can queue them again one by one or all at once, and mail being sent right now stays untouched.
- **Sort the waiting list by any column.** The date of birth has its own column, and every column heading sorts. The list still opens on the highest score.

### Fixes

- **Group profile fields stay in their group.** A profile field made for a group could lose its place. It now appears and is filled in under its group, and fields without a group are offered for assignment at the top of the group tab under Members → Configuration.
- **Waiting list answers read as they were given.** Answers in the waiting list could show in a raw stored form. A date now appears as a date and a yes as a yes, in every column.

## v26.11.11

### Improvements

- **Filter the log by source and thread.** Both come as searchable lists with a count each, and threads from the same pool count as one. Each line now shows its severity in colour and tells you up front when it holds a stack trace.

### Fixes

- **Address change links open a real page.** Confirming a new email address led nowhere and could report the change as done too early. The page now says what happened and which of the two confirmations is still missing.
- **Selection fields show their choices again.** The options of a waiting list selection field went missing. They now appear in the list, in the editor and in the dropdown on the public form and on each entry.
- **The scoring formula suggests fields again.** Opening a bracket used to wait for you to guess a first letter. It now lists every waiting list field, the waiting-time values and the age function right away.
- **Legal pages open from a typed address.** `/privacy`, `/terms` and `/imprint` could fail when opened directly instead of through a link. They now always load and say so plainly if the text cannot be fetched, and the shipped deployment files set `NUXT_BACKEND_URL`, which your own files must set too.

## v26.11.10

### New Features

- **Read the instance log right in Ember.** Under Monitoring you search the running instance's log by message or logger and filter by severity, no server access needed. It is stored in the database only if an operator switches that on under Settings, while the console and log file always keep everything.
- **See where your mail stands.** A new page under Monitoring shows how much mail waits, what each provider accepted and what it reported back. Every provider also shows today's sends against its allowance.
- **Refused providers are skipped per domain.** When a receiving domain turns away a sending server, Ember remembers it and stops spending allowance on certain refusals. The block lifts itself after a week, and the mail overview lets you lift it by hand sooner.
- **Stay signed in on your own device.** The login screen offers to keep you signed in, and only then does your session run long. Without it a session ends after an hour, right for a shared machine, and the second factor stays a separate choice.

### Improvements

- **Stuck messages say they are stuck.** The mail overview marks a waiting message that no provider can carry right now. You can tell a queue that will never move from one that is just busy.
- **The log file stops growing forever.** It rolls at 100 MB, keeps two weeks or 2 GB of history and compresses old parts. Before, every start wrote one file that nothing ever removed.
- **Sessions can last up to thirty days.** Under Settings → Security an operator sets how long sessions run on trusted devices and on all others. Signing out or changing a password still ends every session at once.
- **More room to read the consent text.** The consent window on the login screen is wider now. It shows more of the document at once instead of a column as narrow as a password field.
- **Platform statistics count more.** Outgoing mail, active delivery blocks, two-factor accounts, upcoming appointments and their registrations now each have a figure. Two new charts show registrations over the last thirty days and how attendance was answered.

### Fixes

- **Chart headings and legends stay clear.** A chart's heading, legend and axis label could crowd each other. Each now keeps its own place.
- **Statistics panels are evenly spaced.** The first block under a heading sat at a different distance than the rest. Every block on the page is now spaced the same way.

## v26.11.9

### New Features

- **Mail providers form one ordered list.** An instance and a station each keep an ordered list of providers, worked from the top. Every entry is edited, moved, tested and given its delivery address the same way.

### Improvements

- **Each provider has its own daily allowance.** When a provider has sent its share for the day, the next one takes over instead of holding mail until tomorrow. This replaces a station's overall daily and monthly caps.
- **Send a test mail to any address.** You can test any provider in the list, not only the active one, and send to any address you name. A misconfigured backup no longer surprises you when everything above it has run out.
- **Each provider gets its own report address.** The address for delivery reports matches the format that provider sends. Two different providers in a list get two different addresses, for stations and for the instance.
- **Privacy sections for each email provider.** The privacy policy ships a ready section for Brevo, Sweego, Twilio SendGrid and rapidmail, plus a blank one for any other server. All start switched off, so you enable only the one you actually use.
- **A privacy section for Cloudflare.** An instance behind Cloudflare sends every visitor request through their servers. The privacy policy now ships a section saying what is processed there, on what legal basis and in which countries.
- **Pick which shipped sections to load.** Under Settings → Legal each row shows its heading, tells you whether it replaces a section in the editor and lets you read it first. Selecting everything loads Ember's layout and leaves alternatives, like the email provider sections, for you to pick.

### Fixes

- **Icons arrive with the page.** Icons on public pages, the login page and the help center could appear late or not at all without scripts. They now come with the page the server sends.
- **Queued mail survives the daily limit.** Reaching the limit could leave the remaining messages behind. They now wait for the next attempt.
- **Malformed requests explain what is wrong.** A request the server could not read got only a bare failure. The answer now names the reason and the field at fault.

## v26.11.8

### New Features

- **Read and write permissions for the wiki.** A folder or file now says what its audience may do: read, read and edit, or full access including deleting and publishing. You can let a group read a folder without letting it change anything.
- **Ask questions when people register.** An event can ask everyone signing up for details like shirt size, guests or what they bring. Answers can be text, number, yes/no, date, choice or member, each optionally required and with a default.
- **Answers sit right beside the registration.** Chosen answers appear next to each name in the registration list, with totals above number questions. Event templates pass their questions on to every event made from them.

- **Placeholders for repeated legal details.** A name in double curly braces stands for a value like the operator's name, address or email address. You fill it in once under Settings → Legal, and Ember applies it across every document and language.
- **The privacy notice lists your browser storage.** Privacy policy and consent text name every value Ember keeps in your browser, what it is for and how long it stays. The section is written from the application itself, so it stays correct as Ember changes.

### Improvements

- **Attendance buttons say what they do.** The present, absent and excused buttons now carry a name. Screen readers announce it, and hovering shows it.

- **Write steps straight into a procedure.** Adding a step now puts an empty row in the list. No dialog asks for its title first.
- **Mark a profile field as date of birth.** The new field type works like a date field and can feed a calculated age. A station has one such field, so the type is offered again only once that field is gone or changed.
- **Terms of service that match the product.** The shipped terms cover public pages, partner sharing, AI-assisted questions, feeds, exports, station obligations and how a station's use ends. They come in six parts you can reorder or switch off.
- **An imprint you fill in, not rewrite.** The shipped imprint has placeholders for the operator's name, address, phone, email and the person responsible for content, and an empty one stays visible instead of leaving a blank line. New operator setting: `api.placeholderFile`.
- **Take over a lawyer's document as it is.** Under Settings → Legal you import a file or paste text, and Ember splits it into sections and turns references like "§ 12" into links. Numbers that match no section stay untouched and are listed before you save.
- **The terms number themselves.** Paragraph numbers are set when the document is shown, so moving or switching a section renumbers everything, references included. A reference to a removed section is marked instead of pointing nowhere.
- **A privacy section for your email provider.** Privacy policy and terms ship a section about the service that sends your mail. It stays off until you fill in the provider, its address, its server location and how long it keeps logs.
- **Start legal documents from the shipped templates.** Under Settings → Legal you load sections of Ember's own document into the editor, one at a time or all at once. A section with the same name is replaced, and nothing is written until you save.
- **Choose what your browser keeps, group by group.** Besides the required values, you now allow or refuse two groups separately: what features remember and what your view settings remember. You choose with the consent and can change it under Account → Data & account, which deletes a refused group's values at once.
- **Privacy policy and terms are never blank.** An instance without its own documents now serves the ones Ember ships instead of an error. The shipped set lands exactly where the instance reads its documents from.
- **The browser storage section stays up to date.** It is generated, so you can only show, hide or move it. Privacy policy and consent text both use it, and a change asks for consent again.
- **The wiki only offers what you may do.** Edit, delete and create appear only where your permission allows, and read-only entries are marked, naming the folder that decided it. Existing stations notice nothing until they set their first permission.
- **Organisers see every registration answer.** Whoever may edit the event sees all answers with notes, plus totals: numbers added up, choices counted per option. A question can also be kept for organisers only, so members neither see nor answer it.
- **A station's public calendar opens.** Visiting it shows the station's dates. The subscription link for your calendar app sits right beside them.
- **Station public pages arrive complete.** The station name, menu, blog, wiki and calendar now come with the page the server sends. Search engines and link previews see all of it.
- **Public pages arrive complete.** The station directory and the imprint, privacy and terms pages now carry their content from the server, so search engines and link previews see it. Visitors get the same pages a moment sooner.
- **Open files your partners share.** A shared wiki file opens like your own, from the file list and from search, instead of only offering a copy. Text and Markdown files show their content and take comments, while other formats still need copying first.
- **Open shared catalogues and test sheets.** A shared question catalogue shows its categories and question count, and a shared test sheet its sections and points. You can still copy both into your own station.
- **Save wiki files as PDF.** Markdown and text files download as a PDF with your station's name and logo, from the file, the file list, a partner's shared file or the public wiki. Headings, lists, tables, quotes and code stay, and images are replaced by their description.
- **Tile and list view offer the same actions.** You can remove a favourite in both wiki views. Every button on an entry now names what it does.
- **Guardians manage sign-in for their members.** Under Profile → Managed profiles a guardian sets a member's email address and switches signing in on or off. Allowing it sends a password invitation, while refusing it or changing the address ends open sessions.
- **Email falls back instead of getting stuck.** An instance can list further providers, each with a number of attempts before the next takes over. A message refused because of the relay itself, such as a blocked sending address, moves straight to the next provider.
- **Ember learns whether your email arrived.** Providers now report back whether a message was delivered, bounced or blocked, and Ember records it. Under Settings → Mailing you find an address to paste into the provider, with a key Ember generates for you.
- **One language for all system emails.** Under Settings an operator picks the language for emails to accounts outside any station, like self-registered users and the first administrator, who used to get English. Accounts created from a station keep that station's language.

### Security

- **Guardians see only their own children's changes.** The change list, the pending overview and acknowledging a change are limited to the members a guardian manages. Reviewing the whole station stays with the permission meant for it.

### Changes

- **One menu for wiki entry actions.** Editing, downloading and deleting an entry now sit in one menu instead of a row of icons. An entry with only one action keeps it as a plain button.
- **Quiz and exams are named by use.** The menu entry reads "Quiz & Exams" while both are on, and "Quiz" or "Exams" when only one is. With both in use, each gets its own section instead of one list of five.
- **Shared wiki needs matching versions.** Partner stations on the previous version pause wiki sharing until both sides update. Every other shared feature keeps working meanwhile.

### Fixes

- **Members find the forms meant for them.** The forms page showed members nothing at all. It now lists every form the station has opened to them.

- **Editing a procedure saves again.** Changes to a procedure's name, description, due date or visibility were lost. They now stick.
- **Choosing a station takes you there.** Picking a station could send you back to the station picker, even after a long break or from a station link. It now opens the page you asked for.
- **Open tasks show after a long break.** Coming back to a station could skip the forms and tests still waiting for you. They now appear as they should.
- **Admin pages stay shut without admin rights.** Without instance administration rights, an admin page showed a panel where nothing worked. You now land back in your station.
- **The menu narrowing control stays on desktop.** The sidebar control showed up on phones, where there is nothing to narrow. It now appears on desktop only.
- **Station applications can be decided again.** The list of applications waiting for a decision was broken. Each one now shows its state with buttons to accept or reject.
- **New instances start with legal documents.** A fresh instance could start without its legal texts. Privacy policy, terms, consent text and imprint now arrive on first start in German and English, and existing documents stay untouched.
- **Members see their station's events.** The events page showed events only to those who also record attendance. Now everyone sees them.
- **Event registration lists load again.** Switching to an event's registrations failed to show who signed up. The list now loads.
- **Shared question catalogues appear again.** The shared section of the catalogue list stayed empty. It now shows what partner stations share.
- **Partner station filters work everywhere.** Picking a partner station in the wiki, catalogue or test sheet filters did not narrow the entries. It now shows only that station's entries.
- **Switching off quiz or exams cleans up the menu.** A station that switched off one of the two still had its pages in the sidebar. They now disappear.
- **The member import reports what it did.** Finishing an import showed an empty page. It now shows how many members and helpers were created, grouped and filled in, plus anything worth pointing out.
- **Chosen files really upload.** Uploads like wiki files, folder icons, avatars and imports failed as if no file had been chosen. The file you pick is now sent and the upload completes.

## v26.11.7

### New Features

- **Sort and filter your item lists.** Item tables on the inventory pages can show custom fields as columns, sort by any column and filter by value. You can filter by source and by whether an item is assigned or in storage, and a column picker shows or hides extra columns.
- **Fill in custom fields when adding items.** The add-item dialog now takes the inventory's custom fields directly. Number fields check their allowed range while you type.
- **Help for every setup step.** Each step of the station setup assistant has its own help article. You reach it from the help center menu and the search box.
- **A help article for every page.** Event templates, single news articles, inventory items, the answer generator, partner station views and reported problems now have their own guide. Every help center article is searchable from the menu.
- **Separate help articles for procedures.** The procedure list, editor, detail page and templates each have their own article. They no longer share one general page.

### Changes

- **Partner compatibility is checked per feature.** When two connected stations run different versions, only the features whose data exchange changed pause, not the whole partnership. The partner page shows what is paused, and it resumes on its own once both run the same version.
- **No size label for unsized items.** Item lists and member inventory pages leave the size empty for items without sizes. Size changes in exchange and procurement views stay as they were.
- **Item actions in one menu.** The buttons on each inventory item row now sit in one menu. Every entry has a clear label.
- **Custom item fields are easier to set up.** Ember suggests the technical key and option values from the names you enter. You reorder fields by dragging, or with arrows on phones.
- **One item edit dialog everywhere.** Editing an item from the edit page opens the same dialog as the detail page, with custom fields, storage container and ownership. Custom field values stay when you save.
- **Confirmations use Ember's own dialog.** Deleting a file tag or folder, unassigning an item during a check and handing over someone else's item now ask in a proper dialog. The plain browser prompt is gone.
- **Attendance number fields can have a default.** A number field in an attendance session now takes a default value like every other field type. You enter it as a number, not as text.

### Security

- **Boards you cannot see stay hidden.** Comments, checklists, links, labels and history on a board you cannot access are no longer readable. Such a board now answers exactly like a missing one, so nobody can probe whether it exists.

### Fixes

- **Public blog article links work.** Opening a single article from a station's public blog failed. It now loads the article.
- **Reorder tickets on a partner's board.** Dragging a ticket within a lane on a board shared by a partner station did not save. The new order now sticks.
- **Creating custom item fields works.** Adding a custom field to an inventory failed for some field types. Every type now saves, selection fields with options included.
- **Custom item field values are kept.** Values in an item's custom fields vanished after saving or after editing its name, identifier or size. They now stay put.
- **Opening a station loads it fully.** Picking a station on the overview sometimes sent you back or showed an empty page. It now reliably opens the station, and links from emails and feeds land in the right one.
- **A clear message when your session fails to load.** A failed session load left an empty page with a bare menu. You now see an error message with a retry button.
- **Submit forms and polls on public pages.** A form or poll on a public page could not be sent. It now shows the consent checkbox and accepts the submission.
- **Contact form submissions open.** The submissions view of a contact form on a page did not list anything. It now shows the responses received.
- **Guardian names show on waiting list entries.** An applicant's guardians appeared as a dash. Their first and last names now show.
- **Attendance fields save right away.** Yes/no, date, selection and member fields in an attendance session saved only after a short delay. They now save the moment they change.
- **Edit responses after questions are added.** A submitted response hid questions added after it was sent. Opening it now shows every question.
- **Public waiting list and blog switches stick.** Turning either one on or off under Station → Federation was not saved. The setting now stays.
- **Replacing a presentation file works.** Uploading a new version of a wiki presentation did not replace the stored file. It now does.
- **Saved member filters apply reliably.** A saved filter failed when it was made for a tab that no longer exists. It now applies anyway.
- **Ordering questions keep all their items.** Moving an entry in a quiz training ordering question left a blank item behind. Every item now stays.
- **The feed notification switch tells the truth.** The feed channel under Account → Notifications could show the wrong state. It now shows whether it is really enabled.
- **Attendance help pages show the help menu.** The help pages for attendance and its settings opened without the help center navigation. They now show it.
- **Public station pages show their title.** Pages in a station's public area had no name in the header bar. They now show it.
- **The relocation notice highlights its menu entry.** The page announcing a station's move did not mark its menu entry. It now shows as active.
- **Delete comments on partner wiki articles.** Removing your own comment on an article a partner station shares failed. It now goes through.
- **Partner news notifications reach the right people.** Replies and mentions on a partner station's news article went astray. They now reach the members of the station that owns it.
- **Wiki search accepts any input.** Searching for nothing but punctuation failed with an error. It now simply finds nothing.
- **Link previews stay clean.** A link to an unreachable page picked up the title of an error page. It now keeps its address as the label.
- **Uploads with unusual file names work.** Files with no name or a capitalised extension failed to upload. Ember now recognises them by type.
- **Public forms and polls pages show their own titles.** Both pages showed the general forms title in the header. Each now shows its own name.
- **Reorder checklist items and partner board tickets.** Dragging a checklist entry on a board ticket, or a ticket within a lane on a partner's board, did not save. Both now keep their new position.

## v26.11.6

### Changes

- **Barcodes are recognised faster.** The scanner camera now records at a higher resolution and keeps focusing. QR codes and barcodes sharpen sooner and are read more quickly.

### Fixes

- **The scan button only opens the scanner.** Tapping scan inside an add or edit dialog also saved the dialog, creating entries with an empty code. It now just starts the camera.
- **Cancelling a scan turns the camera off.** Closing the scan dialog while the camera was starting left it running and disturbed the next scan. The camera is now released right away.

## v26.11.5

### New Features

- **Fill a storage container in one go.** The scan button on the storage container page becomes an add button. You scan barcodes or search by name or code, optionally only among unplaced items, and place several items at once.

### Changes

- **Filter the problem log by level.** The admin problem log now has error and warning filters. Acknowledging entries updates the list in place, no reload needed.
- **See where member permissions come from.** When you edit a member, permissions from their member type or groups appear ticked, locked and labelled with their source. Choosing station administrator marks every other permission as granted.

### Fixes

- **Every listed permission can be granted.** The item hand-out, storage location, form submission, poll result and checklist permissions could not be switched on. The permission picker now enables them all.
- **Opening a station stays in the station.** Picking a station on the overview sometimes bounced straight back. It now reliably lands on the station dashboard.
- **Startup cleanups run every time.** Cleaning up orphaned accounts and stale transfers was sometimes skipped with a warning. Both now run on every server start.

## v26.11.4

### Security

- **Visitors see only publicly listed stations.** Without signing in, the station directory at /discovery now shows only stations that chose public visibility. Instance-visible stations and stations with public content appear only to signed-in users.

### New Features

- **Send a test email from mail settings.** Station and instance mail settings now have a "Send test mail" button. It sends a real message to your own address, so you can check delivery end to end.

### Changes

- **The start page leads to the demo.** When station registration is closed and a demo address is set, the start page's main button opens the demo. Without a demo address it still points to the self-hosting guide.
- **Invited members get their account right away.** Invited people appear in the member list at once, ready for groups, events and attendance before they first sign in. The invite email asks them to set a password.
- **Invites to existing accounts join the station.** Inviting an address that already has an account used to fail. That account now simply joins the station.
- **Open invites become accounts on upgrade.** Invitations not yet accepted turn into member accounts, and the old acceptance page is gone. You can send these members a fresh password link with the resend button in the member list.
- **See when a setup link expires.** For members who have not set a password yet, the pending marker shows how long their emailed link stays valid.

### Fixes

- **Applications confirm only once sent.** The application page at /apply could show "application received" too early. It now shows the form first and confirms only after you send it.
- **Pages open in your theme.** Public pages briefly flashed the stock colours before switching to the instance theme. They now start in the right theme, also on installations that only set `NUXT_BACKEND_URL`.
- **The location map shows its pin again.** Picking a station address on the map during setup or in the settings showed no pin. The draggable pin is back.
- **Every permission has a readable name.** Some permissions showed raw technical keys in the picker. The checklist, item hand-out, storage location, form submission and poll result permissions now have proper names, descriptions and icons.
- **Page titles follow navigation.** The browser tab title and page heading updated only after a full reload. They now change as you move between pages.

## v26.11.3

### New Features

- **Step-by-step guides for mail providers.** The help center now has a page for each supported provider: Brevo, RapidMail, Sweego and Twilio SendGrid. Each walks you through creating the SMTP credentials and shows which fields to fill in.

### Changes

- **The bundled database moves to PostgreSQL 18.** The compose files now mount the database volume at `/var/lib/postgresql`, as the new version requires. The old data format is not compatible, so existing installations must migrate their data, for example with a dump before and a restore after.
- **Emails wait for mail setup.** Without a mail provider, sign-up, invite and password emails used to end up only in the server log. They now stay queued and go out once mail is configured.
- **Mail settings adapt to your provider.** The mail forms show each provider's own fields with matching labels and guidance, for example an SMTP key for Brevo and just an API key for Twilio SendGrid. A failed connection test tells you which credentials the provider expects.

### Fixes

- **Admin help articles are back in the sidebar.** Articles like the mail settings help were missing from the help sidebar. The admin help area now mirrors the admin navigation, so they show up again.
- **Admin help stays in the admin help area.** Help on security settings, two-factor and storage monitoring jumped to the station help center. It now opens with the admin help navigation.
- **Demo forms keep their question settings.** Choice, date and Likert questions on demo instances lost their options and scales. They carry them again.

## v26.11.2

### Security

- **Visitor addresses cannot be forged behind a proxy.** Behind a reverse proxy or Cloudflare (`network.trustedProxies`, `network.cloudflare`), Ember now takes the visitor address from the nearest hop that is not a trusted proxy. Forwarded-address headers a visitor sends themselves are ignored for rate limiting and security logs.

### Fixes

- **Problem monitoring shows times and stack traces.** Entries on the admin problems page showed an invalid date and empty details. They now show their first and last occurrence and the full stack trace.
- **Set a manager for stations without one.** Entering a manager email for a station that had none did not work. It now invites the account if needed and grants it station administrator access.
- **Everyone can edit their own name and email.** Changing your own name and email on your profile page needed the member edit permission. It now works for everyone, and an email change still waits for the confirmation link.
- **The remote storage key creates itself.** A production install with a blank `storage.credentialEncryptionKey` needed manual setup. Ember now writes a fresh key on first start, so storage credentials can be encrypted right away.

## v26.11.1

### Security

- **Stricter isolation between stations.** Every station resource, from pages and forms to members, inventory, wiki and boards, is now checked against your own station before it is read or changed. This closes cases where another station's data could be reached by its id.
- **Two-factor sign-in is rate limited.** Repeated two-factor attempts are throttled per account and per address, and a pending challenge ends after several wrong codes. A stolen password no longer comes with unlimited guesses.
- **Authenticator codes work only once.** A code from your authenticator app can no longer be used twice. That holds even within its short validity window.
- **Password resets forget remembered devices.** Resetting a password or removing a second factor now revokes every "remember this device" entry. A saved device can no longer skip the two-factor prompt afterwards.
- **Setting up two-factor asks for your password.** Adding your first authenticator app or security key now needs your account password. A stolen browser session alone cannot add its own second factor.
- **Shorter password reset links.** Self-service reset links now expire after one hour, configurable via `auth.resetTokenHours`. Invites and resets issued by an administrator keep their longer window.

### Changes

- **Station logos load lighter.** A station logo now accepts PNG, JPEG, WebP or GIF, and every place gets a copy at the right size instead of the full file. SVG uploads are no longer accepted.

### Fixes

- **Event access rules keep their match mode.** Whether an event's user type, group and tag conditions must all match or just one was lost on save. Your choice now sticks.
- **New members start with blank profile fields.** Custom profile fields without a default were not empty on the new member form. They now start blank.

## v26.11.0

### New Features

- **An admin overview of what needs attention.** Administrators now land on a panel at Admin → Dashboard → Overview with tiles for failed mail, pending applications, unverified accounts, open problem reports and more. A tile turns green at zero and takes you straight to the right admin page.
- **Admin statistics with charts.** The statistics dashboard now shows daily sign-in activity for the last 30 days and the ten largest stations by members. Pie charts for email verification and station setup progress sit beside the existing tiles.
- **Forms shown as tiles.** Each form is a tile with its status, response count, title, description, creation date and last activity, and you sort the list as you like. A click opens the editor for drafts or the analytics otherwise, and all other actions sit in the tile's corner menu.
- **See who still owes a required form.** A required form's analytics page lists the members who have not answered yet. Chasing the missing ones takes a single glance.
- **Checklists for member follow-up.** You build a list of yes/no steps, pick members by type, group, tag or by hand, and tick each one off with optional notes and full history. The matrix works on phones, can be searched and rearranged, and exports to CSV or a printable PDF, with separate permissions to view or manage.

### Changes

- **One consistent page header everywhere.** Every page now shows its title in the top header bar exactly once. Doubled titles and pages without a header title are both gone.

### Fixes

- **Signed-out visitors stay on the home page.** Arriving with an expired session sent you to the login form. Ember now quietly clears the old session and keeps you on the public home page.
- **Form questions with their own settings save again.** Rating, choice, ranking and Likert questions with their own configuration were rejected on save. They now save normally.

## v26.10.2

### New Features

- **Members can put themselves into event slots.** You can mark a member field on an event so any eligible member signs up for the slot themselves, no editing rights needed. A single slot belongs to whoever claims it first until they free it, list fields take anyone who wants in, and you can also limit them by user type or tag.

### Fixes

- **Removing a station no longer floods the log.** After a station was removed, its leftover traffic figures made the server log the same error at every save, forever. Those figures now count toward the instance as a whole, and the log stays quiet.

### Changes

- **Batch events start from an event template.** Pick a template in the batch creator and every event in the batch gets its title, description, category, registration settings and fields. This replaces the separate "field layout" feature, which is gone.
- **The member list shows who has not signed in yet.** Members who have never signed in carry an hourglass next to their name. Managers get a paper-plane button right there to send the password-setup mail again.
- **Mail keeps trying when the relay is down.** When the relay is unreachable, slow or briefly unhappy, mails stay queued and Ember retries every ten seconds until they go through. Permanent failures, such as a rejected recipient or bad credentials, are still marked failed so an operator notices.
- **System mails speak the station's language.** Accounts created through a station, by invite, application, waiting list or import, get their account mails in that station's language. Accounts that signed up on their own still get English.
- **Mail settings are tested before they are saved.** Saving the instance or station mail settings first tests the connection, and a failed test shows you the server's own error. A new clear action resets either setting to empty.

## v26.10.1

### New Features

#### Station setup walkthrough

- **A guided setup at /station/setup.** A new station's administrators walk through address and map pin, modules, permissions, groups, mail relay, branding and visibility. It ends with a first event, a first wiki page and invites, and the sidebar ticks off each finished step.
- **A setup checklist on the dashboard.** While a step is still open, the dashboard pins a checklist with links straight into the setup. It goes away once an administrator marks setup as finished.
- **Invite members by email.** Administrators send invites from the setup or the members screen, each with a single-use link to /invite. The recipient sets a password and joins, with name, member type and optional group and guardians already in place, and you can revoke invites still pending.
- **Import your roster right from the invite step.** The invite step opens the member import with its full column mapping and preview. When the import is done, you land back in the setup.

### Changes

- **Waiting-list mails use the instance mail relay.** All mails for the public waiting list now go out through the instance mailbox, like account mails do. Stations no longer need their own relay for them, and their sending limits no longer apply.
- **Moving a station sends far less data.** A transfer now carries only the original of each page image, and the destination builds the smaller sizes itself. Stations full of images move in a fraction of the time.
- **New page images take about half the space.** Ember keeps the original plus one WebP version per configured width and skips the redundant extra sizes. Existing images keep their old files until you upload them again.
- **Transfer progress shows the real total.** The file count per category shows the full number from the start. It no longer climbs as the transfer finds more work.
- **The page files browser uploads two at a time.** Dropping a batch of files now uploads two in parallel. A typical batch is done in about half the time.
- **The daily storage check clears out orphaned files.** Files left behind by deleted pages, wiki files, lost-and-found items, quiz questions and folder icons are now removed from disk. Inline knowledge-base images and board attachments are left alone for now.
- **Deleting a station also removes its lone accounts.** Accounts that belonged only to that station, and are not instance administrators, go with it. A transfer that fails halfway cleans up the same way, so no half-imported accounts linger.

### Fixes

- **Moving a station now brings members without email.** Members too young for an email address, such as youth signed up by a guardian on the waiting list, were dropped during a transfer along with their trial entry. They now arrive on the new station together with their trial and waiting-list entries.

## v26.10.0

### New Features

#### Inventory Storage and Custom Fields

- **Storage containers for your equipment.** Every room, shelf, drawer and box is a container, and containers nest as deep as you like. You find them under Station → Inventory → Storage, can search or scan them, and every item shows its container path as clickable links.
- **Each inventory gets its own fields.** Add extra fields of five kinds: date, dropdown, text, number with an optional unit, or yes/no. You arrange them in the inventory editor, and they show up on the item form.
- **Check a container scan by scan.** Station → Inventory → Check → Container check walks you through what should be in a container, confirming, missing or lost item by item. Anything you find there unexpectedly is collected separately, and a switch includes every container inside it.
- **A page just for handing out and taking back.** Under Station → Inventory → Assign, someone with the new "Assign inventory" permission picks a recipient and scans item after item. Each scan hands the item out, or takes it back if it is already with that person, and the station owner grants the permission explicitly.
- **An item is with a member or in storage.** Handing an item to a member takes it out of its container, and putting it in a container ends the handover. It can never be in both places, or halfway in between.

#### Pluggable Storage Backends

- **Choose where your uploaded files live.** Files can stay on local disk, as before, or go to an SMB share, an SFTP server or any S3-compatible store, such as AWS S3, MinIO, Backblaze B2, Wasabi, Hetzner Object Storage or Cloudflare R2. Operators pick the default under Admin → Monitoring → Storage → Backend, with no mounts or extra container privileges needed.
- **Each station can bring its own storage.** A station manager can point the station at a private S3 bucket, SMB share or SFTP host under Station → Manage → Storage → Backend. Credentials are stored encrypted, and a "test connection" button checks them before anything changes.
- **Switching storage moves everything in one go.** Ember tests the new storage, copies every file over and only then switches. A failed move leaves everything on the old storage, so nothing is ever half moved.
- **Own storage means no instance limits.** A station on its own storage is no longer bound by the instance's storage limits. Its dashboard shows a badge for its own storage instead of the limit bars.
- **A log of every storage change.** Every change, connection test, refusal and move is recorded with who did it, the station and the outcome. You find it per station under Station → Manage → Storage → Backend → Audit, and for the instance under Admin → Monitoring → Storage → Audit.
- **Move a station to another instance.** A station can now be exported from one instance and imported on another in a single flow. During the move the station is read-only with a banner naming the destination, answers changes with `503 Service Unavailable`, and opens up again if the operator aborts.
- **Help articles for the new storage options.** The help center explains switching the instance storage, choosing a station's own storage and reading the audit log.
- **New config key for storage credentials.** `storage.credentialEncryptionKey` (env `STORAGE_CREDENTIAL_ENCRYPTION_KEY`) is the AES-256 key that encrypts the storage credentials stations enter. It is needed once a station uses its own storage, and a fresh install generates it on first start.

### Changes

- **The inventory sidebar is tidier.** The Inventory label opens the overview, and Storage sits right next to it. Check splits into Member check and Container check, and the other entries move into a new Management subgroup so the list stays short.
- **The help center sidebar always stays open.** It no longer copies the collapsed state from the dashboard. On desktop it stays full width, so every article is reachable by its title.
- **Menus pop out of the collapsed sidebar.** With the sidebar collapsed to icons, hovering or focusing a group icon opens a small menu with all its entries. Subgroups open further menus, and badges stay visible on the icons.
- **Tests can be required, like forms.** Test managers can now mark a quiz as a required test. It shows up on the requirements page after login until the member has taken it, and "Start" takes them straight into the test.

### Fixes

- **Required forms keep their setting.** Marking a form as required and saving it failed. The setting now saves.
- **The required test button starts the test.** The button next to a required test opened the test's read-only page. It now starts the test right away.
- **Links survive signing in with several stations.** A link to a station page lost its destination when your account belonged to several stations. You now pick a station and land where the link pointed, and links from notifications and feeds take you to the right station directly.
- **The first-login tour leaves your links alone.** Following a notification on your first login sent you to the dashboard halfway through. The tour now waits until you open the dashboard yourself.

## v26.9.1

### Changes

- **Legal documents now share one folder.** The privacy policy, terms of service, consent text and imprint now default to `data/documents/` with one subfolder each, instead of four separate folders. When you upgrade, move your existing files there, or point `privacyPolicyDir`, `tosDir`, `consentDir` and `imprintDir` in `conf.yml` at the old paths.

### New Features

- **Scan barcodes and QR codes for inventory.** A scan button next to every internal ID field opens the camera and reads Code 128, Code 39, QR, Data Matrix, EAN and UPC labels. It works when you create, edit, search, lend and check items, and in quick check mode it stays open so you can sweep through a whole pile of returns.

### Changes

- **You can sign in without a station.** Any account with a verified email can now sign in. Administrators land in the admin panel, and users without a station land in their Account area.
- **The admin panel is always one click away.** Administrators see the shield button on the station overview, in the Account area and on the station dashboard. It hides only while you are already in the admin panel.
- **One header everywhere.** The station overview, account pages and station panel share the same avatar menu and station button. The station button only shows when you have a station to switch to.
- **Signing in brings you back where you started.** When signing in needs a second factor, Ember remembers where you were going. After the check you land on the page you first tried to open.
- **Member types are listed by responsibility.** The member type list now runs from *Manager* to *Team*, *Guardian*, *Member* and *Trial*. It is no longer sorted alphabetically.
- **The data export button says what it does.** Under Account → GDPR it reads "Download the full data export as a ZIP", and the button for a member in your care names them. The page explains that the archive covers every station of your account.
- **Trusted devices are now translated.** The trusted devices section on the security page shows its title, description, empty state and column names in your language.
- **Demo login lists accounts without a station on top.** In demo and dev mode, accounts without a station, such as the demo administrator, now sit at the top of the login picker. They no longer get a tab of their own.

### Security

- **Only members and trial members get guardians.** Team members, managers and guardians can no longer be given a guardian. Both the relations tab and the server refuse it.
- **You can no longer lock yourself out.** Nobody can remove their own permissions in the member editor. The station owner always keeps the Station Administrator permission.

### Fixes

- **Permissions now match the account you sign in with.** Switching accounts, or signing in again after an old session, could apply permissions from the previous account's station. Now single-station accounts go straight to their station and everyone else goes through the station picker.
- **The station button opens the right station.** The button in the overview header could open the station panel with the wrong station. It now asks you to pick when it has to, and picks for you when you have only one station.
- **The dashboard no longer breaks with an old station.** Opening the dashboard with a station your account does not belong to made parts of it fail. The dashboard now sends you back to the station picker.
- **Station Administrator is back in the permission list.** The entry was missing when the member's user type already granted it. It shows again and can still be switched.

## v26.9.0

### New Features

#### Account Settings Hub

- **A new Account area just for you.** Your picture, name, email, password, two-factor, theme, sessions, data export and account deletion now live on pages of their own. They belong to you as a person, not to one station membership.
- **An avatar menu in the header.** A button with your picture and name replaces the lone logout icon. It opens a menu on desktop or a drawer on phones, with Account settings and Logout.
- **One profile picture across all stations.** Your picture now follows you to every station you belong to. Existing pictures are not carried over, so upload yours once more after the update.
- **The station profile keeps only station fields.** It now holds just the station's own fields and the reminder about missing ones. Everything personal moved to the Account area, and a link in the old spot takes you there.

#### Instance Security Configuration

- **Security settings in three clear parts.** Settings → Security now leads to *Tokens & Sessions*, *HIBP* for the breach check and *Two-Factor*. Every field explains itself right next to it.
- **Two-factor management gets its own entry.** Resetting an account's two-factor and reading the audit log have their own place in the sidebar. They are kept apart from the configuration page.
- **Secrets are generated for you.** A missing `tokenPepper` or two-factor encryption key is generated on first start and written to `config.yaml`. A fresh production install starts without any manual secret setup.

#### Two-Factor Authentication

- **Authenticator apps and security keys.** You can add a TOTP authenticator app by scanning a QR code, and register one or more named FIDO2 or WebAuthn security keys. You set them up, rename and remove them under Account → Security.
- **Backup codes for emergencies.** When you add your first second factor, you get ten one-time recovery codes, shown once. You can make a fresh set whenever you like.
- **Signing in with a second factor.** With a second factor set up, Ember asks for the code or security key on its own page after your password. "Remember this device" skips the question in that browser for up to 30 days, and operators can change that limit.
- **Sensitive actions ask you to confirm again.** Changing your password or second factors, signing out other sessions, changing permissions, federation settings or instance config all need a recent second-factor check. A dialog asks for it and then carries on with what you were doing.
- **See and manage your trusted devices.** A panel lists every trusted device with when it was last seen and when it expires. You can revoke one or all of them.
- **Every two-factor event is recorded.** Adding, removing, verifying, confirming, using a backup code, trusting a device and admin resets all land in the account's log. Nothing happens unnoticed.
- **Two-factor controls for administrators.** Station admins find Security under Manage to require two-factor per user type, see who set it up and reset it for a member. Instance admins get the same under Settings → Security → Two-Factor, plus Two-Factor Management for resets and the full log.
- **No security keys in demo mode.** Demo deployments hide the security key setup. Demo accounts cannot sensibly be paired with a physical key.
- **Two-factor is on from the start.** It is enabled out of the box, and the encryption secret is generated on first start if missing. Existing sessions stay valid, but instance admins and station managers are asked to set it up at their next sign-in.

#### Public Form Submission

- **Visitors can fill in forms without an account.** Every form now has a purpose, such as contact or poll. You can publish it as a public page or embed it in a station page.
- **Spam protection for public forms.** Repeated submissions from the same visitor are filtered out without storing their IP. A rate limit stops floods from any single source.
- **See your form results at a glance.** A poll view sums up the answers per question, and contact messages arrive in their own inbox. A help article explains both.

#### Per-Station Page File Browser

- **Files, folders and tags for your pages.** Every station has its own file browser with folders and tags. The page editor picks files from the same place, so you reuse uploads instead of uploading again.

#### Page Editor Cell Types

- **A big batch of new cell types.** Callouts, quotes, accordions, PDFs, downloads, countdowns, galleries, maps, hero banners, polls, member lists and many more join the page editor. Cells can also be split or wrapped in nested rows right where they are.
- **Cut, copy and paste between cells.** The empty cell chooser even offers a paste-here shortcut.

#### Public Quiz Teaser

- **Public quizzes get a public list.** Every quiz catalog marked as public now shows up in a read-only list anyone can browse.

#### Collapsible Desktop Sidebar

- **Collapse the sidebar on desktop.** A toggle slides the sidebar down to a slim rail of icons, keeping the logo and station name on top. On phones the drawer works as before.

#### Trusted-Proxy and Cloudflare-Aware Client IP

- **The real visitor IP behind proxies.** Operators can name trusted proxies and turn on Cloudflare support in the network config. Ember then sees the real visitor IP behind Traefik, Cloudflare or both.
- **Cloudflare's address list stays current.** Ember refreshes the list of Cloudflare addresses on every start. When that fails, it falls back to the copy it ships with.

#### Shared Search Pickers

- **The same search pickers everywhere.** Picking events, forms, members, news, pages, partner stations or wiki articles now looks and works the same. That holds in the page editor and in many other places.

#### Consent Gating for Public Submissions

- **Public submissions record consent.** Anonymous forms, polls and waiting-list sign-ups now ask visitors to accept the current privacy policy and terms of service. Ember records that consent the moment they submit.
- **Only part of the IP is kept.** The IP stored with the consent is shortened first. IPv4 keeps the first three parts and IPv6 the /48 prefix.

#### Landing Page Rebuild

- **A completely new home page.** It is redesigned from top to bottom. The demo, register and hosting buttons show the right settings from the very first moment.
- **Fonts come with Ember.** Bitter and JetBrains Mono ship with Ember. The home page no longer loads fonts from Google.

#### Theme Improvements

- **No more flashing colours on load.** The instance theme, and a station's theme on its public pages, is in place before the page appears. You no longer see the colours switch after loading.
- **Visitors see the instance's default theme.** A theme saved for a signed-in user no longer carries over after logout or into a fresh tab.
- **Station themes stay on their pages.** A station's theme no longer follows you to the start page after you leave.

#### Per-Station Traffic Monitoring

- **See traffic for every station.** Admin → Monitoring → Traffic and Station → Manage → Traffic show data in and out plus requests per hour, split by signed-in, anonymous and federation traffic. You can switch the time window, measure and traffic kind, and the admin view adds a station ranking.
- **Smaller responses thanks to compression.** Text content such as JSON, HTML, CSS, feeds, SVG and calendar feeds is now compressed with gzip by default. Binary files stay as they are.
- **New config keys for traffic and compression.** `metrics.trafficEnabled`, `metrics.trafficRetentionDays` and `metrics.trafficFlushIntervalSeconds` control the traffic figures. `api.httpGzipEnabled`, `api.httpGzipLevel` and `api.httpGzipMinSizeBytes` tune compression.

### Security

- **Sign-in tokens are stored only as hashes.** Sessions and the codes for password reset, email verification and station deletion are now stored hashed with a server secret. A leaked database alone no longer hands anyone a working token.
- **Breaking on upgrade: everyone signs in again.** The upgrade removes the stored plain tokens, which ends every session and every pending recovery link. Users sign in once more. Pending password-reset, email-verification and station-delete mails have to be requested again.
- **New required production secret `auth.tokenPepper`.** It is generated on first start if you have not set one. Demo and dev runs use a fixed placeholder.
- **Text is cleaned before it is shown.** Wiki articles, station pages and legal documents pass through a strict filter that strips scripts, unsafe links, foreign frames and unknown images. Legal documents allow no images or frames at all.
- **Uploaded files are served safely.** Uploads keep their own type only if they are PNG, JPEG, WebP, GIF or PDF, and everything else is served as `application/octet-stream`. File names are cleaned, so a crafted name cannot slip extra headers into a download.
- **Federation signatures cannot be reused.** A signature now only fits the exact request, partner and method it was made for. Every request carries a one-time value, and repeats are refused.
- **Breaking on upgrade for federation.** The federation protocol version moves up on its own with this release. Partners still on an older version fail the signature check until both sides upgrade. Plan the upgrade together with each partner.
- **Sign-in and account actions are rate limited.** Signing in, registering, password and email actions are limited per IP and, where known, per email or account. Too many tries get `429 Too Many Requests` with a `Retry-After` header.
- **Nobody can probe which emails have accounts.** Registering with a known address always looks successful, and the owner gets a heads-up by mail. Failed sign-ins all get the same message and take the same time, whatever the reason.
- **A stronger password policy.** New passwords need at least 12 characters, and long passphrases are now fully used instead of only their first 72 bytes. Existing passwords keep working and move to the new scheme at your next sign-in.
- **Breached passwords are caught.** New passwords are checked against Have I Been Pwned, and again in the background after each sign-in, and a match asks for a new one next time. Operators tune or switch this off with `auth.hibp`, and an outage there never blocks sign-in.
- **A new password ends your other sessions.** Changing your own password keeps only your current browser signed in, while an admin reset signs out everywhere. Every change also sends a notice to the account's email.
- **Changing your email takes two confirmations.** Both the old and the new address have to confirm before the change happens. The old address is told someone tried to move the account and is advised to reset the password.
- **File paths cannot escape their folder.** Image, logo and legal document requests that point outside their folder are refused. Member IDs in addresses must be valid IDs.
- **Avatars no longer show across stations.** A member's picture can no longer be fetched just by knowing their ID. You have to share a station, be an instance admin or be an active federation partner of their station.
- **Federation cannot be pointed at internal hosts.** Federation and webhook addresses must use HTTPS and may not lead to local, private or reserved addresses. The new `federation.allowPrivateHosts` switch, off by default, lifts this for development.
- **Uploaded images are checked for real.** Ember looks at the actual file content for PNG, JPEG, WebP or GIF. Anything that does not match is refused before it is stored.
- **Detailed logs hide credentials.** The most detailed request logs now mask tokens, federation signatures and station identifiers.
- **Stronger cache checks.** Cache checks now use a SHA-256 fingerprint instead of a short hash. A forged "not modified" answer is no longer possible.
- **The backend no longer serves static files.** It answers only `/api/v1` and `/docs`, and the web server handles every page. A misconfigured folder can no longer end up public.
- **Error messages give less away.** Unexpected input errors now just say "Invalid input". The full details still reach operators in the admin problem feed.

### Changes

- **The admin sidebar is reorganised.** Every monitoring page now sits under "Monitoring", and the data inspector for development sits under "Dev Tools". Please update bookmarks to the old pages.
- **The waiting-list status page is rewritten.** It shows the reminder email, the date you joined, the next confirmation deadline and a rough queue position from your score. Guardians are shown by full name, or by email if there is none.
- **The join date moves to the Profile tab.** In the member editor it now sits next to first name, last name and email. It used to live in the General tab.
- **A tidier waiting-list entry page.** The detail labels line up neatly. On narrow screens they wrap cleanly.
- **A fresh log file on every start.** The server writes a new log file to the `logs` folder each time it starts. The console output stays as it was.

## v26.8.0

### New Features

#### Discovery Chain (Cross-instance Catalog)

Ember instances can now find each other. Each instance gradually builds a list of other Ember instances and shows their public stations on one discovery page, even stations it has never federated with.

- **Every instance signs what it shares.** Each instance creates its own long-lived Ed25519 key on first start, stored under `data/discovery/`. A short fingerprint of it identifies the instance in logs and the admin pages, separate from the federation keys.
- **Instances answer each other later, not right away.** A ping is acknowledged at once with `204`, and the list of known instances follows later by callback. Nobody holds a connection open, slow instances cannot pile up, and replayed or badly timed messages are refused.
- **A public list of stations.** Each instance offers its public stations with a rough member count, such as "10-50", so small stations do not reveal their exact size. Stations that are not public never leave the database.
- **A public card for each instance.** Anyone can ask an instance for its address, ID, public key, version and whether discovery is on. Admins use it to add instances by hand and to test connections.
- **Your federation partners come first.** On start, Ember asks its federation partners about themselves and adds them as the first known instances. There is no global seed list, so you decide where discovery begins.
- **Admins can add an instance by hand.** Enter its address, and Ember fetches its public key. You can pin the key you expect, so a mismatch is caught right when you add it.
- **Unreliable instances are pinged less.** Failed signatures, timeouts, bad announcements and admin downvotes lower an instance's score, and a low score pauses pings for a day. Good answers raise it again, and negative scores slowly recover, so a short outage is forgiven.
- **A blocklist that always wins.** Admins can block addresses or public keys outright, whatever their score. Blocked instances are refused in both directions.
- **Settings for each instance.** Admins can switch discovery off and choose how far pings spread, from 0 to 10 with 2 as the default. They also set the ping interval, which is at least 60 minutes.
- **Regular background rounds.** Ember pings hourly, refreshes station lists every six hours and tidies up after itself. Start-up is staggered so federation partners are known before the first round.
- **A discovery page at /admin/discovery.** It shows your own instance, the settings, the blocklist and every known instance with its actions. "Discover now" refreshes everything at once, and "Seed from federation" reads the federation partners again.

### Changes

#### Calendar Multi-day Events

- **Multi-day events span the calendar.** On the upcoming events calendar, an event over several days now shows as one bar across the week, not a chip per day. Bars carry the category colour and stack neatly when they overlap.
- **Repeating multi-day events span every time.** A repeating event over several days now spans correctly on every date. Before, only the first one did.
- **Upcoming list shows sensible date ranges.** Repeating events could show odd ranges that ended before they began. They now skip the range, and one-day events show a single date.

## v26.7.1

### Changes

#### Personal Feed Overhaul (iCal, Atom, RSS)

Your personal calendar and notification feeds got a rewrite from top to bottom. Feed readers like Thunderbird, Apple Calendar, NetNewsWire, Feedly and Reeder now show you the same context as the web app.

- **Guardians keep seeing their children's events.** The calendar feed hides an event only when every registration that matters to you is declined. Events whose deadline passed without any active registration drop out, so your calendar stays tidy.
- **Calendar entries tell the whole story.** Each event carries its category, repeat rule, registration details, custom fields and registrations per member in your care, in the station's time zone. A link opens it in Ember, and cancelled events are marked "[Cancelled]".
- **Events get a location field.** It fills the standard calendar location. Your phone and calendar app turn it into a map link you can tap.
- **Notification feed entries look their best.** Each entry shows status badges and a clear action button, with a plain-text version for readers that strip formatting. The person behind the notification is the author, and categories and images come along too.
- **Event notifications carry the event's details.** New events, reminders, cancellations and registration updates show start and end times. They also list every filled-in custom field, such as the location, meeting point or notes.
- **Feed titles say what happened.** Instead of a bare category, a title reads like "News: Q3 schedule published" or "Registration Accepted: Open Training". Long titles are cut at a word boundary.
- **Same-day events show one time range.** An event that starts and ends on the same day reads as one line, such as "When: 15 Sep 17:00 to 19:00".
- **Feed entries show the latest details.** Lost-and-found dates, lending periods, who owns an inventory item, board ticket details, procedure progress and storage warnings are read fresh. What you see is what Ember knows right now.
- **Lost-and-found images appear in feeds.** Your feed reader can load item pictures through your feed link. Nothing else in Ember becomes reachable that way.
- **Atom is the recommended format.** The feed settings page puts it first and explains why. RSS moves into a fallback section, and the calendar feed gets its own card.
- **Choose how much each feed shows.** Pick Rich, Compact or Minimal on the feed settings page, and the link you copy follows your choice. Rich is the default, and your browser remembers what you picked.
- **Your feed link stays private.** It never leaks to other sites you click through to, and search engines will not pick it up. Renewing or revoking the link asks first, because every subscribed reader stops working at once.
- **Feeds are easier for everyone to read.** Entries handle any writing direction, keep their links underlined and have large tap targets. Status symbols keep their meaning without colour, for monochrome screens and colour-blind readers alike.

#### Notifications

- **Many new events, one notification.** When lots of events are created at once, you get a single notification for all of them instead of one each.
- **Every notification speaks English and German.** Each notification type now has a translated category and message in both languages.
- **Singular and plural read correctly.** Notifications about new events, reminders and expired registration deadlines get the count right, and so does the email digest subject.

#### Recurring Events

Reminders for recurring events used to open a generic event page, and comments ran together across every date. Both now know which date you mean.

- **Reminders open the right date.** A weekly reminder takes you straight to the date it is about.
- **The event page shows one date.** It takes the date from the link, or the next one coming up. The extra "Next date" box is gone, and the date appears directly as start and end.
- **Switch between list and calendar.** The upcoming events page offers a new month view. Your browser remembers which one you prefer.
- **The calendar fits small phones better.** On a narrow phone screen it gains about 60 pixels of width, so each day cell is roughly a fifth wider.
- **Comments stay with their date.** A comment on one date of a recurring event belongs to that date only.

#### Feed Telemetry (Admin)

A new admin page under "Monitoring → Feed Telemetry" charts how feeds are used and how fast they are.

- **A clear overview of feed traffic.** Four summary cards show total requests, full renders, cache hits and the average render time. Charts break down requests by type, response times and daily volume, next to a status code table and a ranking of feed readers.
- **No feed link is tied to a person.** This is on purpose. Even an admin with database access cannot tell which member uses which reader.
- **You decide how long figures are kept.** Request statistics stay for 3 days and feed figures for 90 days by default, and both can be changed.
- **A help article explains every chart.** It covers the colours of the response time chart, the HTTP status codes that matter and how the reader ranking protects privacy.

#### News View Tracking

- **Ember notices which news you have seen.** An entry counts as seen once it has been fully visible for a moment. This is separate from pressing "I've read this".
- **Editors see who has read the news.** An eye icon on each entry opens a list of who has seen it and who has not.

#### Backend-driven Search

- **Upcoming events search everything.** The search bar now looks through all upcoming events, not just the page already loaded. It ignores upper and lower case.
- **One search bar across Ember.** Eleven pages now share the same clear search field with a magnifying glass and a clear button. That includes events, the help center, board tickets, procedures, protocols, the wiki, lending offers and quiz catalogs.

#### Other Improvements

- **Multi-day events fill the calendar.** A one-time event that spans several days now shows on every day from start to end.
- **Guardians see the inventory page.** It opens for you as soon as a member in your care owns an item.
- **Exchange type shows only where it applies.** The exchange type column appears only for those allowed to handle inventory exchanges.
- **Jump to your notification settings.** The notifications panel on the dashboard now has a shortcut to them.
- **Exchange requests show up at once.** Once you send an exchange request, the inventory card switches to "exchange pending" right away.
- **Everyone can open the quiz tests page.** The page itself decides what you may see there.
- **Event managers can use event notes freely.** They no longer need the permission for member notes.
- **Quiz reviewers see catalog names.** Permission to read test results is now enough to list them.
- **"Exchanged" is now called "Done".** The new name says more clearly that nothing is left to do.
- **The and/or choice is easier to read.** When restricting who sees something, "and" and "or" now sit side by side as two buttons, both always visible.
- **Active editor buttons are readable.** An active button in the text editor now glows in the primary colour instead of turning black on colour.
- **Comments keep their line breaks.** Line breaks you type in Chrome or Edge now survive sending.
- **Help for new events opens the right article.** The help link on the page for creating an event now leads to its own article instead of a broken redirect.
- **The settings tour step opens the right page.** The intro tour now takes you to the page it actually describes.
- **Absences line up on the profile.** The date of an absence stays centred with the name.

#### Bug Fixes

- **Notifications open the right page.** Lost-and-found, board ticket and news notifications used to land on the dashboard. They now take you to the item itself.
- **You can edit your own comments again.** Editing a comment you wrote failed in some places. It now works in news, the wiki and events.
- **The wiki tag filter filters.** It did nothing for search results and was missing when browsing. It now works in both.
- **Skipping a quick check no longer hangs.** Skipping one could leave the procedure stuck on an empty screen. It now carries on.
- **Attendance settings pages are back.** They had gone missing from the project by mistake, so demo installations showed "not found". They are there again.
- **Page permissions can be granted.** The permissions to edit and manage pages could not actually be given to anyone. Now they can.

### Technical

- **Feeds go easy on your server.** Readers that are up to date get a short "not modified" answer, and each feed link has a fair request limit. Feeds hold the last 100 notifications or about a year of events, and one broken entry no longer spoils the rest.
- **Feed summaries and contents sit in the right place.** Atom entries had their summary and full text swapped. They are now the right way round, and categories with the same name are kept apart.
- **New building blocks for limits and help hints.** A shared rate limiter and a reusable help hint now back several features.
- **Every notification knows where it leads.** Bulk-created events are gathered into one notification. A notification without a link to open is now refused outright.
- **Recurring events get date links and dated comments.** Each date of a recurring event has its own address, and comments remember their date. Partner stations that do not send a date keep working.
- **Dates in comments and reminders are real dates.** They are now handled as calendar dates rather than plain text.
- **Permission names were tidied up.** What used to be called roles is now called permissions throughout, including the help pages and translations.
- **Ember recognises you reliably as the author.** Ownership checks on comments now compare only the member's identity. This is what makes editing your own comments in news, events and the wiki work again, including at partner stations.
- **Station numbers read back correctly.** Some station numbers sent back to Ember could not be read and caused errors. They now round-trip cleanly, including for partner stations.
- **Guardian calendar feeds load faster.** Registrations for all members in your care are now fetched in one go instead of one by one.
- **Database updates for this release.** The upgrade adds what feed figures, news views and dated comments need. It also renames "Exchanged" to "Done" and makes the page permissions available.
- **The build checks conventions every time.** The convention checks for icons, help articles and translations now always run before the app is built, and two mistakes in them were fixed.
- **Admin help has its own layout.** The admin help center no longer shares its sidebar with the regular help center.
- **The wiki was split into smaller pieces.** Its larger screens were broken into smaller parts to keep them maintainable.
- **Feeds can carry pictures.** A new library lets feed entries include images.
- **Demo data behaves like real use.** The demo now creates its content the way you would, so notifications appear naturally and link to the right place. It also adds lost-and-found items and one example of every notification for the demo admin.
- **More tests guard the feeds.** New tests cover feeds, rate limits, calendar and notification output and feed figures. Plural handling and the rule that every notification needs a link are tested too.

## v26.7.0

### New Features

#### Storage Monitoring & Quota System
- **See how much storage each station uses.** Ember tracks files in five categories. They are wiki files, board attachments, page images, avatars and other images.
- **Storage limits keep usage in check.** You can set a limit per category and in total. An upload that would go over it is refused with `413 Payload Too Large`.
- **Reusable storage presets.** Named profiles such as Small, Standard or Premium can be applied to many stations at once.
- **Stations can have their own limits.** A station either gets custom limits or uses the defaults from the configuration.
- **A warning before storage runs full.** Station managers are notified when usage crosses a threshold. It sits at 80% by default and can be changed.
- **Storage figures correct themselves.** Ember recounts the real usage on startup and at a regular interval you can set.
- **Presentations take less space.** PowerPoint and OpenDocument presentations above a set size are packed more tightly, without any loss. That saves 10 to 30 percent.
- **A storage dashboard for admins.** It shows summary figures, a bar chart per station and a pie chart per category. A sortable station table shows each station's status and lets you assign presets.
- **Stations see their own storage.** Station managers get a read-only view with a bar chart and a breakdown by category.
- **Manage presets with ease.** Create, edit and delete presets, with sizes entered as a number plus MiB, GiB or TiB. Apply one to several stations at once, and deleting asks first.
- **Storage defaults live in the configuration.** The new `storage` section in config.yaml holds the defaults for limits, compression, the warning threshold and the recount interval.
- **Help articles for storage.** Both the admin view and the station view have their own article.

#### Federation Version Broadcasting
- **Partners learn each other's version on startup.** When Ember starts, it greets every partner station and they swap version information.
- **Partners can ask for the version.** The new `/remote/federation/ping` address answers with the current federation version.
- **Older partners get a version.** Partners added before versions were tracked are updated on startup.
- **New partners start with the right version.** They are created with the current federation version instead of a placeholder.
- **The version covers more of what partners exchange.** It now also reflects the data shared for lending and boards.

#### Public Pages (Layout Editor)
- **Build your own public pages.** Stations can now create public pages with a light layout editor. It works much like the page builders you may know from WordPress.
- **Lay out pages in rows.** Each row holds one to four columns, and you choose their widths freely.
- **Text, images and videos.** A cell can hold formatted text, an uploaded image you can size and fit, or a video. Videos come from YouTube or a direct link.
- **Pages look good on phones.** On a small screen, columns stack on top of each other.
- **Nest pages up to three levels.** Their addresses follow the nesting, such as `/page/about/team`.
- **Pick a landing page.** One page can be your station's landing page. It appears first in the sidebar.
- **Stations get a readable address.** Each station gets a short, readable name in its address. It is made from the station name, and you can change it.
- **Pages are ready for search engines.** Each page has its own description and preview image. Ember fills them in from the content when you leave them empty.
- **Public pages are rendered on the server.** Formatted text arrives as finished pages, ready to read.
- **Public pages wear your station's look.** They show the colours and feel you chose for your station.
- **Images are tidied up for you.** You can upload images up to 5 MB per page. Images no longer used are cleaned up when you save.
- **Copy, cut and paste rows and cells.** Paste buttons appear between rows, right where you want the content to go.
- **Shape columns by hand.** Split a column with one click, swap two columns, and drag to resize them.
- **Move rows up and down.** Buttons on each row change its position.
- **Preview before you publish.** The editor switches between editing and a preview.
- **Duplicate a page.** The copy includes every row and cell.
- **Publish when you are ready.** Publishing needs the page manager permission. When a parent page is unpublished, its child pages are hidden too.
- **A help article for pages.** It explains how to manage your pages.
- **Demo pages to explore.** The demo comes with nested sample pages such as Welcome, About us, Our team, Equipment and Join us.

#### Station Public URL
- **Give your station a public address.** Each station has a readable address you can change, such as `/public/station/jugendfeuerwehr-musterstadt`.
- **Addresses are made for you.** A new station gets one from its name, and Ember makes sure no two are alike.
- **Old links still work.** Links that use a station's internal number now forward to its readable address.
- **Station discovery uses readable links.** Links from station discovery now use the station's readable address.
- **Change your address in the settings.** You edit it in the federation settings, and Ember warns you when it is already taken.

#### Public Waitlist Registration
- **Waiting lists open to the public.** Each waiting list can be made public. People can then sign up without an account.
- **Choose which fields the public sees.** Each waiting list field can be shown on the public form or kept hidden.
- **Sign-ups confirm their email.** Everyone who signs up gets a confirmation email. The link in it is valid for 24 hours.
- **You approve each public sign-up.** A confirmed sign-up waits as pending. Someone allowed to edit waiting lists has to approve it.
- **Approve or reject with all details at hand.** Pending entries on the waiting list page open up to show the full sign-up. You approve or reject them right there.
- **Know when someone signs up.** Members who may edit waiting lists are notified about each new public sign-up.
- **Switch public waiting lists on per station.** A station setting decides whether public waiting lists are offered at all.
- **A friendly public sign-up page.** Visitors pick a list and fill in its public fields and their guardians' details. Then they confirm their email.
- **A page to confirm the email.** The link in the email opens a page of its own that confirms the address.
- **Waiting lists appear in the public sidebar.** When enabled, your public station sidebar links to them.
- **Guardians have first and last names.** Their names are now two fields. That lets Ember turn them into accounts directly.

#### Public Blog
- **Turn news into blog posts.** A switch in the news editor marks an entry as a blog post.
- **Blog posts are easy to spot.** In the internal news list they carry a "Blog" badge.
- **A public blog for your station.** The blog lists each post with title, excerpt, author and date. Opening one shows the full post.
- **The blog can be your landing page.** When you have not chosen a page, the blog greets your visitors.
- **Switch the blog on per station.** A station setting decides whether the blog is available.
- **The blog has its place in the sidebar.** Its link sits after the landing page and before the calendar.

#### Station Settings UX
- **Settings save as you go.** Federation settings now save shortly after each change. There is no save button to forget.
- **You see when it is saved.** A spinner says "Saving…", and a checkmark says "Saved".

#### Wiki: Presentation Support
- **Upload presentations to the wiki.** PowerPoint (.pptx, .ppt) and OpenDocument (.odp) files are welcome.
- **Presentations open in your browser.** Ember turns them into PDFs on the server, so anyone can view them without extra software.
- **No waiting after an upload.** The upload finishes at once, and the conversion runs in the background. You can see whether it is pending, done or failed.
- **Present slide by slide.** A full-screen mode shows PDFs and presentations one slide at a time, with a slide counter. Move on with the keyboard, a click or a swipe.
- **Controls step out of the way.** In presentation mode, the header and buttons fade out when you stop moving. Only your slides remain.
- **Download the original file.** The file page offers the original presentation for download.
- **Replace a presentation.** Upload a new version, and Ember converts it again.

#### Procedures (Abläufe)
- **Procedures guide people step by step.** The new Procedures module gives each person a checklist for a structured process. Think onboarding or handing out equipment.
- **Templates for recurring procedures.** Reusable templates hold the items and the order they depend on. Procedure managers look after them.
- **Start a procedure your way.** Create one from scratch or from a template. You can still change its items before you submit it.
- **Assign procedures to people.** Pick one or more members to work on a procedure.
- **Items can wait on other items.** An item can depend on others. Blocked items show a lock icon.
- **Keep some steps private.** Procedures and single items can be private. Then only members allowed to edit procedures see them.
- **Let assignees tick off items.** Some items can be checked by the assigned members themselves. All others need the permission to edit procedures.
- **Resolve and reopen.** You can resolve a procedure at any time and reopen it when needed.
- **Stay informed about procedures.** You are notified about assignments, resolutions, reopenings and finished items.
- **Open procedures in the sidebar.** A badge counts your open procedures. Everyone with an assigned procedure sees it.
- **Demo procedures to try.** The demo includes two templates, onboarding and equipment handout, and four sample procedures in different states.
- **A help article for procedures.** It gives an overview of the whole module.

#### Server-Side Rendering
- **Public pages load faster.** Public pages are now rendered on the server, and the help center is prepared ahead of time. The signed-in station and admin pages keep working as an app in your browser.
- **Backend and frontend ship separately.** They now come as two Docker images. Operators can scale and deploy each one on its own.

#### SEO
- **A sitemap for search engines.** `/sitemap.xml` lists the static pages and every station that can be discovered.
- **Search engines know where to look.** `robots.txt` welcomes them to `/discovery`, `/public/` and `/helpcenter/`. It keeps them out of `/station/`, `/admin/` and `/api/`.
- **Each public page names its true address.** Public pages tell search engines which address is the original, so duplicates do not compete.
- **Shared links look good.** Public pages carry the details social networks need for a link preview. That means title, description, image and language.
- **Search results can show more.** The home page, station pages, the public calendar and wiki navigation describe themselves to search engines. Events can then appear as rich results.
- **Search the site from the results page.** Search engines can offer a search box for the discovery page.
- **Bigger previews in search results.** Search engines may show large images, full snippets and video previews.
- **Verify your site with Google.** The optional `NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION` setting connects your instance to Google Search Console.
- **Help pages are easy to find.** All 142 help pages describe themselves for search engines and link previews, based on their title and subtitle.

#### Data Tracking System
- **One record of all the data Ember keeps.** A single description now covers every kind of data that moves with a station, goes into a privacy export or is erased on request. Each item records whether it has been checked, and editing its description keeps that check intact.
- **Station moves follow that record.** Exporting and importing a station is now driven by the record instead of hand-written steps for each kind of data.
- **Data moves in the right order.** Ember works out by itself which data has to come first, based on how it connects. Nobody has to keep a list by hand.
- **Data linked from elsewhere moves too.** Data that belongs to a station only through another link, such as member accounts, is now included in a station move.
- **Linked details travel along.** An export carries details such as a member's email address. The import uses them to reconnect everything on the other side.
- **Each kind of data has the right shape.** Station-wide settings travel as a single entry, simple lists as plain lists. Everything is labelled by what it is.
- **Accounts move with the station.** Members' accounts and sign-in details come along. An account that already exists with the same email is linked, and new accounts must set a new password at first sign-in.
- **Partnerships survive a station move.** Partner stations and everything shared with them move along. That covers boards, inventory, the wiki, protocols, quizzes, events and news, so partners still recognise the station afterwards.
- **Privacy exports follow the record.** The export of a person's data now comes from the same record. It finds everything tied to their account or membership and groups it clearly.
- **Erasure follows the record too.** For every kind of data, the record says whether it is deleted, emptied, anonymised or kept, and anonymised names read "Deleted". Ember applies these rules in a safe order and logs what it keeps.
- **A data overview for development.** A page at `/admin/data-tracking` shows the record, but only on development instances. You can search, filter, mark entries as checked and edit erasure rules, and warnings point out missing links or rules that would not actually delete anything.
- **Partner members can attach files to board tickets.** Board attachments now remember the uploader the same way the rest of the board does. So members from partner stations can attach files too.

#### Documentation
- **Every setting explained for hosts.** The hosting help page now lists all environment variables by topic, from database and mail to theming and Docker. Each one comes with its default and a plain explanation.

### Improvements
- **Sturdier answers from the server.** About 50 server answers now follow a fixed shape. Mistakes in them are caught before a release instead of after.
- **Automated tests retry on hiccups.** A failed test run is tried once more, and publishing images is retried up to three times. Short outages no longer break a release.
- **Coverage checks no longer run tests twice.** The coverage step reuses the results of the earlier test run.
- **One file picker for the wiki.** Every file upload in the wiki now uses the same styled picker.
- **The frontend image builds much faster.** It now starts from a slim Node.js base.
- **Inventory items tell you their status.** An item now shows "Assigned" or "Available" instead of a vague "Active".
- **Avatars in the inventory editor.** Member names there now show their avatars.
- **The members badge counts more.** The Members entry in the sidebar now counts waiting list entries as well as pending changes.
- **The inventory badge counts exchange requests.** The Inventory entry in the sidebar shows how many exchange requests are waiting.

### Bug Fixes
- **The waiting list help tile leads somewhere.** The waiting list tile on the help center home page linked to a page that does not exist. It now opens the waiting list article.
- **Inventory items show who has them.** The item page did not show the member an item is assigned to. It now reads the assignment directly and shows it.
- **Permission choices are no longer lost.** Unticking a parent permission, such as managing lost and found, threw away the sub-permissions you had picked. Ticking it again now brings them back.
- **My Inventory appears only when it has something.** The tab showed even when nothing was assigned to you. It now appears only when you have items.
- **Removed members no longer leave quiz traces.** Deleting a member could leave quiz attempts pointing at nobody. Their attempts are now removed with them, and grades they gave simply lose the grader's name.

### Technical Changes

#### Data Tracking Backend
- **The data record has a clear structure.** Each kind of data is described the same way. That covers its fields, links, privacy role and how it is erased.
- **Ember reads its own database layout.** It collects the tables, fields, links and their descriptions straight from the database.
- **Changes to the layout are noticed.** A fingerprint of each table's fields and links shows when something changed and needs checking again. Descriptions are left out of it on purpose.
- **The record stays in step with the database.** Refreshing it picks up the current layout and descriptions. Checks already made are kept.
- **Ember finds how data belongs to a station.** It follows the links from any kind of data back to its station on its own.
- **A reliable order for moving data.** The order is worked out from the links between data. Loops are broken safely, and the result is the same every time.
- **Four flows share one engine.** Station export, station import, privacy export and erasure all run on the same rules.
- **The data overview has a backend of its own.** It runs only on development instances, and tests can point it at their own copy of the record.
- **The data overview stays out of production.** Its addresses exist only on development instances.
- **Much less hand-written database code.** Station moves lost about 2400 lines of it, the privacy export about 470 and erasure about 100. Everything works the same from the outside.
- **Database updates for this release.** Board attachments now remember their uploader in the way partner stations understand. Quiz attempts are tied to their members, and leftovers from deleted members are cleaned up.
- **The data record was corrected.** Some entries named fields that do not exist, and some described data that is gone. Both are fixed.
- **Old helper tools were retired.** The command line tools for reviewing the record are gone, since the data overview does their job. The refresh tool stays, because only it can read the live database.

#### Storage Monitoring Backend
- **Five storage categories.** Wiki files, board attachments, page images, avatars and other images are counted separately.
- **Storage usage is kept up to date.** Usage changes with every upload and deletion and can be read per station and category.
- **Presets are stored and applied.** Presets can be created, changed, applied to stations and reset.
- **Limits are checked on every upload.** Ember checks the station's limits and the size of single files and images. It also notices when the warning threshold is crossed.
- **Usage is recounted regularly.** A recount of files and database runs a minute after startup and then at the interval you set.
- **Presentations are packed tighter.** They are recompressed at the highest setting without losing anything.
- **Storage has its own server addresses.** They cover station usage, the admin overview, presets and recounts.
- **Station managers hear about full storage.** Crossing the warning threshold notifies them.
- **Sizes read like people write them.** Values such as "5G" or "50M" are understood and shown the same way.
- **Storage settings can come from the environment.** Every storage setting can be overridden with a `STORAGE_*` environment variable.
- **Database updates for storage.** New storage for usage figures and presets, plus limits and a preset on each station.

#### Federation Version
- **Partners are greeted after startup.** Two minutes after Ember starts, it contacts every partner station.
- **The version answer has a fixed shape.** `/remote/federation/ping` always answers in the same, well-defined form.
- **The version covers lending and boards.** What partners exchange for lending and boards now counts towards the version.
- **Partners without a version are updated.** On startup, every partner still on the placeholder version gets the current one.
- **New partners start current.** A partner gets the current federation version as soon as it is created.

#### Sitemap
- **The sitemap is built properly.** It is now generated from structured data instead of pieced together as text.
- **The sitemap says what changed when.** Wiki files and pages carry the date of their last change, and overview pages take the newest date of what they contain.
- **The sitemap answers quickly.** It is kept in memory for six hours.

#### Station Applications
- **Application states are fixed values.** An application's state can only be one of the known states.
- **Existing applications were tidied up.** Their states were brought into one consistent spelling.

#### Public Waitlist Backend
- **A pending state for sign-ups.** Waiting list entries can now wait for approval.
- **Confirmation links expire.** Each email confirmation link is stored and runs out after 24 hours.
- **Editors hear about public sign-ups.** A new public sign-up notifies the members who may edit waiting lists.
- **A confirmation email in two languages.** It is available in German and English.

#### Guardian Schema
- **Guardian names have two parts.** First and last name are stored separately, so an account can be created straight away.
- **Guardian details have one shape.** The app now uses a single, named description for guardian details.

#### Badge Convention
- **Badges look the same everywhere.** A build check rejects hand-made badges and asks for the shared badge styles.
- **54 badges were brought in line.** Each one now uses one of the shared badge styles.
- **A nudge towards named types.** A build check warns when a piece of state is described inline instead of by name.

#### Bug Fixes
- **Email confirmation is saved again.** Confirming an email address was not stored correctly. It now is.
- **Deleting an event comment works.** Deleting a comment did not mark it as deleted. It now does.
- **Waiting list field settings save.** The settings of a waiting list field could not be read when saved. They now arrive in the form the app sends.

#### Federation Routes
- **Federation has a new address.** Federation management moved to `/station/federate`. Its old address clashed with other entries in the sidebar.

#### Help Center
- **The roles page uses the right words.** It now speaks of "User types & permissions".
- **The federation page is complete.** Texts that were missing from it have been added.
- **Form labels look the same everywhere.** A shared label style replaces many copies of the same pattern.
- **The page editor has a help page.** It now has a help center page of its own.

#### Demo Service Refactoring
- **The demo setup is split into parts.** Its main part shrank from 2180 to 679 lines, and four parts now stand on their own:
  - Members: groups, profile fields, users and tags.
  - Events: categories, events, attendance and templates.
  - News: news articles with their comments.
  - Pages: public pages with their hierarchy.
- **Demo data loads faster.** Members are created first, then all other parts load at the same time.

#### Frontend Architecture
- **One way to set a page's true address.** Public pages share a single helper for it, based on `NUXT_PUBLIC_SITE_URL`.
- **The sitemap finds all stations.** A small server step fetches every discoverable station for the sitemap.
- **The frontend build no longer hangs.** A wrapper waits for the build to finish and then stops the build tool, which could otherwise hang forever.

#### CI/CD
- **Image builds no longer wait on themselves.** The Docker build no longer waits for its own check, which could block it forever.

## v26.6.1

### New Features

#### Mention System
- **Mention whole groups at once.** In a comment you can now mention an entire group in one go. The same works for everyone on an event, everyone registered, or everyone who declined.
- **Mentions show faces and colors.** The mention list shows each member's avatar, name color and tags. You find the right person at a glance.
- **Guardians hear about event mentions too.** When a group is mentioned on an event, the guardians of those members are notified as well.
- **Mentions respect who may see things.** On content kept to certain groups, the mention list only offers members who can actually see it.

#### Notifications
- **Mentions get their own notification.** Being mentioned now has a notification of its own. It no longer looks like a reply to a comment.
- **Mentions in news comments notify.** When you mention someone in a news comment, they now get a notification.
- **News notifications name the author.** A notification about a new news post now tells you who wrote it.

#### Event Detail
- **Event pages are split into tabs.** An event's page now has an Info tab and a Registrations tab.
- **Simple cards for pending registrations.** If you cannot confirm registrations, you see pending ones as simple cards.

### Bug Fixes
- **Registering uses the event's own date.** Signing up for or declining an event could be recorded for today instead of the event's day. It now uses the right date.
- **Recurring events show today correctly.** A recurring event that had not ended yet could skip today as its next date. Today now shows as the next date while the event is still running.
- **Event notifications open the right event.** Comment and mention notifications for events led to the list of events. They now open the event itself.
- **No more empty requirements page.** With nothing left to complete, the requirements page showed an empty screen. It now takes you to the dashboard instead.
- **Avatars stop reloading in the mention list.** Avatars in the mention list loaded again every time you hovered over them. They now load once.

### Improvements
- **Home page tiles fit every screen.** The tiles show one at a time on a phone, two on a tablet and three on a desktop. The arrows are always visible, and you can swipe on touch screens.
- **The logo takes you home.** Clicking the Ember logo or name in the sidebar opens the home page.

### Technical Changes

#### Database
- **Full names are stored ready to use.** The database now keeps each account's full name ready. Lists and searches no longer piece it together each time.

#### Backend Architecture
- **Group mentions have fixed kinds.** Mentioning a group, an event, the registered or the declined now uses a fixed set of kinds. Typos in these can no longer slip through.
- **Group mentions become personal notifications.** A mention of a whole group is turned into one notification for each member behind it.
- **Mentions are no longer comment replies.** The mention notification is its own type with its own text, separate from news comment notifications.
- **Wiki comment notifications moved behind the scenes.** Notifications about wiki comments are now raised in one central place. You will not notice a difference.
- **News finds its author's name itself.** Ember now looks up the author's name of a news post on its own. Nobody has to hand it over anymore.
- **Event dates are checked on the server.** A one-time event takes its date from its start time. For a recurring event, Ember checks that the date falls on the right weekday.
- **Member suggestions follow visibility.** The member suggestions can now be limited to the members who may see a given entry.

#### Frontend Architecture
- **Registrations live in their own tab.** The registration part of the event page moved into its own piece. The event page shrank from 506 to 319 lines.
- **One list for every kind of mention.** The mention box now suggests members, groups and special mentions in a single list.
- **Avatars react only to real changes.** An avatar now reloads only when the member it shows actually changes.

## v26.6.0

### New Features

#### Boards (Planer)
- **Boards for planning your work.** Every station can now plan on its own kanban boards. You shape the lanes yourself and drag tickets between them.
- **Tickets with everything they need.** A ticket carries a title, a formatted description, one of five priorities, an assignee and a due date. Custom fields hold whatever else your station tracks.
- **Checklists inside tickets.** Add a checklist to a ticket, reorder it by dragging and watch the progress bar fill up. Finished items can be cleared in one go.
- **Tickets can point at each other.** Link tickets as related, blocking, blocked by, causing or caused by. The links show neatly on the ticket.
- **Web links on tickets.** You can attach links to outside websites to a ticket.
- **Files on tickets, with previews.** Upload files to a ticket and see them as tiles. Images, PDFs and CSV tables open full screen, and the arrow keys take you through them.
- **Colored labels for sorting.** Each board has its own colored labels, which you can create right where you pick them. Filter the board and the archive by label.
- **Wiki pages on tickets.** Link a wiki page to a ticket by searching for its title. The ticket shows the folder it lives in.
- **Talk it through in comments.** Tickets have threaded comments with mentions. You can reply, edit and delete.
- **Watch a ticket you care about.** Follow a ticket and get a notification whenever it changes.
- **The whole story of a ticket.** One timeline shows comments, lane moves and every change to priority, labels, text, due date and fields. Lane colors, priority icons and label badges make it easy to scan.
- **Give your lanes a color.** The color tops the lane's column and tints the ticket's status button.
- **Lanes that assign people.** A custom field can name a member who takes over a ticket as soon as it reaches a certain lane.
- **A backlog out of sight.** Switch on a backlog for a board, and its tickets wait in a hidden lane. They have their own table view.
- **An archive for finished tickets.** Done tickets disappear from the board after a set number of days. You find them in the archive, filterable by label.
- **Only the boards you can open.** The board overview lists just the boards you have access to.
- **Managers keep boards in order.** A management page lets managers create, edit and delete boards. Each card has its own settings button.
- **Board settings in one place.** Edit lanes and their colors, add custom fields of several kinds and switch the backlog on or off. You also decide here who may view and who may edit.
- **Reminders for overdue tickets.** Once a day, the assignee hears about overdue tickets that have not reached the last lane.
- **Search across all tickets.** Search finds tickets by title and description, with the best matches first.
- **Look without touching.** If you may only view a board, you see everything without any edit controls.
- **Drag and drop throughout.** Drag tickets between lanes and see where they will land. Checklist items have handles to drag them by.

#### Board Access & Permissions
- **Managers reach team boards.** A board kept to the team was closed to managers. Managers can now open it, as their role always implied.
- **Ember knows who may edit a board.** The board now checks with the server whether you may edit it.
- **Decide who sees and edits each board.** Limit viewing and editing per board by role, group or tag.

#### Permission System
- **Permissions that fit each task.** The old handful of roles gives way to a tree of permissions. Each area, such as events, members, inventory or boards, has its own rights to read, edit and manage.
- **Permissions for whole member types.** A new page lets you grant extra permissions to every trial member, member, guardian or team member of the station at once.
- **A clearer way to pick permissions.** Permissions now come in collapsible groups with icons and short descriptions. This replaces the old role checkboxes on members and groups.
- **The sidebar shows what you may use.** Each sidebar link now appears according to your actual permissions. It no longer depends on being a manager or not.
- **Read without edit controls.** If you may read but not edit, for example the waiting list or a board, you see the content without the edit buttons.
- **Finer rights for tests.** The single quiz manager role is split up. Viewing and editing catalogs, setting up tests, reading results and reviewing each have their own permission now, and managing tests and protocols sits with station administration.
- **Separate rights for protocols.** Setting up protocols, starting runs and grading members are now three separate permissions.
- **One right covers all news editing.** Creating, editing and deleting news posts now fall under one news editing permission. Sharing news with partners has a permission of its own.
- **Separate rights for forms.** Seeing a form's results and creating or editing forms are now separate permissions. Forms can also be kept to chosen members.
- **Finer rights for station settings.** General settings, look and feel, mail, modules and import and export each have their own permission now. In the sidebar, managing and partner stations are now groups of their own.
- **Finer rights for members.** Importing, deleting and setting permissions or member types now need the right to edit members. Managing tags and reading member details have permissions of their own.
- **Finer rights for inventory.** Changing and deleting items needs the right to edit inventory. Reading what a member holds needs the right to read members.

#### Member Identity & Display
- **Groups give names their color.** Give each group a color. A member's name then shows in the color of their highest-ranking group, everywhere in Ember.
- **Tags as colored badges.** Mark a tag as visible, give it a color and a place. It then shows as a small colored badge next to member names.
- **One way to name a member.** A member is now identified the same way everywhere, from storage all the way to the screen.
- **Names come straight from the member.** Every displayed member name is now taken from that one shared identity.

#### Waitlist Guardians
- **Several guardians per waitlist entry.** An entry on the waiting list can now hold several guardians, each with name, email and phone number. This replaces the single parent name and email.
- **Guardians get their accounts automatically.** When you accept an entry from the waiting list, Ember creates accounts for its guardians. They can sign in right away and are linked to their child.
- **New entries start as trial members.** An entry on the waiting list counts as a trial member until you accept it. Then it becomes a full member.
- **Guardian details at a click.** Click an entry on the waiting list to see its guardians' contact details.
- **Room to add an entry.** Adding someone to the waiting list now opens a full page instead of a small dialog.
- **Adding to the waitlist has its own right.** A new permission lets people add entries to the waiting list without being able to edit it.

#### Member Detail & Edit
- **A member's page in tabs.** A member's page is now split into Profile, Permissions, Guardians, Absences, Inventory and Notes.
- **Link guardians and members.** A new Relations tab lets you assign guardians to members, and members to guardians.
- **Absences on the member's page.** If you may edit members, you can add, view and delete absences right from a member's page.
- **See what a member may do.** The Permissions tab shows a member's type, their permissions in plain words, their groups and their tags.

#### Event Reminders
- **Reminders before an event.** Events and event templates can carry several reminders, each a chosen number of days before.
- **Reminders go out on their own.** Ember sends the reminders in the background to everyone they concern.
- **Reminders reach the right people.** For a public event, everyone who did not decline is reminded. For an event with registration, only those registered or waiting for confirmation.
- **Templates pass on their reminders.** An event created from a template takes over the template's reminders.

#### Federated Comments
- **Comment on partner events.** You can comment on events your partner stations share with you. Each comment shows the station its author belongs to.
- **Comment on partner news.** News posts shared by partners can be commented on too, with full threads.
- **Comments on wiki files.** Wiki files have threaded comments, shared with partners as well. Deleted comments are hidden rather than wiped.

#### News Federation
- **Share news post by post.** Choose for each news post whether it goes to all partners or only to some.
- **Decide who reads shared news.** Set the minimum role a partner station's member needs to see your shared news.
- **Partner news in your feed.** Posts from partners show up right in your news list, marked with a badge.

#### Event Cancellation
- **Cancel an event with a reason.** Managers can now cancel an event and say why.
- **Events cancel themselves when too empty.** An event that has too few registrations by a set date is cancelled automatically.
- **Everyone hears about a cancellation.** All registered members get a notification when an event is cancelled.

#### Quiz & Test Improvements
- **Four new kinds of questions.** Tests now offer list questions, ordering, matching and fill in the gap.
- **Read catalogs without editing them.** If you may view catalogs, you see their questions and answers without edit controls.
- **All attempts on a Results tab.** A test's page now has a Results tab that lists every attempt.
- **An attempt opens in one go.** Opening an attempt now loads the attempt, its questions and the member together.
- **Faster grading.** Shortcuts let you mark a question as reviewed and go on, or mark it and finish. The buttons are smaller, and the navigation on phones is tidier.

#### Other
- **See what you still need to do.** A new page shows the requirements you still have open at your station. A badge in the sidebar keeps count.
- **Boards are easier to get around.** Board addresses are readable, partner tickets can be linked and the activity tab runs in order. You can also move around with the keyboard.
- **Sidebar counts load at once.** All the badges in the sidebar now arrive together in a single request.
- **Clearer error files in development.** Error files written during development are named by time, source and a short code. Errors caught in the browser are reported there too.
- **The end date fills itself in.** When you set a start date and the end date is empty, Ember fills it in for you.
- **Two more modules to switch.** Test protocols and boards can now be switched on and off on the modules page.

### Improvements

- **Fields without borders.** Input fields can drop their border for clean editing right in place.
- **Click a title to edit it.** A ticket's title reads like a heading. Click it, and you can edit it in place.
- **Member picker opens ready.** The member picker opens its list right away, with the cursor in the search.
- **Priority picker opens ready.** The priority picker opens its list right away.
- **Editors close when you click away.** The editors for lane, priority, assignee and due date close when you click outside them.
- **A proper color picker.** Lane colors in the board settings are now chosen with a dedicated color field.
- **Drop-down lists keep their size.** Drop-down lists no longer grow too wide or shrink too far in tight layouts.
- **The checklist progress bar shows up.** The checklist's progress bar was invisible. It now shows in the station's main color.
- **Overdue tickets stand out.** A due date that has passed shows in red on the ticket's tile.
- **See attachments at a glance.** A paperclip with a count on each ticket tile shows how many files it carries.
- **A real Save button for descriptions.** Saving a ticket's description now uses a proper Save button instead of a checkmark.
- **Comments are sent with Submit.** The comment button now reads Submit, the same as on news comments.
- **Your boards in the sidebar.** The sidebar lists only the boards you can view. Managers see all of them on the management page.

### Bug Fixes

- **Board access settings show what was saved.** The view and edit settings of a board came back empty. They now show the groups and roles you saved.
- **Board management has its buttons back.** The management page did not show the controls to create and edit boards. They are back.
- **Board managers see only their boards.** Board managers saw every board in the sidebar. They now see only the boards they may open.
- **Managers reach boards kept to the team.** A board kept to the team stayed closed to managers. Managers can now open it.
- **Downloading files works.** Downloading a file from a ticket failed with an access error. It now downloads.
- **Creating tickets works again.** Creating a ticket could fail with an error. It now goes through.
- **Wiki links show clean folder paths.** A wiki file at the top level showed its folder with a doubled slash. The path now reads correctly.
- **Wiki links appear right away.** A ticket's wiki links did not show on its first opening. They now load straight away.
- **The checklist progress bar was invisible.** It used a color that did not exist. It now shows.
- **Lane tops had no color.** The colored border on top of each lane used a color that did not exist. It now shows the lane's color.

---

### Technical Changes

#### Database
- **A home for everything on boards.** The database gains storage for boards and all their parts. That covers lanes, fields, tickets, links, checklists, comments, watchers, files, history, labels and wiki links.
- **Tickets are ready for search.** Ticket text is indexed as it is saved, so search stays fast as boards grow.
- **Each board remembers its backlog.** A board keeps track of which lane is its backlog.

#### Backend Architecture
- **Boards are built from solid parts.** Boards, lanes, fields, tickets and everything attached to them each have a clear shape on the server. Priorities are one of them.
- **Boards are stored and loaded together.** One place on the server saves and loads boards with their lanes, fields, labels, access rules and backlog.
- **Tickets are stored and loaded together.** One place saves and loads tickets with all they carry. The activity timeline comes from a single combined lookup.
- **Board access follows the role ranking.** Access to a board takes the order of roles into account. Labels and the backlog are managed in the same place.
- **Tickets keep track of their life.** Moving a ticket assigns the lane's member, and mentions in comments are picked up. Watchers are notified, and every change lands in the history.
- **Over fifty ways in for the app.** Boards and tickets come with more than fifty server endpoints behind the screens.
- **A daily check for due dates.** A background job sends the daily reminders about due dates.
- **One notification for any ticket change.** Watchers get the same kind of notification, whatever changed on the ticket.
- **Mentions work on tickets.** Mentioning someone in a ticket comment notifies them with a link to the ticket.
- **Small helpers got their own place.** A few small pieces of board data moved into their own files during formatting. You will not notice a difference.

#### Frontend Architecture
- **New pages for boards.** Boards come with their own pages for the overview, a single board, a ticket, the settings, the backlog and the archive. Five new help pages explain them.
- **New building blocks for boards.** Ticket tiles, checklists, the activity timeline, ticket links, a label picker and a color field are new. The existing drag list is reused.
- **The app talks to boards in one place.** More than forty calls cover boards, tickets, labels, files, wiki links and history.
- **Files open only for you.** Ticket files are downloaded and previewed with your sign-in. Bare file addresses are no longer used.

#### Permission Architecture
- **Permissions and member types are well defined.** Station and instance permissions and member types each have a fixed set. Loose role names are gone.
- **Member type permissions are stored.** Permissions given to whole member types are saved, and the app can read and change them.
- **The permission picker explains itself.** It shows permissions as a tree and hides those already implied. It also says where a granted permission comes from.
- **Every page checks the right permission.** Each server action now checks the specific permission it needs. Reading often needs only a read permission where it used to need a manager.

#### Member Identity
- **Every member has a lasting identity.** Each member now carries a lasting identifier together with their station. Members of your own and of partner stations are handled the same way.
- **Names are looked up once.** Member names are resolved in one place and kept at hand for speed.
- **Mentions name station and member.** Mentions now point to both the station and the member. Older mentions keep working.

#### Waitlist & Guardians
- **Guardians of waitlist entries are stored.** Each entry's guardians are saved and leave with the entry. The parent name and email of older entries were carried over.
- **Reminders are stored and tracked.** Ember saves the reminders of events and templates and remembers which ones it has sent.
- **Reminders are checked every half hour.** A background job looks for due reminders every 30 minutes.
- **Questions of an attempt load together.** All questions of a test attempt are fetched in one go.

#### Test Coverage
- **Board storage is well tested.** More than 20 tests cover how tickets, lanes, labels, files, fields, links, search, history, backlog and wiki links are stored.
- **Board logic is well tested.** More than 25 tests cover editing, access by role, labels, backlog, fields, files, comments, watchers and moving or linking tickets.
- **Background jobs stay out of the coverage count.** The two reminder jobs run in the background and are left out of the test coverage figures.
- **Every coverage goal is met.** Tests cover 95% of storage code, 90% of the logic and 80% of the request handling.

---

## v26.5.0

### New Features

#### Comments & @Mentions
- **Talk about events in comments.** Events now have threaded comments, just like news.
- **Mention people with an @.** Type `@` in any comment to find a member and tag them. They get a notification.
- **Replies reach the author.** When someone replies to your comment, you hear about it.
- **Deleted comments keep their replies.** Deleting a comment that has replies no longer wipes the thread. The comment reads "This comment was deleted" instead.

#### Notes
- **Notes on items, members and events.** Managers can keep internal notes on inventory items, member profiles and events. Every note keeps its version history.
- **Member notes stay with managers.** Notes on a member's profile are visible to managers only.

#### Feeds (iCal, RSS, Atom)
- **Your events in your calendar.** Subscribe to your events in Thunderbird, Outlook, Google Calendar or any other calendar app.
- **Notifications in your feed reader.** Follow your notifications as an RSS or Atom feed.
- **You control your feeds.** Create, renew or revoke your feed's secret link. Choose which kinds of notifications appear in it.
- **A nudge to set up feeds.** The dashboard reminds you when your feeds are not set up or not in use.

#### Event Templates
- **Templates for recurring events.** Save an event as a template with all its fields, attendance settings and registration limits. Load it the next time.
- **Quick fields for the essentials.** The field editor offers buttons to add Location, Meeting point and Topic in one click.

#### Federated Events
- **Share events with partners.** Events can now be shared with your partner stations.
- **Sign up at partner stations.** You can register for events your partner stations run.
- **Partner events in your list.** Events from partner stations show up on the upcoming events page.

#### Federated Wiki
- **Browse your partners' wiki.** Look through the files and folders your partner stations share.
- **Search across partners.** Search asks all your partner stations at the same time.
- **Focus on one partner.** A filter shows only what a single partner shares.

#### Public Calendar & Station View
- **A calendar for the public.** Show your events to visitors who have no account.
- **A public page for your station.** Visitors see your calendar and your wiki on one page, in tabs.
- **Choose what the public sees.** Each event field can be marked as public or internal.

#### Event Categories
- **Sort events into categories.** Create, edit, reorder and delete event categories.
- **Decide how much each category shows.** Set how many events each category shows on the overview.
- **Public categories for the calendar.** Mark a category as public to show its events on the public calendar.

#### Registrations
- **Registrations grouped by event.** Registrations are grouped per event, with the nearest deadline first.
- **A table for fair decisions.** See how often each member was accepted or turned down. It helps you decide fairly.
- **Limit the number of places.** Cap how many registrations an event accepts.
- **Managers hear about missed deadlines.** When a deadline passes with registrations still open, managers get a notification.

#### Inventory
- **A page for every item.** Each inventory item has its own page with its details, who holds it, its full history and the managers' notes.

#### Theming
- **More themes to choose from.** New themes for color blindness join a fiery new theme.
- **Rounded or square corners.** Pick the feel you like: rounded or square.
- **Themes at every level.** The instance, the station and each user pick a theme. Each level can lock its choice for the level below.

#### Problem Reports
- **Report a problem in one click.** A bug icon floats on every station page. Your report brings along the page, your roles and your recent requests.
- **Admins look after reports.** Admins can view, acknowledge and delete problem reports.

#### Admin Settings
- **Legal texts in your hands.** Edit the privacy policy, the terms of service, the consent text and the imprint.
- **Mail settings in the admin area.** Set up the outgoing mail server right in the admin area.

### Improvements

- **Lots of new help pages.** New help covers theming, sessions, notifications, modules, import, partner stations, comments, templates, notes, categories, legal texts and mail.
- **Guides for setting up feeds.** Step by step guides show how to add the calendar and news feeds in Thunderbird, Outlook, Android and iOS.
- **Each news post has its own page.** A news post opens on its own page, with the comments always in view.
- **Notifications take you there.** Clicking a notification opens the right page and marks it as read.
- **Fold away sidebar sections.** Sidebar headings can be clicked to collapse and expand their section.
- **Item names lead to the item.** In inventory tables, an item's name opens its page.
- **Settings in smaller pieces.** Admin and station settings are split into focused pages.
- **A nicer landing page.** The landing page got a fresh look.
- **Form answers are checked.** Answers are checked against each question's rules when they are sent. That covers options, selection limits, rating scales, rankings and agreement scales.
- **Absences for both kinds of managers.** Both event managers and attendance managers can see absences.

### Bug Fixes

- **Mentions are recognized again.** Some mentions were not picked up after sending. They are now recognized reliably.
- **Deleting a comment kept its replies.** Deleting a comment took all its replies with it. Now only the comment itself is hidden.
- **News authors hear only about replies.** The author of a news post was notified about every comment. Now only replies reach them.
- **Wiki share links point to the right place.** Shared wiki links led to the wrong address. They now open the right file.
- **Partner wiki files open properly.** A file from a partner's wiki tried to open a local file that did not exist. It now opens the partner's file.
- **Absences stay with managers.** The absences on an event's page were visible to everyone. Only managers see them now.
- **Past events leave the dashboard.** Registrations for past events still showed on the dashboard. They are gone now.
- **Dialogs no longer cause warnings.** Dialogs produced warnings in the browser. They are clean now.

---

### Technical Changes

#### Architecture
- **Events behind the scenes.** Changes in Ember now announce themselves inside the server. Nineteen separate handlers turn them into notifications.
- **Notifications follow real changes.** A notification is created only after a change has actually been saved.
- **Requests no longer send notifications.** Handling a request and notifying people are now kept apart.

#### Code Quality
- **Settings have a proper shape.** The settings of profile, event, attendance, form and waiting list fields are now well defined. Loose text is gone.
- **Kinds of things are fixed sets.** Field types, note and comment targets, table filters and content kinds now come from fixed lists instead of free text.
- **Questions are created from proper settings.** Creating a quiz question now takes well defined settings instead of raw data.
- **Each question checks its answers.** Every form question checks a submitted answer according to its type.
- **Selection limits are a fixed set.** A multiple choice limit is now none, at most, at least or exactly.
- **One list of question types.** A duplicate list of question types is gone.
- **An unused notification trigger is gone.** An old trigger for news comments was removed. The general comment trigger already covers it.

#### Frontend Components
- **New building blocks.** New pieces cover the scrolling tiles, the public event list, the change comparison, the theme picker and the note editor.
- **Links can point at a comment.** A link can highlight a single comment on the page.
- **Fast suggestions for mentions.** A lightweight server lookup feeds the member suggestions when you type `@`.

#### Infrastructure
- **Coverage goals are enforced.** The build checks that tests cover 95% of storage code, 90% of the logic and 80% of the request handling.
- **Every notification handler is tested.** All nineteen handlers have their own tests.
- **Tests run side by side.** The automated tests run in three parallel jobs.
- **Coverage counts across all jobs.** The coverage check adds up the results of the parallel jobs.
- **Documentation is checked.** The build checks the code documentation.
- **A broad set of new tests.** New tests cover attendance, sign-in, batch events, comments, consent, partner stations, fields, templates, feeds, forms, wiki, notes, profiles, quizzes, registrations, applications, protocols and settings.
- **Public flags in the database.** A database update adds what is needed to mark stations, categories, events, fields, boards, problem reports and feeds as public or tracked.

---

## v26.4.0

### New Features

#### Event Batch Import/Creation
- **Create many events at once.** A wizard walks you through planning, editing and confirming a whole series of events in one go.
- **Dates fill themselves in.** Tell Ember how many, how often and what kind of event, and it generates the dates for you.
- **Edit the series like a spreadsheet.** Before the events are created, you adjust them in a table, row by row.
- **Layouts for consistent events.** Save a set of fields as a layout and reuse it, for a series or for a single event.
- **A page for your layouts.** Create and edit event layouts and their fields on a page of their own.
- **Filter upcoming events.** A filter bar narrows upcoming events by category and more.
- **Events sorted by category.** The events overview groups events by their category.
- **Fair decisions on registrations.** A panel shows how often each member was accepted or turned down. It helps you share places fairly.

#### Federation System
- **Connect with other stations.** Partner up with other stations and share your wiki, quiz catalogs and test protocols.
- **Partnerships on your terms.** Start, pause, resume or end a partnership whenever you like.
- **Choose what goes each way.** For each partner, decide which kinds of content you send and which you receive.
- **Partners on other Ember servers.** Stations on separate Ember instances can partner too. Every message between them is signed.
- **Browse what partners share.** Look through the wiki files, quiz catalogs and protocols your partners share with you.
- **Copy shared content in one click.** Take a copy of a partner's content into your own station.
- **Browse even when a partner is offline.** Ember remembers what partners share, so you can still look around while their server is away.
- **Partners hear about changes right away.** Instances tell each other straight away when shared content changes.
- **Changes are picked up regularly.** Ember also checks partners for updates on a schedule, so nothing slips through.

#### Inventory Lending
- **Borrow from partner stations.** Ask a partner station to lend you inventory items for a chosen period.
- **Every loan has clear steps.** A request moves from requested to approved, lent, returned and finally closed.
- **Pick the exact items.** Assign specific items to an approved request.
- **Chat about a loan.** Both stations talk in a built-in chat, with system messages marking each step.
- **Keep items at home when needed.** Block whole inventories or single items for a period so they cannot be lent.
- **See what partners can lend.** Browse what partners have available, filtered by dates and searchable.
- **Know what is out.** Each inventory shows which items are currently lent out.
- **Block several things at once.** One block can cover several inventories and items, set up with simple tiles.

#### Federation Discovery
- **Let others find your station.** Choose whether your station can be found by nobody, by stations on your instance or by everyone.
- **A public list of stations.** The page `/discovery` lists findable stations to anyone, without signing in.
- **Pairing codes to get connected.** A short code carries everything another station needs to request a partnership.
- **Invite codes that just work.** A code created by a manager starts the partnership right away. Consent was already given.
- **Partnership requests to answer.** A pairing code from discovery creates a request. The other station accepts or declines it.
- **All requests in one place.** View incoming partnership requests and accept or decline them.

#### Public Wiki
- **Open your wiki to the public.** Each station picks a mode: off, everything public or nothing public.
- **Exceptions for single files and folders.** Any file or folder can differ from the station's mode.
- **Read the public wiki without an account.** Visitors can browse, read and search public wiki content without signing in.
- **Public files look right.** Formatted text is rendered, PDFs can be downloaded, images show and YouTube videos play.
- **Search the public wiki.** Search covers public content and shows a snippet of each match.

#### Unified Restrictions System
- **One system for restrictions.** Who may see what is now handled the same way everywhere.
- **Combine restrictions freely.** Mix roles, groups, tags and members, and choose whether all or any must match.
- **Higher roles include lower ones.** A manager has everything the team has, and the team everything a signed in member has.
- **Managers see everything in their area.** Management roles are not held back by restrictions in their own area.
- **Restrictions are checked quickly.** The database checks restrictions directly, which keeps lists fast.

#### Quiz AI Generation
- **Let AI write quiz questions.** Generate questions and wrong answers with the help of an AI provider.
- **Many questions at once.** Generate several questions per category, with an eye on what is already there.
- **Your own instructions.** Replace the default instructions for each batch you generate.
- **Generation runs in the background.** Long generations keep going while you wait, and the results appear when they are ready.

#### Quiz CSV Import
- **Import questions from CSV.** Bring questions from a CSV file into a quiz catalog.
- **Map the columns your way.** Decide which column fills which part of a question.
- **Choose your separators.** Set the separators for columns and for questions with several answers.

#### API Monitoring (Admin)
- **Every request is logged.** Each request is recorded with its kind, address, status code and duration.
- **See how fast Ember is.** A dashboard shows the slowest and fastest requests, hourly figures and status codes.
- **Look closely at one endpoint.** Open a single endpoint to see its response time chart and request history.
- **A log of problems.** Problems across the application are logged. You can filter and acknowledge them.

#### GDPR Export Improvements
- **Your data as a ZIP file.** The data export now downloads as a ZIP file instead of plain data.
- **A readable PDF summary.** The export includes a PDF with your account, your memberships and your inventory.
- **Your own files included.** Wiki files you created are part of the ZIP.

#### Station Export/Import
- **A moved station keeps its identity.** A station keeps its identifier when transferred, so partnership codes still work.
- **The wiki moves along.** A station transfer includes wiki folders, files, content and version history.
- **The logo moves along.** The station logo is part of the transfer.

### Improvements

#### Frontend Architecture
- **Over thirty new building blocks.** More than thirty new shared components cover tables, text, display, inputs and discovery.
- **Automatic checks for clean pages.** The build checks for raw markup, too many style classes, repeated patterns and oversized files.
- **Every page has help.** The build checks that each page has a help article.
- **Every icon is in place.** The build checks that all icons in use are registered.
- **Big pages in smaller pieces.** Large pages for attendance, inventory, members, quizzes and the wiki are split into focused parts.
- **An updated style guide.** The `/style` page shows all the shared components.

#### Wiki
- **Better editing of files and folders.** The edit dialogs now handle restrictions, tags and public visibility.

#### Attendance
- **A tidier attendance session.** The session page is split into a toolbar, a header, the member list, check mode, a summary and fields.
- **Rapid check mode.** Checking members in and out is now much faster.

#### Events
- **Export events your way.** A dialog lets you choose what event data to export.

#### Waiting List
- **The waiting list in sections.** The waiting list is split into overview, waiting, invited, testing and finished.

#### Theme & UI
- **The right theme from the start.** Dark and light mode now apply correctly on your first visit.
- **Readable charts in dark mode.** Chart labels were hard to read in dark mode. They now have the right colors.
- **Easier station switching.** Switching stations in the footer is easier.

#### Quiz
- **Fairer quiz scoring.** The points for quiz questions are calculated in a better way.
- **Tidier quiz editors.** The quiz settings editors and catalog pages were cleaned up.

#### Federation
- **More reliable change messages.** Messages between partners about changes are more reliable.
- **Cleaner handling of shared content.** Shared content and its changes are handled in a cleaner way.
- **Better errors between partners.** Problems talking to a partner are handled better.

#### Admin
- **More in station management.** Station management now includes partnerships, discovery and modules.
- **Clear image tags.** Releases are published as `latest`, and every change on `main` as `dev`.

### Security & Technical

- **Stations stay strictly apart.** Every read and write now checks that you belong to the station in question. Even with a valid session, you cannot reach another station's data.
- **Every lookup stays in its station.** Events, news, members, forms, inventory, wiki, attendance, groups, tags, the waiting list and partnerships are always read for one station only.
- **Partner messages are signed.** Every request between partners carries an RSA-2048 signature.
- **Station addresses cannot be guessed.** Stations are known to the outside by random identifiers, so nobody can count through them.
- **Role ranking is enforced in the database.** The database itself makes sure higher roles include lower ones.
- **Every station gets its own key.** A private key is created for each new station.

### Privacy Policy

- **The privacy policy describes the new export.** It now explains that your data comes as a ZIP with a PDF and your files, under Art. 15 and Art. 20 GDPR.

### Infrastructure

- **Dependencies stay up to date.** Updates arrive automatically after 14 days of settling in. Minor updates and patches are merged on their own.
- **Database updates for the new features.** Database updates add partnerships, unified restrictions, the role ranking, request logging and discovery settings.

### Bug Fixes

- **Long problem messages are shortened.** The admin problems page showed error messages in full. They are now cut to a readable length.
- **New field types work in events.** The event field editor and its inputs struggled with the new field types. They now handle them.
- **Batch events work with attendance.** Events created in a batch did not fit together with attendance. They now do.

---

## v1.2.0

### New Features

#### Test Protocols (Prüfungsprotokolle)

- **Test protocols for practical exams.** Grade practical exams such as the Jugendflamme with protocols of your own. A protocol has sections, subsections and checkboxes worth half a point or a full point.
- **Build your own protocols.** Create and edit protocols with their sections, subsections and items. Give each a name, a description and the score needed to pass.
- **Test runs for your members.** Start a run from a protocol and choose who takes it by group, role or name. A run stays open until you close it.
- **Grading made for tablets.** Grade step by step or jump to any section, with large checkboxes for your fingers. Every tick is saved at once, and the tabs show the score as it grows.
- **One tester per member at a time.** While someone grades a member, nobody else can. The same tester can come back, and the lock lifts when they leave.
- **See which sections are done.** Mark a section as tested and it gets a checkmark. Each member's progress shows at a glance, such as "5/7 sections".
- **An evaluation table at a glance.** Sections run down, members run across, with an average column and soft colors from green to red. The first three columns stay put while you scroll, and a filter shows who is not finished.
- **Protocols as PDF files.** Download the results ready to print or file away.
  - Each member gets a landscape PDF with your logo and station name, the checkboxes, the testers per section and the points neatly aligned.
  - The evaluation table comes as a landscape PDF too, in soft colors, with subsection rows, bold totals and your station's branding.
  - One ZIP file bundles all member PDFs together with the evaluation table.
- **Example data to try it out.** A Jugendflamme level 1 protocol comes ready with all seven sections, from emergency call to underground hydrant. It includes an open run for this year and a finished one from last year.
- **Two new roles for protocols.** One role manages protocols and runs, the other grades members. Managers have both.
- **Protocols as a module.** Each station can switch test protocols on or off.
- **Help for test protocols.** A help page explains how protocols are built, how grading works and how locking keeps testers apart.

## v1.1.0

### New Features

#### Wiki (Lernsammlung)

- **A real editor for the wiki.** Write with everything you would expect: bold, italic, underline, strikethrough and headings from H1 to H3. Lists, quotes, code blocks, tables, dividers, colored and highlighted text are all there.
- **The editor got tidier inside.** The editor's toolbar, dialogs and menus were split into small parts of their own. It works the same, and it is easier to keep in shape.
- **Images in your articles.** Upload an image or add one from a web address. Set its width right below the image.
- **Videos in your articles.** Paste a YouTube, Vimeo, PeerTube or Dailymotion link. Ember recognizes the site and embeds the video.
- **A friendly link dialog.** A floating panel lets you search wiki files by title and see their folder. You can edit the link text right there.
- **Link details on hover.** Hover over a link to see where it goes. Edit it, open it in a new tab or remove it from there.
- **Easy table editing.** A toolbar for adding and removing rows and columns appears when you work in a table. It stays in view in long documents.
- **Switch to plain text.** Flip between the formatted view and the plain Markdown text at any time.
- **Format right where you select.** Select text and a formatting toolbar appears next to it. You can close it without losing your selection.
- **Import Word documents.** Upload `.docx`, `.odt`, `.rtf` or `.html` files. They are turned into wiki articles automatically.
- **Search inside PDFs.** The text of uploaded PDFs is now included in search.
- **Smarter search.** Search finds words from their beginning, so "Notr" finds "Notruf". Matches are highlighted in yellow, in clean text snippets.
- **Point to related files.** Add "further reading" links between wiki files, right on the file's page.
- **More on a file's page.** See who last edited a file and when. The description is editable, and saving takes you out of edit mode.
- **Tags that suggest themselves.** Tags on files and folders are suggested as you type, whatever the capitalization.
- **Icons for your folders.** Upload your own icon for a folder. It shows in the grid and the list, and it now stays after saving.
- **See what changed between versions.** The version history marks additions in green and removals in red. It also shows who wrote each version.
- **A compact list view.** The file list is tighter, with simple dividing lines instead of cards.
- **Files are kept on disk.** PDFs, images and other files are stored in the data folder instead of the database.
- **Link entries open in a new tab.** A link entry in the wiki opens in a new tab instead of inside the page.
- **YouTube videos are searchable.** Ember picks up a video's title and author, so search can find it.
- **A showcase of formatting.** A demo file at the top of the wiki shows everything the editor can do.

#### Quiz System

- **Quizzes for your station.** Build catalogs and categories, manage questions, create tests and grade them.
- **Many kinds of questions.** Choose from multiple choice, fill in the blank, free answer, matching, image with text, true or false, ordering and lists.
- **AI helps write questions.** Generate questions with OpenAI, Anthropic Claude or Google Gemini. The AI remembers the conversation, so it does not repeat itself.
- **Import questions from CSV.** Upload a file, map its columns and check the result before importing. Answers are split per question, and the AI can suggest wrong answers.
- **Print a quiz as PDF.** The PDF has checkboxes, gaps to fill, word banks, section summaries and images. You decide where pages break.
- **Tests go from draft to closed.** A test is a draft, then active, then closed. Its questions are fixed when it goes live, and each student's attempts are counted.
- **Most answers grade themselves.** Multiple choice, true or false, matching, ordering and fill in the blank are graded on submit. Free answers and image questions are graded by hand.
- **Question settings are stored cleanly.** The settings of each question are kept in a structured form instead of loose text.

#### Waiting List

- **A waiting list for newcomers.** Collect sign-ups with your own form fields, invite codes and scoring formulas.
- **Every step is recorded.** An entry goes from waiting to invited, testing and finally joined or withdrawn. Ember notes when each step happened.
- **Invited means a member.** An invitation creates the member and puts them in the testing group.
- **Attendance for newcomers.** Members on trial join attendance sessions through their testing group. Their attendance is counted.
- **Families can help themselves.** A public page lets people sign up, confirm their interest and withdraw on their own with a personal link.
- **Interest is confirmed automatically.** Ember checks for overdue confirmations and sends reminders. After a grace period, the entry is withdrawn.
- **Change the sign-up date.** Managers can change when an entry joined the waiting list.
- **Emails for each step.** Templates in German and English confirm a sign-up, remind people to confirm and warn before removal.
- **Example data to try it out.** Example entries cover every step, attendance included.

#### Admin Settings

- **Platform settings in one place.** Switch station sign-up on or off and set sign-in and session options. Configure outgoing mail and edit the legal texts with their versions.
- **Release notes inside Ember.** Click the version in the footer to read the latest release notes, nicely formatted.

### UI & Component Improvements

- **One kind of toggle for choices.** Picking roles, groups and tags now uses the same toggle buttons on every page.
- **One kind of menu entry.** Menu entries look and behave the same everywhere, starting with the wiki.
- **Formatted text looks right.** Formatted text had no styling at all. Headings, lists, quotes, tables, code, images, embeds and dividers now look as they should.
- **Borders are visible again.** Borders were invisible throughout the app. They now show in both light and dark mode.
- **Search matches stand out.** Matching words in search results are highlighted in yellow.
- **The Ember logo, everywhere.** The logo with its little blink appears on the landing page, in the sidebar, in the help center and on error pages.
- **Pick your theme colors.** A new picker lets you choose theme colors.
- **Friendly not found pages.** Pages that do not exist show a proper, branded page.
- **Write scoring formulas easily.** A formula field helps you write the scoring for the waiting list.
- **The style guide grows.** The `/style` page shows the new toggle buttons and menu entries.
- **More help.** New help pages cover the wiki editor and the admin settings. The existing pages were updated.

### Infrastructure

- **Legal texts are there from the start.** Ember brings templates for its legal texts. On first start, it copies any missing ones into the data folder.
- **Leaner container images.** Data, build leftovers and editor files are kept out of the container image.
- **WebP images work.** Ember now reads WebP images. Formats it cannot read are handled gracefully.
- **Pandoc converts documents.** Set `PANDOC_BIN` to point at Pandoc for document conversion. It defaults to `pandoc`.
- **Strikethrough in formatted text.** Struck through text is now shown as such.
- **Sensitive requests stay out of the logs.** Sign-in, AI and configuration requests are left out of request logging.
- **Shared tools behind the scenes.** CSV reading, document conversion, text comparison and PDF creation each live in one shared place.
- **New tests.** Tests now cover formatted text, the quiz PDF, the waiting list and the score calculation.

### Bug Fixes

- **Version colors show correctly.** The colors in the version comparison did not work. They show properly now.
- **Formatted text had no styling.** The styling meant for formatted text did nothing. Ember now brings its own.
- **Switching to plain text crashed.** Toggling the plain text view could crash the editor. It now switches smoothly.
- **Links opened inside the editor.** Clicking a link while editing opened it in the editor. That no longer happens.
- **Dividers were invisible.** Horizontal dividers did not show. They now appear as a soft line.
- **Heading buttons did nothing.** In lists and quotes, the heading buttons had no effect. They now work everywhere.
- **The paragraph button did nothing.** Pressing it had no effect. It now turns text back into a plain paragraph.
- **Images went missing in the editor.** Some images did not show while editing. They now appear.
- **WebP images could not be uploaded.** Uploading a WebP image failed. Formats that cannot be read are now handled gracefully.
- **Folder icons did not stick.** A newly uploaded folder icon did not show. It is now saved and shown.
- **The table toolbar did not appear.** Working in a table did not bring up its toolbar. It appears now.
- **Formatting menus got in each other's way.** Two menus competed for the same spot. They are now one menu.
- **Search snippets showed odd fragments.** Snippets showed word stems instead of real text. They now show the actual text.
- **Demo mode allowed new stations.** In demo mode, anyone could register a station. That is now switched off.

### Dependencies Added

- **A new editor foundation.** The wiki editor is built on a new editor toolkit with tables, highlights, YouTube, images, colors, underline, links and placeholders.
- **Web pages to Markdown.** A new helper turns web content into Markdown.
- **Markdown to web pages.** A new helper turns Markdown into web content.
- **Text comparison in the browser.** A new helper compares texts for the version history.
- **Reading text from PDFs.** A new helper reads the text inside PDF files.
- **Reading CSV files.** A new helper reads CSV files.
- **Reading WebP images.** A new helper lets Ember read WebP images.
- **Talking to AI providers.** New connections reach OpenAI, Anthropic and Google for generating questions.
- **Text comparison on the server.** A new helper creates the change sets between versions.
