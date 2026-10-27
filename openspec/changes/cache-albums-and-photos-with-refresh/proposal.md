# Proposal

## Why

Currently, the app performs a full MediaStore scan every time it starts, which can be slow on devices with many photos (several seconds to load). The app only keeps albums in memory, so every launch requires a complete re-scan of all device photos. Users experience a noticeable delay before they can browse their albums, especially on devices with thousands of photos.

## What Changes

- **Persist album and photo metadata to local storage**: Store album structure (id, name, photo count) and photo existence (id, URI, path, display name, favorite status, date added) to a persistent cache on first scan
- **Load from cache on startup**: Display cached albums immediately when the app opens, providing instant UI response
- **Background refresh**: After showing cached data, perform a background MediaStore scan to update the cache with any new, modified, or deleted photos
- **Regular refreshes**: Automatically refresh the cache when the app resumes or on a periodic basis (e.g., every 15 minutes while the app is active)
- **Cache invalidation**: Detect when photos are added, deleted, or modified externally and update the cache accordingly
- **Graceful degradation**: If cache is unavailable or corrupted, fall back to direct MediaStore query

## Capabilities

### New Capabilities
- `album-cache/persistent-storage`: Persistent caching of album and photo metadata with automatic refresh

### Modified Capabilities
- N/A

## Impact

- **Affects**: `DeviceAlbumViewModel.kt`, `DeviceAlbumsRetriever.kt`, `MainActivity.kt`, new `AlbumCacheRepository.kt` (or similar)
- **Dependencies**: Add Room database or extend DataStore usage for persistent storage
- **Performance**: Significantly faster app startup (instant display of cached albums vs. seconds of scanning)
- **Data**: New persistent storage layer for album/photo metadata
- **UI**: May show a subtle loading indicator during background refresh
- **No breaking changes** to existing functionality
