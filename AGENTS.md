## Project constraints

- Native Java and Android framework APIs only. No external app or test libraries.
- Build and test through Docker; do not install SDK/JDK tools on the host.
- Keep permission use narrow. Do not add screen-content access to work around vendor key behavior.
- Keep remaps persistent, navigation keys safe, and reset reachable with the remote.
- Show review builds on the ADB-connected TV when requesting feedback.
