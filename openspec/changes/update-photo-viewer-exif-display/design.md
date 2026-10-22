# Design

## Context

The photo viewer bottom sheet currently displays photo name, path, file size, and four EXIF fields (focal length, shutter speed, aperture, ISO) in a 2x2 grid, followed by EXIF tag chips. The ExifUtils class extracts EXIF data but doesn't currently extract date taken, camera model, or lens model. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Add date taken, camera model, and lens model to the bottom sheet display
- Remove EXIF tag chips section
- Maintain existing EXIF display functionality
- Handle missing data gracefully with dash (-) placeholder

**Non-Goals:**
- Add new EXIF fields beyond the three specified
- Change the existing EXIF display format
- Add editing capabilities for EXIF data

## Decisions

### Layout Organization
**Decision**: Add new fields to the existing grid layout, replacing the EXIF chips section.

**Rationale**: The current grid layout (2x2) can be expanded to accommodate the new fields. We'll add date taken as a prominent field near the top, and camera/lens model in a dedicated section. This maintains visual consistency.

**Alternatives considered**:
- Create a separate section for metadata: Would add unnecessary visual hierarchy
- Add fields inline with existing EXIF: Would make the grid too crowded

### Date Format
**Decision**: Display date in format "MMM dd, yyyy HH:mm" (e.g., "Oct 15, 2024 14:30")

**Rationale**: This format is human-readable and includes both date and time, which is the most useful information for photos.

**Alternatives considered**:
- ISO 8601 format: More precise but less readable
- Date only: Loses time information which can be important

### Data Extraction Strategy
**Decision**: Extend ExifUtils.ExifData to include dateTaken, cameraModel, and lensModel fields, and update extractExifData to populate them from standard EXIF tags (TAG_DATETIME, TAG_DATETIME_ORIGINAL, TAG_MAKE, TAG_MODEL, TAG_LENS_MAKE, TAG_LENS_MODEL).

**Rationale**: Centralizing EXIF extraction in ExifUtils keeps the code maintainable and consistent. These are standard EXIF tags supported by Android's ExifInterface.

**Alternatives considered**:
- Extract in PhotoViewerFragment: Would duplicate extraction logic
- Create separate utility: Would add unnecessary complexity for this scope

### Missing Data Handling
**Decision**: Use dash (-) as placeholder for all missing fields, consistent with existing behavior for other EXIF fields.

**Rationale**: Maintains visual consistency with the existing codebase. Users understand dash as "not available".

**Alternatives considered**:
- Show "N/A" or "Unknown": Would be inconsistent with existing UI
- Hide the field entirely: Would cause layout shifts and confusion

## Risks / Trade-offs

**[Risk] EXIF date format variations** → Parse multiple date tags (DATETIME, DATETIME_ORIGINAL, DATETIME_DIGITIZED) with fallback chain to ensure we get a date when available.

**[Risk] Camera/lens model data missing in some photos** → Already handled by the dash placeholder decision. No functional impact.

**[Risk] Layout may be too tall on small screens** → The bottom sheet is scrollable via NestedScrollView, so content height is not a concern.

**[Risk] Performance impact of additional EXIF extraction** → ExifInterface already reads all tags; we're just adding parsing for a few more fields. Negligible performance impact.
