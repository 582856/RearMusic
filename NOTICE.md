# Third-party notices

RearMusic is distributed under the GNU General Public License version 3. See LICENSE.

The music registry discovery and package-to-`music` whitelist approach in
`WhitelistHook.java` is adapted from REAREye's MusicControlWhitelistModule and
SmartAssistantRegistry, copyright REAREye contributors, licensed GPL-3.0.

Source: https://github.com/killerprojecte/REAREye

Reference revision: cec416e54ab268c626c0f054a2a234cb9787a64a

Changes: Java/reflective implementation, standalone configuration provider,
reversible map updates, independent error handling and optional metadata refresh.

Other runtime dependencies:

- miuix, compose-miuix-ui contributors, Apache-2.0: https://github.com/compose-miuix-ui/miuix
- AndroidX / Jetpack Compose, Android Open Source Project, Apache-2.0.
- Kotlin, JetBrains and contributors, Apache-2.0.
- DexKit, LuckyPray and contributors, Apache-2.0: https://github.com/LuckyPray/DexKit

Compile-only Xposed declarations are API signatures, not bundled framework implementations.
The original RearMusic FFT mapping and bar drawing do not include decompiled player source.
