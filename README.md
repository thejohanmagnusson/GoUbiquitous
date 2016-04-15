# GoUbiquitous - Udacity Android Nanodegree, project 6 Go Ubiquitous
A weather app was the base for the project and a companion app for wearables was added. Both round and square watch faces are supported.

## Requirements
The app was developed with Android Studio and targets the Android Lollipop 5.1 (API 22) platform.

The app uses data from the OpenWeatherMap, to get data from this service a API key is needed.
A key can be acquired from [here] (http://www.openweathermap.org/appid)

Add the key to your *gradle.properties*, note that there are two of these in your Android Studio project! Place the key in the one marked *Global Properties*. The file path to the file on Windows is %USERPROFILE%\.gradle\gradle.properties
**Do not put your API key in build.gradle for security reasons!**

Example: MyOpenWeatherMapApiKey="This-is-your-key-string"

## Dependencies
Enumerated in the app's build.gradle, we require:

- [Android 5.1] (https://developer.android.com/about/versions/lollipop.html)
- [Android support libraries] (https://developer.android.com/tools/support-library/features.html)
- [Glide] (https://github.com/bumptech/glide)
- [Google play services] (https://developers.google.com/android/guides/overview)
- [Muzei] (https://github.com/romannurik/muzei/wiki/API)

## Building and Running
Import the GoUbiquitous folder into Android Studio as an existing project and select `Run > Run app`.
