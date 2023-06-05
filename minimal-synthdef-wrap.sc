(
/**
  example of connecting simple, single input components.
*/
~routedFilter = {|name, usrc, umod, type = \lpf, numChannels = 2|
	var types = Dictionary.newFrom([
		\lpf, LPF,
		\hpf, HPF
	]);

	var filter = SVF;

	SynthDef(name, {|outBus|
		var ctl = SynthDef.wrap(umod, nil);
		var snd = SynthDef.wrap(usrc, nil);
		var sig = filter.ar(snd, ctl);

		Out.ar(outBus, Array.fill(numChannels, sig));
	}).add;
};

~modulator = {|ffreq, rate|
	"rate is a :" .postln;
	rate.postln;
	SinOsc.kr(rate).exprange(ffreq, ffreq*2);
};

~sinePlayer = {|freq|
	Pulse.ar(freq);
};

~routedFilter.(\rf2, ~sinePlayer, ~modulator);
)

(
var control = Bus.control;
var out = Bus.new(index: 0);
var send = Bus.audio;

a = Synth.new(\rf2, [
	freq: 300,
	ffreq: 200,
	rate: 3,
]);

)

( // not working example
/**
the goal here is to have the option of making one lfo "over there", a filter "over where", and my synth "right here"
and being able to interchange the parts freely. But when passed into euroCrack and eval'd as a SynthDef, only the default
values were applied. Even changing args to integers and puttings types in lists failed, for good reason:
when the SynthDef is called, the given inputs are immediately applied.

However! If creating your synths on the fly, for example in a Routine or with NRT, you could call up another euroCrack
on demand. Probably.
*/
~euroCrack= {|uSource, uMod, uFilter, numChannels = 2|
	SynthDef(\modSynth, {|out|
		var filter = SynthDef.wrap(uFilter, nil);
		var modulator = SynthDef.wrap(uMod, nil);
		var sound = SynthDef.wrap(uSource, nil);
		var sig = filter.ar(sound, modulator);
		Out.ar(out, Array.fill(numChannels, sig));
	}).add;
};

~filter = {|ftype = \hpf|
	var types = Dictionary.newFrom([
		\hpf, RHPF,
		\lpf, RLPF,
		\bpf, BPF,
		\svf, SVF
	]);
	(types @ ftype) ? AllpassC;
};

~oscillator = {|freq, osc = \sine|
	var types = Dictionary.newFrom([
		\sine, SinOsc,
		\square, Pulse,
		\saw, Saw,
		\triangle, LFTri,
		\noise, WhiteNoise
	]);
	var sig = (types @ osc) ? SinOsc;
	sig.ar(freq);
};

~modulator = {|ffreq, rate = 1|
	Pulse.kr(rate).exprange(ffreq, ffreq*2);
};

~sinePlayer = {|freq|
	Pulse.ar(freq);
};

~euroCrack.(~oscillator, ~modulator, ~filter);
)

(
var control = Bus.control;
var out = Bus.new(index: 0);
var send = Bus.audio;

a = Synth.new(\modSynth, [
	freq: 300,
	ffreq: 200,
	rate: 2,
	osc: \saw,
	numChannels: 2,
	out: out,
]);
)



(
/**
The goal here is similar, but the result is different.
Instead of making a few modular synths, just make all varieties possible.
maybe that's better anyway? so each version you can think of already exists.
And if you want to add another version, give it a name and val for the respective type.

or maybe i'm just rationalizing.

ps
each instance has its own output bus and fx bus.

pps the synths don't free themselves :/ bug todo if i keep this approach
*/

var oscTypes = Dictionary.newFrom([
	"sine", SinOsc,
	"square", Pulse,
	"saw", Saw,
	"triangle", LFTri,
	"noise", WhiteNoise
]);

var filterTypes = Dictionary.newFrom([
	"hpf", RHPF,
	"lpf", RLPF,
	"bpf", BPF,
	"svf", SVF,
	"apf", AllpassC
]);

~busses = [];

~euroCrack = {|name, uSource, numChannels = 2|
	var signal = Bus.new;
	var fx = Bus.new;

	SynthDef("modSynth_" ++ name, {
		var sound = SynthDef.wrap(uSource);
		Out.ar(signal, Array.fill(numChannels, sound));
	}).add;

	[name, signal, fx];
};

oscTypes.keysValuesDo({|oName, osc|
	filterTypes.keysValuesDo({|fName, filter|
		var name = oName;
		var ugen = {|freq, nVoices = 3|
			var sig = 0;

			nVoices.do({|i|
				var df = freq * ((1+i)/100);
				sig = sig + osc.ar(freq + df, mul: nVoices.reciprocal);
			});

			FreeSelf.kr(DetectSilence.ar(sig + Impulse.ar(0)));
			sig;
		};
		~busses = ~busses.add(~euroCrack.(name, ugen));

		name = fName;
		ugen = {|freq, nVoices = 3, ffreq|
			var sig = 0;

			nVoices.do({|i|
				var df = freq * ((1+i)/100);
				sig = sig + osc.ar(freq + df, mul: nVoices.reciprocal);
			});
			sig = filter.ar(sig, ffreq);

			FreeSelf.kr(DetectSilence.ar(sig + Impulse.ar(0)));
			sig;
		};
		~busses = ~busses.add(~euroCrack.(name, ugen));

		name = oName ++ "_" ++ fName;
		ugen = {|freq, nVoices = 3, ffreq|
			var sig = 0;

			nVoices.do({|i|
				var df = freq * ((1+i)/100);
				sig = sig + osc.ar(freq + df, mul: nVoices.reciprocal);
			});
			sig = filter.ar(sig, ffreq);

			FreeSelf.kr(DetectSilence.ar(sig + Impulse.ar(0)));
			sig;
		};
		~busses = ~busses.add(~euroCrack.(name, ugen));
	});
});

SynthDef("send_src", {|channel, mix = 1|
	var sig = In.ar(channel) * mix;

	Out.ar(0, sig!2);
}).add;

SynthDef("send_fx", {|fx, mix = 0.05|
	var sig = In.ar(fx) * mix;
	Out.ar(0, sig!2);
}).add;

"here's " ++ ~busses.size ++ " synthesizers for you. have fun!"
)

(
var getSynthChannels = {|name|
	~busses.select({|list| list.first == name}).first;
};

Pbind(
	\dur, 1/2,
	\freq, Prand((1..8) * 100, inf),
	\ffreq, Prand((1..32) * 50, inf),
	\instrument, Pfunc({
	    ~osc = ["square", "sine", "saw"].choose; ~fil = ["hpf", "lpf"].choose; ~name = ~osc ++ "_" ++ ~fil; "modSynth_" ++ ~name}, inf)
).play;

Pbind(\instrument, "send_src",
	\channel, Pfunc({
		var busses = getSynthChannels.(~name);
		busses @ 1});
).play;
)