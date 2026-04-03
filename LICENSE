# Contributing to CAMMIC

First off, thank you for considering contributing to CAMMIC! It's people like you that make CAMMIC such a great tool for privacy monitoring on Android.

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues as you might find out that you don't need to create one. When you are creating a bug report, please include as many details as possible:

* **Use a clear and descriptive title**
* **Describe the exact steps to reproduce the problem**
* **Provide specific examples to demonstrate the steps**
* **Describe the behavior you observed and what behavior you expected**
* **Include screenshots if possible**
* **Include device information:**
  * Device model
  * Android version
  * CAMMIC version

**Example bug report:**

```markdown
**Device:** Pixel 7 Pro
**Android Version:** 14 (API 34)
**CAMMIC Version:** 1.0

**Steps to Reproduce:**
1. Grant all permissions
2. Start monitoring
3. Open WhatsApp and make a voice call
4. Check the event list

**Expected:** WhatsApp should be shown as the package using microphone
**Actual:** Shows "Unknown Background Process"
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

* **Use a clear and descriptive title**
* **Provide a detailed description of the suggested enhancement**
* **Explain why this enhancement would be useful**
* **List some examples of how this enhancement would be used**

### Pull Requests

* Fill in the required template
* Follow the coding style (Kotlin conventions)
* Include comments in your code where necessary
* Update documentation if needed
* Test your changes on a physical device

## Development Setup

### Prerequisites

* Android Studio Hedgehog or later
* JDK 11 or later
* Android SDK with API 34+
* A physical Android device (API 24+) for testing

### Building from Source

1. **Clone the repository**
   ```bash
   git clone https://github.com/daviderreg/CAM_MIC.git
   cd CAM_MIC
   ```

2. **Open in Android Studio**
   * Launch Android Studio
   * Select "Open an Existing Project"
   * Navigate to the cloned directory

3. **Sync Gradle**
   * Wait for Gradle sync to complete
   * Resolve any SDK component prompts

4. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

## Coding Style

* Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
* Use meaningful variable and function names
* Keep functions small and focused
* Add KDoc comments for public APIs
* Use the existing code style as a reference

### Code Review Process

After you submit a PR:
1. Maintainers will review your code
2. Address any feedback or requested changes
3. Once approved, your PR will be merged

## Questions?

Feel free to open an issue with the "question" label if you have any questions about contributing!

## Thank You!

Your contributions to open source, large or small, make projects like this possible. Thank you for taking the time to contribute.
