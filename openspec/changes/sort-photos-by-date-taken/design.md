# Design

## Context

Album photos are loaded by `DeviceAlbumsRetriever` from MediaStore and persisted in a Room cache (`AlbumDatabase` v1, cache version `"1"`) via `AlbumCacheRepository`. The album detail screen (`AlbumDetailViewModel`) currently sorts by `DevicePhoto.additionDate` (MediaStore `DATE_ADDED`) or display name; the sort order is not user-selectable in the UI. EXIF data is only read on demand in the photo viewer bottom sheet (`ExifUtils`), never cached. See proposal.md for motivation.

Android's media scanner already derives `MediaStore.Images.Media.DATE_TAKEN` (millis since epoch) from each photo's EXIF `DateTimeOriginal` when present, falling back to the file modification time. `DATE_TAKEN` is a standard column available since API 1 (minSdk is 23), so no new permission or dependency is needed.

## Goals / Non-Goals

**Goals:**
- Read `DATE_TAKEN` during the MediaStore scan and carry it on `DevicePhoto`
- Persist it in the Room cache (`PhotoEntity.dateTaken`)
- Always display album photos newest-taken-first, falling back to date added per photo
- Keep the change small: no EXIF parsing, no EXIF caching, no user-facing sort setting

**Non-Goals:**
- Parsing EXIF ourselves for sorting (per-file I/O on thousands of photos would reintroduce the slowness the album cache removed)
- Caching any EXIF data (the photo viewer keeps reading EXIF on demand, unchanged)
- A user-facing sort-order picker (order is hardcoded)
- Caching image thumbnails or changing refresh behavior

## Decisions

### Source of the capture date: MediaStore `DATE_TAKEN`, not EXIF parsing
**Decision**: Add `MediaStore.Images.Media.DATE_TAKEN` to the retriever's query projection and map it to a new `DevicePhoto.dateTaken: Date?` field. Treat `0`/missing as null.

**Rationale**:
- The media scanner already did the EXIF work; reading the column is free at scan time
- Parsing EXIF per photo for sorting would mean one file open + `ExifInterface` parse per photo on the IO thread — exactly the cost the album cache exists to avoid
- `DATE_TAKEN` already implements the desired fallback chain (EXIF `DateTimeOriginal` -> file mtime), with our own `dateAdded` fallback layered on top in the sort key

**Alternatives considered**:
- **Parse EXIF with `ExifUtils` for every photo at scan time**: rejected — too slow without a persistent EXIF cache, and the user explicitly ruled out EXIF caching
- **Parse EXIF lazily per photo and merge into the sort**: rejected — photos not yet opened individually would sort by fallback date, producing an inconsistent order

### Data model and persistence
**Decision**: Add `dateTaken: Date?` to `DevicePhoto`, and a nullable `dateTaken: Long` (epoch millis) column to `PhotoEntity`. Map both ways in `CacheMapper`. Bump Room `version` 1 -> 2 with an explicit `Migration(1, 2)` that runs `ALTER TABLE photos ADD COLUMN dateTaken INTEGER DEFAULT NULL`, and bump `CURRENT_CACHE_VERSION` `"1"` -> `"2"`.

**Rationale**:
- Nullable column keeps the migration trivial and backward compatible; old cache rows simply have null `dateTaken`
- The cache-version bump makes `AlbumCacheRepository.loadCachedAlbums()` clear the stale cache and trigger a fresh MediaStore scan, so `dateTaken` is repopulated immediately on first launch after upgrade
- Storing epoch millis as `Long` makes the value directly comparable/sortable and avoids `Date` serialization in Room

**Alternatives considered**:
- **`fallbackToDestructiveMigration()` instead of an explicit migration**: works (cache is disposable), but the explicit `ALTER TABLE` is one line and preserves the existing cache rows; combined with the cache-version bump the net effect is the same freshness guarantee
- **Storing `dateTaken` only in memory, not in Room**: rejected — the whole point of the cache is that sorting must work on cached data before/without a rescan

### Sort implementation: hardcoded in the ViewModel
**Decision**: In `AlbumDetailViewModel.updateDisplayPhotos()`, remove the `SortOrder` switching and always sort by `((photo as? DevicePhoto)?.dateTaken ?: photo.additionDate)?.time ?: 0L` descending. Delete the now-unused `SortOrder` enum and `setSortOrder`/`getCurrentSortOrder` (no fragment calls them).

**Rationale**:
- One place computes the display order, applied identically to cache-loaded and freshly scanned photos
- The effective key `dateTaken ?: additionDate` implements the required fallback per photo
- The enum has no UI consumers today, so removing it is a clean minimal diff; keeping dead sort modes invites confusion

**Alternatives considered**:
- **Order in SQL (`ORDER BY COALESCE(dateTaken, dateAdded) DESC` in `PhotoDao`)**: viable, but the ViewModel re-sorts in memory anyway for filtering, so sorting in SQL alone would not cover all paths; keeping the sort in the ViewModel covers every source of photos
- **Keep the enum, default to date-descending**: leaves dead code and a misleading API; the user asked for a hardcoded order

### Incidental fix: `DATE_ADDED` is seconds, `Date` expects millis
**Decision**: Parse `DATE_ADDED` as seconds (`Date(seconds * 1000L)`) in `DeviceAlbumsRetriever`.

**Rationale**: `MediaStore` `DATE_ADDED`/`DATE_MODIFIED` are seconds since epoch while `DATE_TAKEN` is millis; the current `Date(dateAdded.toLong())` misplaces addition dates ~1000x in the past. The fallback sort key must be correct, so this is in scope. Relative ordering was previously preserved (monotonic transform), so this changes absolute values, not the old order.

### Preserve `dateTaken` when rebuilding photo objects
**Decision**: `DeviceAlbumViewModel.updatePhotoFavoriteStatus()` constructs new `DevicePhoto` instances; it must copy `dateTaken` from the old instance.

**Rationale**: otherwise toggling a favorite would silently drop the capture date from the in-memory and re-cached photo.

## Risks / Trade-offs

- **`DATE_TAKEN` may be 0 or stale for very recently added photos** (media scanner may not have run yet) -> treated as null and ordered by `dateAdded`; it self-heals on the next scan/refresh.
- **Reliance on the media scanner's EXIF extraction** -> acceptable: this is the platform-standard source of capture time and matches what gallery apps display; EXIF parsing ourselves was ruled out for performance.
- **Room schema change on existing installs** -> explicit migration 1->2 plus cache-version bump; if anything fails, `AlbumCacheRepository` already falls back to a direct MediaStore query and rebuilds the cache.
- **Destructive risk of removing the `SortOrder` enum** -> low: no UI code references it (verified by search); if a future change needs user-selectable sorting, the enum can be reintroduced.

## Migration Plan

1. Ship code with Room version 2 + `Migration(1, 2)` (`ALTER TABLE photos ADD COLUMN dateTaken INTEGER DEFAULT NULL`) and `CURRENT_CACHE_VERSION = "2"`.
2. On first launch after upgrade, `loadCachedAlbums()` detects the version mismatch, clears the cache, and the next MediaStore scan repopulates albums/photos including `dateTaken`.
3. Rollback: revert the code change; the old app version reads the v2 database only if it tolerates the extra column — since the old code has no migration path from 2 to 1, rollback requires clearing app data or shipping a 2->1 migration; acceptable because the cache is fully rebuildable from MediaStore.

## Open Questions

- None blocking. (Whether to later re-expose a user-facing sort menu is deliberately deferred.)
