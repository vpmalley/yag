# Tasks

## 1. Extend ExifUtils to extract new fields

- [x] 1.1 Add dateTaken, cameraModel, and lensModel fields to ExifUtils.ExifData class and verify the class compiles
- [x] 1.2 Add parsing methods for date (from TAG_DATETIME, TAG_DATETIME_ORIGINAL, TAG_DATETIME_DIGITIZED), camera make/model (from TAG_MAKE, TAG_MODEL), and lens model (from TAG_LENS_MAKE, TAG_LENS_MODEL) and verify ExifUtils tests pass
- [x] 1.3 Update extractExifData to populate the new fields from EXIF tags and verify with a sample photo that has these fields

## 2. Update Bottom Sheet Layout

- [x] 2.1 Add date taken TextView with label to layout_photo_details_bottom_sheet.xml and verify layout renders without errors
- [x] 2.2 Add camera model TextView with label to layout_photo_details_bottom_sheet.xml and verify layout renders without errors
- [x] 2.3 Add lens model TextView with label to layout_photo_details_bottom_sheet.xml and verify layout renders without errors
- [x] 2.4 Remove exifTagsContainer, EXIF Tags header, and EXIF Data header from layout_photo_details_bottom_sheet.xml and verify they no longer appear
- [x] 2.5 Reorganize layout to place new fields appropriately and verify the visual hierarchy looks correct

## 3. Update PhotoViewerFragment

- [x] 3.1 Add date taken, camera model, and lens model TextView references in updateBottomSheetContent and verify no null pointer exceptions
- [x] 3.2 Update updateBottomSheetContent to populate the new fields from ExifData and verify values display correctly for photos with the data
- [x] 3.3 Remove updateExifTags call and exifTagsContainer update from updateBottomSheetContent and verify EXIF chips no longer appear
- [x] 3.4 Verify all existing EXIF fields (focal length, shutter speed, aperture, ISO) still display correctly

## 4. Testing

- [x] 4.1 Test with photo that has all EXIF data (date, camera model, lens model) and verify all fields display correctly
- [x] 4.2 Test with photo missing date taken and verify dash (-) is displayed
- [x] 4.3 Test with photo missing camera model and verify dash (-) is displayed
- [x] 4.4 Test with photo missing lens model and verify dash (-) is displayed
- [x] 4.5 Test with photo missing all new fields and verify all show dashes
- [x] 4.6 Verify existing EXIF fields (focal length, shutter speed, aperture, ISO) still work correctly
- [x] 4.7 Verify EXIF tag chips are completely removed from the UI
