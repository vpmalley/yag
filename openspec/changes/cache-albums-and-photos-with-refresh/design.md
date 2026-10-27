# Design

## Context

Currently, the app performs a full MediaStore scan on every launch via `DeviceAlbumsRetriever.getLocalAlbums()`, which queries all device photos. This takes several seconds on devices with many photos. The `DeviceAlbumViewModel` keeps albums only in memory (`_albums` LiveData), so there's no persistence between app sessions. The app already uses DataStore for settings persistence.

See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Reduce app startup time from seconds to milliseconds by loading cached data first
- Keep album/photo data fresh with background refreshes
- Handle cache corruption gracefully with automatic fallback
- Maintain data consistency between cache and MediaStore

**Non-Goals:**
- Caching actual image thumbnails (only metadata is cached)
- Offline-first architecture (MediaStore is always the source of truth)
- Cross-device sync
- Caching EXIF data (only basic photo metadata)

## Decisions

### Storage Technology
**Decision**: Use Room Database (SQLite) for persistent caching.

**Rationale**: 
- Room is the standard Android solution for structured data persistence
- Better suited for relational data (albums → photos) than DataStore
- Supports efficient queries, updates, and migrations
- Type-safe with Kotlin
- Already a common Android dependency

**Alternatives considered**:
- **DataStore with JSON serialization**: Simpler but less efficient for large datasets, no query capabilities, harder to update individual records
- **SharedPreferences with JSON**: Not suitable for large datasets, no structured queries
- **Custom file-based storage**: Too much boilerplate, error-prone

### Cache-First Loading Strategy
**Decision**: Load from cache first, then refresh in background (Cache-Aside pattern with background refresh).

**Rationale**:
- Provides instant UI response (milliseconds vs seconds)
- Users see their albums immediately
- Background refresh ensures data freshness without blocking UI

**Alternatives considered**:
- **Refresh-first**: Would maintain startup delay, defeating the purpose
- **Stale-while-revalidate**: Similar to cache-first but more complex

### Refresh Triggers
**Decision**: Refresh on app startup (background), app resume (if >15 min since last refresh), and periodic (every 15 minutes while active).

**Rationale**:
- Startup refresh: Ensures data is current after app updates or device changes
- Resume refresh: Captures changes made while app was in background
- Periodic refresh: Catches changes during long app sessions
- 15-minute interval balances freshness with battery/performance impact

**Alternatives considered**:
- **ContentObserver on MediaStore**: More immediate but higher battery/performance cost, complex implementation
- **Manual refresh only**: Requires user action, less convenient
- **Shorter interval (5 min)**: More fresh but more battery usage
- **Longer interval (1 hour)**: Less battery but staler data

### Data Model
**Decision**: Two Room entities - `AlbumEntity` and `PhotoEntity` with a one-to-many relationship.

**Rationale**:
- Mirrors the existing domain model (`Album` and `DevicePhoto`)
- Efficient queries (load album with all photos in one query)
- Easy to convert between Room entities and domain models

**Alternatives considered**:
- **Single denormalized table**: Simpler but redundant data, harder to update
- **JSON blob per album**: Less queryable, harder to update individual photos

### Cache Invalidation
**Decision**: Full cache replacement on each refresh (delete all, insert new), with version tracking.

**Rationale**:
- Simpler than incremental updates (no need to diff old vs new)
- MediaStore scans are already full scans
- Avoids complex merge logic
- Version field allows detecting schema changes

**Alternatives considered**:
- **Incremental updates (diff and patch)**: More complex, error-prone, minimal benefit since scans are already full
- **Timestamp-based invalidation**: Less reliable than full replacement

### Error Handling
**Decision**: Graceful degradation - if cache fails, fall back to direct MediaStore query.

**Rationale**:
- Ensures app always works, even with corrupted cache
- Cache is an optimization, not a requirement
- Better user experience than crashing

**Alternatives considered**:
- **Fail fast**: Would break app if cache is corrupted
- **Silent fallback**: Same as graceful degradation but without user notification

## Risks / Trade-offs

**[Risk] Cache becomes stale if background refresh fails** → Mitigation: Show subtle indicator when data might be stale; always allow manual refresh

**[Risk] Large cache size on devices with many photos** → Mitigation: Only store metadata (not images); estimate ~1KB per photo, 10,000 photos = ~10MB, acceptable

**[Risk] Room database migration complexity** → Mitigation: Start with version 1, use Room's auto-migration for simple changes, destructive migration as fallback

**[Risk] Battery impact from frequent MediaStore scans** → Mitigation: 15-minute interval balances freshness; scans are on IO dispatcher; stop periodic refresh when app is backgrounded

**[Risk] Race conditions between cache load and refresh** → Mitigation: Use single source of truth pattern; LiveData ensures UI updates on main thread; cancel previous refresh job if new one starts

**[Risk] Cache corruption due to app crash during write** → Mitigation: Room transactions ensure atomic writes; automatic recovery on next launch

## Migration Plan

1. Add Room dependencies to build.gradle
2. Create Room entities, DAOs, and database class
3. Create `AlbumCacheRepository` to abstract cache operations
4. Update `DeviceAlbumViewModel` to use cache-first loading
5. Add background refresh logic with WorkManager or coroutines
6. Test with various scenarios (first launch, app update, cache corruption)

**Rollback**: Remove Room dependencies and revert to direct MediaStore queries; cache is optional optimization
