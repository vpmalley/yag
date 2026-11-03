# Tasks

## 1. Photo model and MediaStore retrieval

- [x] 1.1 Add `dateTaken: Date?` field to `DevicePhoto` (device/model/DevicePhoto.kt) and verify the project compiles (`./gradlew compileDebugKotlin`)
- [x] 1.2 In `DeviceAlbumsRetriever`, add `MediaStore.Images.Media.DATE_TAKEN` to the query projection and map it to `DevicePhoto.dateTaken` (millis since epoch; treat `0`/missing as null), and verify with a debug log or unit check that a known photo carries its capture date
- [x] 1.3 Fix `DATE_ADDED` parsing in `DeviceAlbumsRetriever` from `Date(dateAdded.toLong())` to `Date(dateAdded.toLong() * 1000L)` (column is seconds, `Date` expects millis) and verify `additionDate` now reflects the real date

## 2. Persistence in the Room cache

- [x] 2.1 Add nullable `dateTaken: Long` (epoch millis) column to `PhotoEntity` and verify it compiles
- [x] 2.2 In `AlbumDatabase`, bump `version` to 2, register a `Migration(1, 2)` running `ALTER TABLE photos ADD COLUMN dateTaken INTEGER DEFAULT NULL`, and bump `CURRENT_CACHE_VERSION` to `"2"`; verify the database builds and a fresh install plus an upgrade from v1 both initialize without crash
- [x] 2.3 Update `CacheMapper` so `DevicePhoto.toEntity` writes `dateTaken` and `PhotoEntity.toDevicePhoto` restores it, and verify a save/load round trip preserves the capture date

## 3. Hardcoded newest-first sorting

- [x] 3.1 In `AlbumDetailViewModel.updateDisplayPhotos()`, replace the `SortOrder` switching with a fixed sort by `(dateTaken ?: additionDate)?.time ?: 0L` descending, and remove the now-unused `SortOrder` enum, `setSortOrder`, and `getCurrentSortOrder`; verify the project compiles and the album grid shows the most recently taken photo first
- [x] 3.2 Update `DeviceAlbumViewModel.updatePhotoFavoriteStatus()` to copy `dateTaken` when rebuilding a `DevicePhoto`, and verify toggling a favorite does not drop the capture date from the in-memory list or the re-saved cache

## 4. Verification

- [x] 4.1 Run `./gradlew assembleDebug` (and existing unit tests `./gradlew testDebugUnitTest`) and verify the build and tests pass
- [ ] 4.2 Manually verify on a device/emulator: album grid is ordered newest-taken-first; a photo with `DATE_TAKEN = 0` (e.g., a screenshot) is ordered by its date added in the correct position; after killing and restarting the app the order is identical from cache; no EXIF data is stored anywhere new
