# Spec Delta

## Purpose

Display comprehensive EXIF metadata in the photo viewer bottom sheet, including date taken, camera model, and lens model, while removing the rarely used EXIF tag chips.

## ADDED Requirements

### Requirement: Display photo date taken
The system SHALL display the date and time when the photo was taken in the bottom sheet. If the date is not available in EXIF data, the system SHALL display a dash (-).

#### Scenario: Photo with date taken
- **WHEN** a photo with valid EXIF date taken data is viewed
- **THEN** the bottom sheet displays the formatted date and time

#### Scenario: Photo without date taken
- **WHEN** a photo without EXIF date taken data is viewed
- **THEN** the bottom sheet displays "-" for the date taken field

### Requirement: Display camera model
The system SHALL display the camera model (device/manufacturer) from EXIF data in the bottom sheet. If the camera model is not available, the system SHALL display a dash (-).

#### Scenario: Photo with camera model
- **WHEN** a photo with valid EXIF camera model data is viewed
- **THEN** the bottom sheet displays the camera model

#### Scenario: Photo without camera model
- **WHEN** a photo without EXIF camera model data is viewed
- **THEN** the bottom sheet displays "-" for the camera model field

### Requirement: Display lens model
The system SHALL display the lens model from EXIF data in the bottom sheet. If the lens model is not available, the system SHALL display a dash (-).

#### Scenario: Photo with lens model
- **WHEN** a photo with valid EXIF lens model data is viewed
- **THEN** the bottom sheet displays the lens model

#### Scenario: Photo without lens model
- **WHEN** a photo without EXIF lens model data is viewed
- **THEN** the bottom sheet displays "-" for the lens model field

## REMOVED Requirements

### Requirement: Display EXIF tag chips
**Reason**: EXIF tag chips are not useful for users and clutter the interface
**Migration**: Remove the EXIF tag chips container and related code from the bottom sheet

#### Scenario: EXIF chips removed
- **WHEN** user opens the photo details bottom sheet
- **THEN** no EXIF tag chips are displayed at the bottom of the sheet
