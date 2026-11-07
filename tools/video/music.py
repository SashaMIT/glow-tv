"""An original, license-free backing track for Glow's promo video: warm chords, a plucked arpeggio and a soft beat.
Usage: python3 music.py OUT.wav SECONDS   (needs numpy)"""
import sys, wave
import numpy as np

OUT, LEN = sys.argv[1], float(sys.argv[2])
SR, BPM = 44100, 104
BEAT = 60 / BPM
rng = np.random.default_rng(98)


def midi(n):
    return 440 * 2 ** ((n - 69) / 12)


def tone(freq, dur, harmonics=(1, .5, .25), attack=.01, decay=.25, sustain=0.0):
    t = np.arange(int(dur * SR)) / SR
    s = sum(a * np.sin(2 * np.pi * freq * (k + 1) * t) for k, a in enumerate(harmonics))
    env = np.minimum(t / attack, 1) * (sustain + (1 - sustain) * np.exp(-t / decay))
    return s * env


def add(buf, at, s, gain=1.0):
    i = int(at * SR)
    n = min(len(s), len(buf) - i)
    if n > 0:
        buf[i:i + n] += s[:n] * gain


total = int((LEN + 2) * SR)
pad, pluck, bass, drums = (np.zeros(total) for _ in range(4))
# Four bars each: C  G/B  Am  F, brightening as the video goes on.
CHORDS = [(48, 60, 64, 67, 72), (47, 59, 62, 67, 74), (45, 57, 60, 64, 72), (41, 57, 60, 65, 69)]
bars = int(LEN / (4 * BEAT)) + 1
for b in range(bars):
    at = b * 4 * BEAT
    ch = CHORDS[b % 4]
    lift = min(1, at / (LEN * .5))
    for n in ch[1:]:
        for d in (-.6, .6):  # two slightly detuned voices
            add(pad, at, tone(midi(n) * 2 ** (d / 1200), 4 * BEAT + .6, (1, .35, .12), attack=.5, decay=3, sustain=.55), .05)
    add(bass, at, tone(midi(ch[0]), 2 * BEAT, (1, .3), attack=.01, decay=.7, sustain=.2), .24)
    add(bass, at + 2.5 * BEAT, tone(midi(ch[0]), 1.5 * BEAT, (1, .3), attack=.01, decay=.5, sustain=.2), .18)
    pattern = [1, 2, 3, 4, 3, 2, 3, 4]
    for k, p in enumerate(pattern * 2):
        when = at + k * BEAT / 4
        if k % 4 == 3 and rng.random() < .35:
            continue
        add(pluck, when, tone(midi(ch[p] + 12), .5, (1, .4, .2, .1), attack=.003, decay=.18), .06 + .05 * lift)
    if at >= 4 * BEAT:  # the beat comes in after the first bar
        for k in range(4):
            kick = np.sin(2 * np.pi * np.cumsum(45 + 90 * np.exp(-np.arange(int(.22 * SR)) / (.03 * SR))) / SR)
            add(drums, at + k * BEAT, kick * np.exp(-np.arange(len(kick)) / (.07 * SR)), .32)
            hat = rng.normal(0, 1, int(.05 * SR))
            hat = np.diff(hat, prepend=0) * np.exp(-np.arange(len(hat)) / (.012 * SR))
            add(drums, at + (k + .5) * BEAT, hat, .035 + .03 * lift)
            if k % 2 == 1:
                snare = rng.normal(0, 1, int(.14 * SR)) * np.exp(-np.arange(int(.14 * SR)) / (.04 * SR))
                add(drums, at + k * BEAT, snare, .05 + .03 * lift)

# A short echo on the plucks, side-chained under the kick for a little movement.
echo = np.zeros(total)
delay = int(.75 * BEAT * SR)
echo[delay:] = pluck[:-delay] * .35
pump = 1 - .25 * np.exp(-((np.arange(total) / SR) % BEAT) / .12)
left = (pad + bass) * pump + pluck * .9 + echo * .4 + drums
right = (pad + bass) * pump + pluck * .6 + echo * .8 + drums
mix = np.stack([left, right], 1)[:int(LEN * SR)]
n = len(mix)
fade = np.ones(n)
fade[:int(.4 * SR)] = np.linspace(0, 1, int(.4 * SR))
fade[-int(2.5 * SR):] = np.linspace(1, 0, int(2.5 * SR))
mix = mix * fade[:, None]
mix = np.tanh(mix * 1.3)
mix = mix / np.max(np.abs(mix)) * .89
with wave.open(OUT, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((mix * 32767).astype(np.int16).tobytes())
print("ok")
