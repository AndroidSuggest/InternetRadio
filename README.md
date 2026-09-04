<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" style="border-radius: 50%;" />

# InternetRadio

InternetRadio is an Android app for discovering and listening to radio stations from around the world, with features like favorites, recent history, and advanced search by country,language and tags.

</div>

## Download

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.armanmaurya.internetradio)
[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/en/packages/com.armanmaurya.internetradio/)
[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/armanmaurya/internetradio/releases/latest/)

---

## Community

Join the Telegram community for discussions, feedback, and announcements, sharing stations:

[![Telegram](https://img.shields.io/badge/Telegram-Join%20Community-2CA5E0?logo=telegram&logoColor=white)](https://t.me/internetradiocommunity)

---

## Features

- **Global Radio Access**: Browse and stream thousands of radio stations globally.
- **Search, Filter & Sort**: Easily find stations by country, language, or tags, with advanced sorting options.
- **Library & Customization**: Manage station Library. Edit any station and configure the startup screen.
- **Android TV Support**: Enjoy a fully optimized and tailored experience on Android TV devices.
- **Android Auto Support**: Support for car screen via android auto.
- **FCast Support**: Cast and control radio stations on FCast-compatible devices.
- **Recording & Scheduling**: Record live streams to your device or set schedules for automated playback and recording.
- **Recent History**: Keep track of your recently played streams.
- **Backup & Restore**: Easily backup and restore your library and app settings.
- **Modern UI**: Built with Jetpack Compose for a smooth, intuitive, and responsive experience across phones, tablets, and widescreen devices.

## Screenshots

### Mobile

<div align="center">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpeg" alt="Screenshot 1">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpeg" alt="Screenshot 2">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpeg" alt="Screenshot 3">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpeg" alt="Screenshot 4">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpeg" alt="Screenshot 5">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpeg" alt="Screenshot 6">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.jpeg" alt="Screenshot 7">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.jpeg" alt="Screenshot 8">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.jpeg" alt="Screenshot 9">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.jpeg" alt="Screenshot 10">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.jpeg" alt="Screenshot 11">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.jpeg" alt="Screenshot 12">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.jpeg" alt="Screenshot 13">
  <img width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/14.jpeg" alt="Screenshot 14">
</div>

### Android TV

<div align="center">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/1.png" alt="TV Browse Screen">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/2.png" alt="TV Player Screen">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/3.png" alt="Select Country">
  <img width="48%" src="fastlane/metadata/android/en-US/images/tvScreenshots/4.png" alt="Select Language">
</div>

## Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **APIs/Services**: [Radio Browser API](https://www.radio-browser.info/), [iTunes Search API](https://affiliate.itunes.apple.com/resources/documentation/itunes-store-web-service-search-api/), [LrcLib API](https://lrclib.net/), [FCast](https://fcast.org/)

## Building From Source

To build InternetRadio from source, ensure you have the latest version of Android Studio installed.

1. **Clone the repository**:
   ```bash
   git clone https://github.com/armanmaurya/internetradio.git
   ```
2. **Open the project** in Android Studio.
3. **Wait for Gradle sync** to complete.
4. **Run the app** on a physical device or emulator.

## Troubleshooting

If FCast is not discovering or connecting to your device on desktop Linux, your firewall may be blocking the required ports. This is a known issue with **UFW** and similar firewall tools. To allow FCast connections, run:

```bash
sudo ufw allow 46899/tcp
sudo ufw allow 46899/udp
```

---

## Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are greatly appreciated.

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## Contributors

<a href="https://github.com/armanmaurya/InternetRadio/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=armanmaurya/InternetRadio" alt="InternetRadio Contributors"/>
</a>

## Translation

Translations are managed on <a href="https://hosted.weblate.org/projects/internetradio/">Weblate</a> — no local setup needed, contribute directly from your browser.

<a href="https://hosted.weblate.org/engage/internetradio/"><img src="https://hosted.weblate.org/widget/internetradio/multi-auto.svg"></a>

## Acknowledgments

- [Maintendo](https://github.com/Maintendo) - app logo.

## License

This project is licensed under the GNU GPL v3 License - see the [LICENSE](LICENSE) file for details.
