# Tasks

## 1. Setup Room Database

- [ ] 1.1 Add Room dependencies (room-runtime, room-ktx, room-compiler) to app/build.gradle and verify Gradle sync succeeds
- [ ] 1.2 Create AlbumEntity data class with fields: id, name, photoCount, lastUpdated and verify it compiles
- [ ] 1.3 Create PhotoEntity data class with fields: id, albumId, uri, path, displayName, isFavorite, dateAdded and verify it compiles
- [ ] 1.4 Create AlbumDao interface with methods: getAllAlbums(), getAlbumWithPhotos(albumId), insertAlbums(), insertPhotos(), deleteAll(), and verify it compiles
- [ ] 1.5 Create PhotoDao interface with methods: getPhotosByAlbum(albumId), insertPhotos(), deletePhotosByAlbum(albumId), deleteAll(), and verify it compiles
- [ ] 1.6 Create AlbumDatabase abstract class extending RoomDatabase with version 1 and verify database builds

## 2. Create Cache Repository Layer

- [ ] 2.1 Create AlbumCacheRepository class that wraps AlbumDatabase and provides methods: loadCachedAlbums(), saveAlbumsToCache(), clearCache() and verify it compiles
- [ ] 2.2 Add mapping functions to convert between Room entities (AlbumEntity, PhotoEntity) and domain models (Album, DevicePhoto) and verify conversions work correctly
- [ ] 2.3 Add error handling for cache operations (corrupted data, IO errors) with fallback to null/empty and verify error cases handled

## 3. Update DeviceAlbumViewModel for Cache-First Loading

- [ ] 3.1 Add AlbumCacheRepository dependency to DeviceAlbumViewModel constructor and verify injection works
- [ ] 3.2 Modify loadAlbums() to first load from cache, then trigger background refresh and verify cached albums display immediately on app start
- [ ] 3.3 Add _isRefreshing LiveData to track background refresh state and verify it updates correctly
- [ ] 3.4 Update fetchDeviceAlbums() to save results to cache after MediaStore scan and verify cache is updated

## 4. Implement Background Refresh

- [ ] 4.1 Add refreshAlbumsFromMediaStore() method that performs scan and updates cache without blocking UI and verify it runs on IO dispatcher
- [ ] 4.2 Add periodic refresh using coroutine with 15-minute interval while app is active and verify it starts/stops correctly
- [ ] 4.3 Add refresh on app resume logic (check last refresh time, refresh if >15 min) and verify it triggers correctly
- [ ] 4.4 Cancel periodic refresh when app goes to background and verify no refresh runs in background

## 5. Update MainActivity Integration

- [ ] 5.1 Update MainActivity to initialize AlbumDatabase and inject AlbumCacheRepository into DeviceAlbumViewModel and verify no crashes on startup
- [ ] 5.2 Replace direct fetchDeviceAlbums() call with loadAlbums() (cache-first) and verify app starts faster with cached data
- [ ] 5.3 Add lifecycle observer to trigger refresh on app resume and verify refresh happens when returning to app

## 6. Handle Cache Edge Cases

- [ ] 6.1 Add cache version tracking in database and implement version mismatch detection with cache invalidation and verify old cache is cleared on version change
- [ ] 6.2 Add try-catch around cache load with fallback to MediaStore query if cache fails and verify app works with corrupted cache
- [ ] 6.3 Add photo existence validation during refresh (check if cached photos still exist in MediaStore) and verify deleted photos are removed

## 7. Testing

- [ ] 7.1 Test first launch (no cache) - verify MediaStore scan runs and cache is created
- [ ] 7.2 Test second launch (with cache) - verify albums display instantly from cache, then background refresh runs
- [ ] 7.3 Test adding new photos to device - verify background refresh detects and displays new photos
- [ ] 7.4 Test deleting photos from device - verify background refresh removes deleted photos from UI
- [ ] 7.5 Test favoriting/unfavoriting photos - verify cache updates and UI reflects changes
- [ ] 7.6 Test cache corruption (manually corrupt database) - verify app falls back to MediaStore query and rebuilds cache
- [ ] 7.7 Test app resume after 20 minutes - verify refresh triggers automatically
- [ ] 7.8 Test periodic refresh - verify refresh runs every 15 minutes while app is active
- [ ] 7.9 Test app backgrounding - verify periodic refresh stops and resumes correctly
- [ ] 7.10 Verify startup time improvement - measure time from app start to albums displayed (should be <500ms with cache vs >2s without)
