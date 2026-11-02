# Spec Delta

## Purpose

Photos in an album are ordered by the moment they were captured, so the most recently taken photo appears first. The capture date comes from the system media store (`DATE_TAKEN`, populated from the photo's EXIF data by Android's media scanner), is persisted alongside the other photo metadata, and is used as the fixed sort order of the album view.

## ADDED Requirements

### Requirement: Capture date is read from the media store
The system SHALL read the capture date (`DATE_TAKEN`, in milliseconds since epoch) for every photo during a MediaStore scan and attach it to the photo. A value of `0` or a missing column SHALL be treated as "no capture date available".

#### Scenario: Photo with capture date
- **WHEN** a photo has a `DATE_TAKEN` value greater than 0 in the MediaStore
- **THEN** the photo carries that timestamp as its capture date

#### Scenario: Photo without capture date
- **WHEN** a photo has `DATE_TAKEN` equal to 0 or the value is missing
- **THEN** the photo's capture date is treated as unknown (null)

### Requirement: Capture date is persisted in the local cache
The system SHALL persist the capture date of each photo in the local album cache so it survives app restarts without re-scanning. Existing cache entries without a capture date SHALL be migrated gracefully (null column) and repopulated on the next MediaStore scan.

#### Scenario: Cache stores capture date
- **WHEN** albums are saved to the local cache after a MediaStore scan
- **THEN** each cached photo row includes its capture date

#### Scenario: Old cache is invalidated and rebuilt
- **WHEN** the app starts with a cache written by an older version that has no capture date column
- **THEN** the cache is invalidated, rebuilt from a MediaStore scan, and photos now carry capture dates

### Requirement: Album photos are sorted by capture date, newest first
The system SHALL always display album photos sorted by capture date in descending order (most recently taken photo first). The sort order is fixed; there is no user-facing option to change it.

#### Scenario: Photos ordered by capture date
- **WHEN** an album is displayed and its photos have capture dates
- **THEN** photos are shown most recently taken first

#### Scenario: Fallback to date added when capture date is missing
- **WHEN** a photo has no capture date (null/0)
- **THEN** that photo is ordered by the date it was added to the device, interleaved correctly with photos that have capture dates

#### Scenario: Sort applies to cached and freshly scanned photos alike
- **WHEN** the album is displayed from the local cache or from a fresh MediaStore scan
- **THEN** the same newest-first capture-date ordering applies in both cases

### Requirement: No EXIF data is parsed or stored for sorting
The system SHALL NOT read, parse, or persist EXIF data for the purpose of sorting. Sorting relies exclusively on the MediaStore `DATE_TAKEN` column (with the date-added fallback). On-demand EXIF display in the photo viewer is unaffected.

#### Scenario: Sorting works without opening any photo file
- **WHEN** the album is displayed
- **THEN** the ordering is computed from MediaStore/cache metadata only, without reading image file contents
