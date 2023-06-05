/**
Here is a minimal example of writing one oscillator to a single bus
to be read by two different instruments.

In other words, a single global oscillator that can be applied anywhere in the composition.

The fx being applied are High Pass Filter and Low Pass Filter.

*/

(
SynthDef.new(\tri, {|target, rate, min, max|
	Out.kr(target, LFTri.kr(rate).range(min, max).poll);
}).add;

SynthDef.new(\sin, {|target, rate, min, max|
	Out.kr(target, SinOsc.kr(rate).range(min, max).poll);
}).add;

SynthDef.new(\saw, {|target, rate, min, max|
	Out.kr(target, LFSaw.kr(rate).range(min, max).poll);
}).add;

// Do not use any envelope on the synths
// So that the effect is very audible while they sustain

SynthDef.new(\syn1, {|freq, amp, mod, out = 0, nodes = 4, stack = 2|
	var sigs = [], sig = 0;
	stack.do({|i|
		sigs = sigs.add(SinOsc.ar(freq * 2.pow(i), 0, amp));
	});
	sig = Mix.ar(sigs);
	nodes.do({|i|
		sig = HPF.ar(sig, In.kr(mod));
    });
	Out.ar(out, sig);
}).add;


SynthDef.new(\syn2, {|freq, amp, mod, out = 1, nodes = 4, stack = 2|
	var sigs = [], sig = 0;
	stack.do({|i|
		sigs = sigs.add(SinOsc.ar(freq * 2.pow(i), 0, amp));
	});
	sig = Mix.ar(sigs);
	nodes.do({|i|
		sig = LPF.ar(sig, In.kr(mod));
    });
	Out.ar(out, sig);
}).add;

SynthDef.new(\mixer, {|in = 3, out = 0, pan = 0|
	var sig = In.ar(in, 1); // cannot use a control var for nChannels
	Out.ar(out, Pan2.ar(sig, pan));
}).add;

SynthDef.new(\metronome, {|rate, amp = 0.1|
	Out.ar(0, Impulse.ar(rate, 0.0, amp)!2);
}).add;
)

(
var modType = \saw;

// vars for synths
var osc, syn1, syn2, mix;

// metadata usable in a piece of music
var cps = 2.1, nCycles = 4;

// values in Hz
var rate = cps/nCycles, min = 50, max = 15000;
// routing
var bMain = Bus.new(\audio),
    bMod = Bus.control,
    bSyn1 = Bus.audio,
    bSyn2 = Bus.audio;

osc = Synth.new(modType, [\target, bMod, \rate, rate, \min, min, \max, max]);
syn1 = Synth.after(osc, \syn1, [\freq, 200, \amp, 0.1, \mod, bMod, \out, bSyn1]);
syn2 = Synth.after(osc, \syn2, [\freq, 300, \amp, 0.1, \mod, bMod, \out, bSyn2]);
Synth.after(syn1, \mixer, [\in, bSyn1, \out, bMain, \pan, -1]);
Synth.after(syn2, \mixer, [\in, bSyn2, \out, bMain, \pan, 1]);
Synth.new(\metronome, [\rate, cps, \amp, 0.1]);
)
