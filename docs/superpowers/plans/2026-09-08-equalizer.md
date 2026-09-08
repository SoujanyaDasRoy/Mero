# A real equalizer

Plan for replacing Mero's equalizer with one that works the same on every
phone, can be tested, and does things worth having.

## The problem is the framework, not the features

Everything that failed in the equalizer failed for one reason: it is built on
`android.media.audiofx`, which attaches effects to the system audio session
and leaves the behaviour to the device. Measured on a real session:

```
APM::EffectDescriptor: registerEffect() memory limit exceeded for
  Fx Insert Environmental Reverb, Memory 91 KB          (status -38)
AudioFlinger: createTrack_l(): mismatch between requested flags
  (00000008) and output flags (00000002)
AudioFlinger: moveEffectChain_ll: 4 effects moved, 0 effects started
```

Read in order, that is the whole story:

| symptom | cause |
|---|---|
| Reverb never worked | The session's effect memory budget refused it outright |
| Audio glitched | An occupied effect chain moved the track off the deep-buffer output |
| Spatial audio never worked | `Virtualizer.strengthSupported` is false on many outputs |
| Beat haptics never worked | `Visualizer` requires `RECORD_AUDIO` — a microphone prompt |
| Nothing was testable | Effects only exist against a live audio session on a device |

Four of five controls were removed rather than fixed, because within audiofx
they were not fixable. Continuing to add features there would keep producing
controls that work on one phone and not the next.

**The fix is to stop using it.** Media3 has its own audio pipeline, and
Mero already depends on every piece.

## What Media3 1.5.0 already gives us

Verified present in the AARs already on disk:

```
androidx.media3.common.audio.AudioProcessor          the interface
androidx.media3.common.audio.BaseAudioProcessor      base class to extend
androidx.media3.common.audio.SonicAudioProcessor     speed and pitch
androidx.media3.common.audio.ChannelMixingMatrix     channel matrixing
androidx.media3.exoplayer.audio.TeeAudioProcessor    taps PCM in-flight
androidx.media3.exoplayer.audio.WaveformAudioBufferSink   amplitude bars
androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor  32-bit float
androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
androidx.media3.exoplayer.audio.DefaultAudioSink.Builder.setAudioProcessors(…)
androidx.media3.exoplayer.DefaultRenderersFactory.buildAudioSink(…)
```

Processing inside ExoPlayer instead of the audio session changes the
constraints completely:

- **Identical on every device.** Our arithmetic, not the vendor's effect
  implementation.
- **No effect memory budget**, so nothing gets refused.
- **The output path is untouched**, so the deep buffer stays and the glitch
  cannot come back.
- **PCM without permission.** `TeeAudioProcessor` sees the samples inside our
  own pipeline. A visualizer — and the beat haptics that were deleted — need
  no microphone prompt.
- **Testable as pure functions.** An `AudioProcessor` is bytes in, bytes out.
  Feed a sine sweep, assert the magnitude response. This is exactly the "pure
  logic with real edge cases gets strict TDD" that CLAUDE.md asks for, and the
  current equalizer cannot be tested at all.

## Libraries

Checked against Maven Central, and against GPL-3.0 compatibility, since
vendoring `innertube` fixed Mero's licence.

| need | choice | why |
|---|---|---|
| Biquad filters | **Write them** (~60 lines) | The RBJ Audio EQ Cookbook coefficients are four lines of arithmetic per band. Every library wrapping them brings its own pipeline to adapt to. |
| FFT for the spectrum | **JTransforms 3.1** (`com.github.wendykierp:JTransforms:3.1`) — verified on Maven Central, BSD-2, GPL-compatible | A correct, fast real-FFT is genuinely worth importing; a hand-rolled radix-2 is where subtle bugs live. |
| Waveform display | `WaveformAudioBufferSink` | Already in Media3. No FFT needed for amplitude bars — worth shipping before the spectrum. |
| Speed / pitch | `SonicAudioProcessor` | Already in Media3. |
| Loudness measurement | **Write it** (EBU R128 / ITU-R BS.1770) | The K-weighting filter is two biquads and a mean-square — reusing the biquad code above. The JVM libraries for this are server-side and heavy. |

Rejected: **TarsosDSP** — not on Maven Central at the usual coordinates
(checked: 404), and its `AudioEvent` pipeline would need adapting to Media3
buffers anyway. **Superpowered** — commercial licence, incompatible with
GPL-3.0. **Dolby Atmos** — needs a Dolby licence, phone-maker firmware, and
Atmos source audio; YouTube serves stereo Opus, so none of the three hold.

## Phases

Each phase is shippable on its own and leaves the equalizer working.

### Phase 1 — Own the chain

Replace `DynamicsProcessing` with our own processor. No user-visible change
beyond the EQ starting to behave identically across devices.

- `MeroRenderersFactory : DefaultRenderersFactory`, overriding `buildAudioSink`
  to install our processors via `DefaultAudioSink.Builder.setAudioProcessors`
- `EqualizerAudioProcessor : BaseAudioProcessor` — ten cascaded peaking
  biquads at the existing centre frequencies, plus preamp with automatic
  headroom so boosting cannot clip
- Delete `AudioEffects`' remaining audiofx use
- **Tests:** sine sweep in, FFT out, assert each band's gain lands within
  0.5 dB of the setting and that a flat EQ is bit-transparent

*Risk:* processing cost on low-end phones. Ten biquads on stereo 48 kHz is a
few hundred thousand multiply-adds a second — negligible, but worth measuring
on the oldest phone in the group.

### Phase 2 — Show the sound

The equalizer currently gives no feedback that it is doing anything.

- `TeeAudioProcessor` + `WaveformAudioBufferSink` → live amplitude bars
- Then JTransforms → a real spectrum, drawn behind the band sliders
- A response curve over the sliders, so the shape being dialled in is visible
- Restore **beat haptics** here — this time with no permission prompt, which
  is why it was removed

### Phase 3 — Things worth having

In rough order of value per line of code:

1. **Playback speed and pitch** — `SonicAudioProcessor`, near-free, and useful
   for more than podcasts
2. **Custom presets** — name, save, delete. The preset list is a fixed map
   today and "Custom" is a hardcoded curve, which is slightly absurd
3. **Per-output profiles** — one EQ for headphones, another for the speaker,
   switched automatically via `AudioDeviceCallback`. Phones sound nothing like
   headphones and a single curve cannot serve both
4. **Real loudness normalization** — EBU R128 per track, replacing the current
   fixed +1.5 dB nudge that does not deserve the name
5. **Crossfeed** (Bauer BS2B) — bleeds a filtered, delayed channel into the
   other so headphone stereo stops sounding like two separate speakers. A
   well-specified algorithm, roughly 80 lines, and the single biggest
   improvement to long headphone listening
6. **Parametric bands** — drag frequency and Q, not only gain
7. **A proper limiter** with attack, release and makeup gain

### Phase 4 — Fit and finish

- A/B bypass so a curve can be compared against flat
- Import/export a preset as text, to pass around the group
- Per-track EQ memory, if it turns out anyone wants it

## What this deliberately does not do

- **No system-wide EQ.** Mero processes its own output; changing other apps'
  audio needs audiofx and its problems back.
- **No Atmos or "3D audio".** Covered above — the licensing and the source
  audio both rule it out. Saying so in the UI beats a switch that does
  nothing, which is what the last three attempts produced.
- **No resampling or bit-perfect output.** Interesting, and not what anyone in
  this group has asked for.

## Suggested order

Phase 1 alone is the one that matters: it turns the equalizer from something
that behaves differently on every phone into something we control and can
test. Phase 2 is what makes it *feel* like a real equalizer. Phase 3 items are
independent and can be picked off in any order — crossfeed and per-output
profiles are the two I would take first.
