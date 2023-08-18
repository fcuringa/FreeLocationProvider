# FreeLocationProvider 

> :warning: This project is currently a Work-In-Progress, documentation is not fully complete and 
> unit tests are not yet present. Use the released versions at your own risk.

FreeLocationProvider is an Android library whose purpose is to provide an easy-to-use replacement
for the default GPS provider included in Android's `LocationManager`. This is achieved by:

1. Fusing the raw GPS updated from `LocationManager` with sensor data from the device's 
   accelerometer and gyroscope using a Kalman filter to provide more accurate positioning and offer
   some positioning in no-reception areas (tunnels, etc);
2. Caching the last known location when the app is stopped to quickly provide a location to the app
   when it starts;
3. Offering a full Kotlin-Flow-based API with minimal configuration, in addition to some built-in
   integration with popular Navigation libraries (currently limited to Mapbox).

## Structure

The project is built around several modules:

- `FreeLocationProvider`: the base library containing the provider itself,
- `FreeLocationProviderMapbox`: Mapbox Engine to ease Mapbox Navigation integration,
- `app`: An application to test the provider accuracy in several use cases - supports navigating 
  "In real life" using the GPS as well as replaying some recorded trip. Requires a Mapbox token.

## Installation

All artifacts are uploaded in Maven Central.

Make sure you set it up in your project's `settings.gradle`.

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        // ...
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // ...
    }
}
// ...
```

### Base provider - `FreeLocationProvider`

Add the dependency in your app's `build.gradle`:

```groovy
implementation 'io.github.fcuringa:freelocationprovider:0.2.0'
```

### Mapbox engine - `FreeLocationProviderMapbox`

You are required to setup your access to Mapbox Navigation first.

See https://docs.mapbox.com/android/navigation/guides/get-started/install/.

Add the dependencies in your app's `build.gradle`:

```groovy
implementation "com.mapbox.navigation:android:2.10.4"   // Mapbox Navigation

// FreeLocationProvider dependencies
implementation 'io.github.fcuringa:freelocationprovider-mapbox:0.2.0'
implementation 'io.github.fcuringa:freelocationprovider:0.2.0'
```

## API usage

Once installed, this is how you may use this library.

### Using the Mapbox integration

```kotlin
// Create the location provider
val locationProvider = FreeLocationProvider.Builder(this.baseContext)
    .configure { provider ->
        provider.coroutineScope = lifecycleScope        // Tie the provider to the activity lifecycle
        provider.coroutineDispatcher = Dispatchers.IO
        provider.engineType = EngineType.FUSED          // Use fused engine
        provider.sampleTimeLocationUpdateMs = 1000      // Request 1 location update per second
    }
    .build()    // ACCESS_FINE_LOCATION permission MUST have ben granted before calling

// Create and initialize engine
val engine = FreeLocationProviderMapboxEngine.Builder(this.baseContext)
    .configure { mEngine ->
        mEngine.coroutineScope = lifecycleScope
        mEngine.coroutineDispatcher = Dispatchers.IO
        mEngine.isDebugEnabled = true           // Print debug logs in logcat
        mEngine.isDebugLogFileEnabled = true    // Save those logs in a file under .FreeLocationProvider 
        mEngine.sensorDelay = SensorManager.SENSOR_DELAY_NORMAL
    }
    .build(locationProvider)
    .apply {
        initialize()    // Optionally pass a replay file to replay a previous route instead of tracking
    }

// Finally setup MapboxNavigation
val navigationOptions = NavigationOptions.Builder(this)
        .accessToken("your_access_token")    // Replace with your access token
        .locationEngine(engine)
        .build()

// Use those NavigationOptions to setup MapboxNavigation as usual
// See https://docs.mapbox.com/android/navigation/guides/get-started/initialization/
```

### Using the provider

TBD

## Test app usage

The application will not compile out-of-the-box as it requires some credentials tied to Mapbox.

See https://docs.mapbox.com/android/navigation/guides/get-started/install/.

First, you must add your Mapbox secret token to `mapbox.properties`:

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.ey...
```

Then, you must add your Mapbox API token under `app/src/main/res/values/mapbox.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="mapbox_access_token">pk.ey...</string>
</resources>
```

Finally, you should be able to compile the app using the standard `app` target in Android Studio.
