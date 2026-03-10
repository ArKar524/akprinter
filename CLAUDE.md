# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

React Native 0.84.1 mobile app ("akprint") bootstrapped with `@react-native-community/cli`. Uses React 19, TypeScript, and Hermes JS engine.

## Commands

- **Start Metro dev server:** `npm start`
- **Run on Android:** `npm run android`
- **Run on iOS:** `npm run ios` (requires `bundle install && bundle exec pod install` first)
- **Lint:** `npm run lint`
- **Run tests:** `npm test`
- **Run a single test:** `npx jest __tests__/App.test.tsx`

## Architecture

- `index.js` — App entry point, registers root component via `AppRegistry`
- `App.tsx` — Root component, wraps content in `SafeAreaProvider`
- `android/` — Native Android project (Kotlin, Gradle, namespace `com.akprint`)
- `ios/` — Native iOS project (Swift, CocoaPods)
- `__tests__/` — Jest tests using `react-test-renderer`

## Key Dependencies

- `react-native-safe-area-context` — Safe area insets handling
- `@react-native/new-app-screen` — Default template screen component

## Node Requirement

Node >= 22.11.0 (specified in `package.json` engines)
