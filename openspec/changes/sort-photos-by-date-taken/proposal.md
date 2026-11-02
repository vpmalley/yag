# Proposal

## Why

Photos in an album are currently ordered by file name (default) or by `MediaStore DATE_ADDED` (when the photo was copied onto the device), neither of which reflects when the photo was actually captured. Users expect the most recently taken photos first. The capture time is already available from the system: `MediaStore.Images.Media.DATE_TAKEN` is populated by Android's media scanner from the photo's EXIF `DateTimeOriginal` when present, falling back to the file's modification time. The app simply does not read it yet.

## What Changes

- `DeviceAlbumsRetriever` additionally queries `MediaStore.Images.Media.DATE_TAKEN` (millis since epoch) and stores it on each `DevicePhoto` as `dateTaken: Date?`. A value of `0`/missing is treated as "unknown" (null).
- `DevicePhoto` gains a `dateTaken: Date?` field; `PhotoEntity` gains a nullable `dateTaken: Long` column so the value is persisted in the Room cache (database version 1 -> 2, cache version `"1"` -> `"2"`).
- The album detail view always sorts photos by capture date, newest first: effective sort key is `dateTaken ?: additionDate` (fallback to the date the photo was added to the device when no capture date is available), descending. The sort order is hardcoded; the user has no sort choice (the `SortOrder` switching in `AlbumDetailViewModel` is removed/deactivated).
- No EXIF data is parsed or cached for this feature: sorting relies solely on the MediaStore column. On-demand EXIF reading in the photo viewer bottom sheet is unchanged.
- Incidental fix: `DATE_ADDED` is expressed in seconds while `Date()` expects millis; the retriever currently passes seconds directly, so `additionDate` values are 1000x too small. It is parsed as seconds (`* 1000`) so the fallback date is correct.

## Capabilities

### New Capabilities
- `album-sorting`: Photos carry a capture date (`DATE_TAKEN` from MediaStore, persisted in the album cache) and the album detail view displays them sorted by that date, newest first, falling back to the date added when no capture date exists. Sort order is fixed; there is no user-facing sort setting.

### Modified Capabilities
- N/A

## Impact

- Affects: `DeviceAlbumsRetriever.kt`, `DevicePhoto.kt`, `PhotoEntity.kt`, `PhotoDao.kt` (optional ordering query), `CacheMapper.kt`, `AlbumDatabase.kt` (schema version + cache version), `AlbumDetailViewModel.kt` (hardcoded sort), `DeviceAlbumViewModel.kt` (preserve `dateTaken` when rebuilding `DevicePhoto` on favorite change).
- Data: Room migration to version 2 adds the nullable `dateTaken` column to `photos`; cache version bump invalidates the metadata cache so `dateTaken` is repopulated on next MediaStore scan.
- UI: Album grid order changes to capture-date descending. No new UI controls.
- No new dependencies; `DATE_TAKEN` is a standard MediaStore column (available since API 1, minSdk 23).
- No breaking changes to existing functionality.
