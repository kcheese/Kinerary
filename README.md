# Kinerary

A dark-themed Android app for following a trip itinerary. The itinerary itself lives in a Google Sheet, which stays the source of truth.

- **Itinerary**: a calendar of the trip with each day coloured by location. Pick a day, or swipe between days, to see what's on. You can also view the whole trip as one list.
- **Index**: a tab of notes for each place, such as hotels, opening hours and links, with tables.
- **Prep & Packing**: the same kind of notes, plus checklists.

## The sheet

Share the sheet as "Anyone with the link can view", then paste its link in Settings. The app downloads the sheet as `.xlsx` and keeps a copy for offline use.

The first sheet, or the one with "itinerary" in its name, is laid out down column A:

| Row | What it is |
| --- | --- |
| A coloured cell on its own, just before a date | A location (e.g. *Tokyo*); its colour is the location's starting colour |
| A date, with "Day N" beside it | A new day |
| Anything else | An activity for that day. Text in other columns shows as a tag (e.g. *Book*), and "- See Index" links to the location's Index page |

The other tabs are copied in as starting notes the first time the sheet loads. Tabs named "… Index" go to Index and the rest go to Prep. Coloured cells become headings and table headers, and tick boxes become checklists.

## Sync

Notes, checklists, location colours and the sheet link sync through the [K-Reader](https://github.com/kcheese/K-Reader) sync server (`POST /kinerary/sync`, using the same accounts). Each page keeps whichever version was saved last.

## Building

Needs JDK 21 and the Android SDK (compileSdk 37.2).

`local.properties` (not in git):

```
sdk.dir=...
kinerary.sync-server=https://your-sync-server
```

A release also needs `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Every release must be signed with the same key so updates install in place.

```
./gradlew testDebugUnitTest assembleRelease
```

The app updates itself from this repo's GitHub releases. Each release is tagged `v<versionName>` and has the APK attached.

Launcher icons are generated from `art/icon.png` by running `python tools/make_icons.py` (needs Pillow).
