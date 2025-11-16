# Java Version Fix

## Issue
You're using Java 25, but Gradle 8.5 supports up to Java 21. Android development typically uses Java 17 or 21.

## Solution Options

### Option 1: Use Java 17 or 21 (Recommended)

**Install Java 17 or 21:**

#### On Arch Linux (using your system):
```bash
# Install Java 17
sudo pacman -S jdk17-openjdk

# Or install Java 21
sudo pacman -S jdk21-openjdk
```

**Set JAVA_HOME:**
```bash
# For Java 17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# For Java 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Add to ~/.bashrc or ~/.zshrc to make permanent
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
```

**Verify:**
```bash
java -version
# Should show Java 17 or 21
```

**Then build:**
```bash
./gradlew assembleDebug
```

### Option 2: Use Android Studio (Easiest)

Android Studio comes with its own JDK (usually Java 17) and will use it automatically:

1. Open the project in Android Studio
2. Android Studio will use its bundled JDK
3. Click **Run** - it will build and install automatically

### Option 3: Use jenv or SDKMAN (Multiple Java Versions)

**Using SDKMAN:**
```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 17
sdk install java 17.0.9-tem

# Use Java 17 for this project
sdk use java 17.0.9-tem
```

**Using jenv:**
```bash
# Install jenv (if not installed)
# Add Java 17
jenv add /usr/lib/jvm/java-17-openjdk

# Set local Java version for this project
cd /path/to/CyKrome-Launcher
jenv local 17
```

## Quick Check

After setting Java 17 or 21:
```bash
java -version
./gradlew --version
./gradlew assembleDebug
```

## Why Java 17/21?

- **Java 17**: LTS (Long Term Support) - Most stable for Android
- **Java 21**: Latest LTS - Also well supported
- **Java 25**: Too new, not yet fully supported by Android toolchain

## Android Studio Recommendation

The easiest solution is to use **Android Studio**, which:
- Comes with its own JDK (Java 17)
- Handles all build configuration automatically
- Provides better debugging and testing tools
- No need to manage Java versions manually

