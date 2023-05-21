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

## Installation

TBD.

## Structure

The project is built around several modules:

- `FreeLocationProvider`: the base library containing the provider itself,
- `FreeLocationProviderMapbox`: Mapbox Engine to ease Mapbox Navigation integration,
- `app`: An application to test the provider accuracy in several use cases - supports navigating 
  "In real life" using the GPS as well as replaying some recorded trip. Requires a Mapbox token.

## API usage

Once installed, this is how you may use this library.

### Using the Mapbox integration

```kotlin
val locationProvider = FreeLocationProvider.Builder.Builder()
            .sampleTimeLocationUpdate(1000)    // Time between updates in ms
            .build() // ACCESS_FINE_LOCATION permission MUST have ben accepted before calling

// Create Mapbox Engine
val engine = FreeLocationProviderMapboxEngine(
    context = this,
    provider = locationProvider,
).apply {
    initialize()    // Optionally, pass a replay file here to replay a trip
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

First, you must add your Mapbox secret token to `gradle.properties`:

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