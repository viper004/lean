# Motorcycle Lean Angle / Inclinometer App 

A complete, high-precision personal motorcycle lean-angle and inclinometer application built from scratch using **Android Studio + Kotlin + Jetpack Compose + Material 3**.
Latest Build : https://drive.google.com/file/d/1tjGrjM6tVonBtNM27XurQrCO_k3rXlt9/view?usp=sharing

---

## 🌟 Key Features

1. **Arbitrary Mount Orientation & Zero Calibration (CENTER Button)**
   - Mount your phone in **any physical orientation** (tilted pitch/roll, landscape, portrait, angled mount).
   - Tap **CENTER** to immediately set the current physical orientation as the reference **0.0°**.
   - Lean right relative to reference $\rightarrow$ positive angle (`+32.5° RIGHT`).
   - Lean left relative to reference $\rightarrow$ negative angle (`-18.2° LEFT`).

2. **Sensor Fusion **
   - **Primary Sensors**: Gyroscope (`Sensor.TYPE_GYROSCOPE`) + Accelerometer (`Sensor.TYPE_ACCELEROMETER`).
   - **Fused Mode**: Uses Android `TYPE_GAME_ROTATION_VECTOR` or complementary sensor fusion (gyroscope angular velocity integration + low-pass accelerometer gravity correction).
   - **Hardware Fallback**: Automatically detects missing gyroscope hardware. Automatically falls back to accelerometer tilt measurement and displays an unobtrusive warning (`Gyroscope unavailable — using accelerometer mode.`).
   - **Offline & Allocation-Free**: Zero backend required, 100% offline, zero allocation inside high-frequency sensor callbacks (50–100 Hz).

3. **High-Contrast Modern Dashboard UI**
   - Sleek dark outdoor high-contrast visual design optimized for motorcycle riders.
   - **Large Angle Display**: Prominent monospace numbers showing instant lean angle.
   - **Visual Horizontal Gauge**: Arc/bar indicator with dynamic needle and peak indicators.
   - **Peak Lean Tracking**: Real-time tracking of `MAX LEFT` and `MAX RIGHT` lean angles with `RESET PEAKS` control.
   - **Subtle Feedback**: Clean `"Centered"` overlay toast confirmation upon zero calibration.

4. **Developer Diagnostic Screen**
   - Inspect raw 3D sensor values: Accelerometer (X, Y, Z in $\text{m/s}^2$) and Gyroscope (X, Y, Z in $\text{rad/s}$).
   - Real-time sensor update frequency (Hz), raw vs filtered lean angle, and orientation gravity vectors.

5. **Settings & Safety Disclaimer**
   - Sensor mode selection: Automatic, Gyro + Accel, Accel only.
   - Configurable smoothing level (Fast, Balanced, Smooth).
   - Keep screen awake option (`FLAG_KEEP_SCREEN_ON`).
   - Reset calibration and reset peak controls.
   - Prominent Safety Disclaimer as required.

---

## 📐 Math & Calibration Formulation

The application avoids simple Euler angle subtraction to prevent gimbal lock and wrapping errors:
1. **At Calibration (CENTER button)**:
   - Captures current unit gravity vector $\mathbf{g}_{ref} = (g_x, g_y, g_z)^T$.
   - Constructs an orthonormal reference coordinate basis frame $(\mathbf{x}_{ref}, \mathbf{y}_{ref}, \mathbf{z}_{ref})$ in phone body space, where $\mathbf{z}_{ref} = -\mathbf{g}_{ref}$ (upward vector at zero reference).
2. **Relative Lean Calculation**:
   - For subsequent sensor readings $\mathbf{g}_{curr}$, project onto reference RIGHT axis $\mathbf{x}_{ref}$ and UP axis $\mathbf{z}_{ref}$:
     $$g_x = \mathbf{g}_{curr} \cdot \mathbf{x}_{ref}, \quad g_z = \mathbf{g}_{curr} \cdot \mathbf{z}_{ref}$$
   - Calculate relative lean roll angle:
     $$\text{Lean Angle} = \text{atan2}(-g_x, -g_z) \times \frac{180}{\pi}$$
   - Guaranteed $0.0^\circ$ at calibration, robust against gimbal lock and arbitrary mounting angles.

---

## 📁 Project Architecture

```
com.example.lean/
├── MainActivity.kt               # Main entrypoint, Compose navigation, Screen Awake flag
├── data/
│   ├── LeanState.kt              # UI state data class (angle, peaks, status, diagnostics)
│   ├── SensorMode.kt             # Sensor mode enum (AUTOMATIC, FUSED, ACCEL_ONLY)
│   └── UserSettings.kt           # Preference data model
├── sensor/
│   ├── LeanSensorManager.kt      # Hardware sensor listener & Realme 11x fallback detection
│   └── SensorStatus.kt           # Sensor availability data class
├── orientation/
│   ├── Vector3D.kt               # 3D vector arithmetic utility
│   ├── Matrix3x3.kt              # 3x3 Rotation matrix transformations
│   └── OrientationEstimator.kt   # Complementary filter & gravity estimation
├── calibration/
│   └── CalibrationManager.kt     # Zero-reference frame & relative roll angle math
├── settings/
│   └── SettingsRepository.kt     # SharedPreferences persistence
└── ui/
    ├── LeanViewModel.kt          # ViewModel managing state & sensor pipeline
    ├── components/
    │   ├── CenterButton.kt       # Calibration button with visual feedback
    │   ├── CenteredToastOverlay.kt# "Centered" confirmation toast overlay
    │   ├── LeanAngleDisplay.kt   # Prominent angle text & direction indicator
    │   ├── LeanGauge.kt          # Visual horizontal inclinometer gauge
    │   ├── PeakLeanCard.kt       # Max Left / Max Right peak lean card
    │   └── SensorStatusChip.kt   # Diagnostic status badges & warning chips
    ├── screens/
    │   ├── MainLeanScreen.kt     # Primary motorcycle dashboard UI
    │   ├── SettingsScreen.kt     # Settings, calibration reset & safety disclaimer
    │   └── DebugSensorScreen.kt  # Developer sensor diagnostic view
    └── theme/
        ├── Color.kt              # Dark high-contrast color palette
        ├── Theme.kt              # MaterialTheme configuration
        └── Type.kt               # Typography definitions
```

---

## 🛠️ Build & Run Instructions

### Prerequisites
- **Android Studio** Ladybug (or newer) / Intellij IDEA.
- **JDK 17** configured.
- **Android SDK Platform 35** and Build-Tools installed.

### Building via Command Line
Run the following command in the project root:

```bash
./gradlew assembleDebug
```

The compiled APK artifact will be placed at:
`app/build/outputs/apk/debug/app-debug.apk`

### Installing on Device / Emulator
To install directly to a connected Android device :

```bash
./gradlew installDebug
```

---

## 🔒 Safety Disclaimer
*Sensor measurements are approximate and should not be relied upon for vehicle safety or riding decisions.*
