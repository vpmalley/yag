# Proposal

## Why

The photo viewer's bottom sheet currently displays technical EXIF information (focal length, shutter speed, aperture, ISO) but lacks the most commonly requested metadata: the date the photo was taken. Additionally, users frequently want to know which camera and lens were used, but this information is currently not shown. The EXIF tag chips at the bottom are rarely used and clutter the interface without providing significant value.

## What Changes

- Add date taken field to the photo details bottom sheet, displaying when the photo was captured
- Add camera model field to show the device/manufacturer that took the photo
- Add lens model field to show the lens information
- Remove the EXIF tag chips section entirely as it's not useful for now
- Update ExifUtils to extract date, camera model, and lens model from EXIF data
- Update the bottom sheet layout to accommodate the new fields

## Capabilities

### New Capabilities
- `photo-viewer/exif-display`: Display date taken, camera model, and lens model in photo viewer bottom sheet, with proper fallback handling for missing data

### Modified Capabilities
- N/A

## Impact

- Affects: `PhotoViewerFragment.kt`, `ExifUtils.kt`, `layout_photo_details_bottom_sheet.xml`
- UI: Bottom sheet layout will be reorganized to add three new fields and remove the EXIF chips container
- Data: ExifData class will be extended with new fields for date, camera model, and lens model
- No breaking changes to existing functionality
