# Spec Delta

## Purpose

Provides persistent caching of album and photo metadata to enable fast app startup and reduce repeated MediaStore queries, with automatic background refresh to keep data current.

## ADDED Requirements

### Requirement: Persist album and photo metadata
The system SHALL persist album metadata (album ID, name, photo count) and photo metadata (photo ID, URI, file path, display name, favorite status, date added) to local persistent storage after each MediaStore scan.

#### Scenario: First scan persists data
- **WHEN** the app performs its first MediaStore scan and no cache exists
- **THEN** the system stores all albums and photos to persistent storage

#### Scenario: Subsequent scan updates cache
- **WHEN** the app performs a MediaStore scan and a cache already exists
- **THEN** the system updates the persistent storage with the latest data

### Requirement: Load cached albums on startup
The system SHALL load albums from persistent cache immediately on app startup, before performing any MediaStore queries.

#### Scenario: App starts with existing cache
- **WHEN** the app starts and a valid cache exists
- **THEN** the system displays cached albums immediately without waiting for MediaStore scan

#### Scenario: App starts without cache
- **WHEN** the app starts and no cache exists (first launch or cache cleared)
- **THEN** the system performs a MediaStore scan and displays albums when scan completes

### Requirement: Background refresh after cache load
The system SHALL perform a background MediaStore scan after loading cached albums to update the cache with any changes (new photos, deleted photos, modified favorites).

#### Scenario: Background refresh updates cache
- **WHEN** cached albums are displayed and a background scan completes
- **THEN** the system updates the cache and UI with any new or modified photo data

#### Scenario: Background refresh detects new photos
- **WHEN** new photos are added to the device while the app is running
- **THEN** the background refresh detects and displays the new photos

#### Scenario: Background refresh detects deleted photos
- **WHEN** photos are deleted from the device while the app is running
- **THEN** the background refresh removes the deleted photos from the cache and UI

### Requirement: Periodic refresh while app is active
The system SHALL refresh the album cache at regular intervals (every 15 minutes) while the app is in the foreground.

#### Scenario: Periodic refresh updates data
- **WHEN** the app has been in the foreground for 15 minutes since the last refresh
- **THEN** the system performs a background MediaStore scan to refresh the cache

#### Scenario: Refresh on app resume
- **WHEN** the app resumes from background after being inactive
- **THEN** the system performs a background refresh if the last refresh was more than 15 minutes ago

### Requirement: Cache validity and corruption handling
The system SHALL handle cache corruption gracefully by falling back to direct MediaStore queries and rebuilding the cache.

#### Scenario: Corrupted cache detected
- **WHEN** the system attempts to load cached data and the cache is corrupted or unreadable
- **THEN** the system falls back to MediaStore query and rebuilds the cache

#### Scenario: Cache version mismatch
- **WHEN** the app is updated and the cache format has changed
- **THEN** the system invalidates the old cache and performs a fresh scan

### Requirement: Photo existence validation
The system SHALL verify that cached photos still exist on the device during refresh, removing any photos that no longer exist.

#### Scenario: Cached photo no longer exists
- **WHEN** a photo in the cache no longer exists in MediaStore during refresh
- **THEN** the system removes the photo from the cache and UI

#### Scenario: Cached photo still exists
- **WHEN** a photo in the cache still exists in MediaStore during refresh
- **THEN** the system retains the photo in the cache and updates its metadata if changed
