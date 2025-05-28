# BLE Debug Mode Feature Development Progress

## Overview
This file tracks the development and implementation of the BLE debug mode feature, which allows activating debug mode from the settings page and displaying extra sensor information when active.

---

## Task List

### UI Changes
- [x] Add a debug mode toggle/button in the settings page
- [ ] Indicate debug mode status in the UI (active/inactive)
- [x] Update sensor data display to show extra information (raw values) when debug mode is active

### BLE Communication
- [x] Implement function to send debug mode activation/deactivation command to BLE device
- [x] Update BLE data parsing logic to handle and display raw sensor values when debug mode is active

### State Management
- [x] Store debug mode status in the app state (Redux, Context, or local state)
- [x] Ensure UI and BLE logic react to changes in debug mode status

### Documentation & Tracking
- [x] Create this `DEV_PROGRESS.md` file to track implementation progress and decisions

---

## Notes
- Initial plan and checklist created. Update this file as tasks are completed or requirements change. 