(
SynthDef(\rings, {|in, out = 0, rate = 1|
	var freq =300, ffilter;
	var mod = freq * Saw.kr(rate, 2).abs;
	ffilter = Ringz.ar(In.ar(in), mod, 32);
	mod.poll;
	Out.ar(out, ffilter!2);
}).add;


SynthDef(\svf, {|freq, cps, in, out = 0|
	var sig = In.ar(in);
	var mod = 4 * freq * SinOsc.kr(cps, mul: 2).abs;
	var filter = SVF.ar(sig,
		cutoff: mod,
		// res: Saw.kr(cps/2).range(0.01, 1.1),
		lowpass: SinOsc.kr(cps).range(0.1, 1),
		// bandpass: 0.8,
		// highpass: 0.8,
		notch: 0.5,
		peak: 0.0,
		mul: 1.0,
		add: 0.0
	);
	filter.poll;

	Out.ar(out, filter!2);
}).add;

SynthDef(\synth, {|freq, ffilter, out|
	var carrier = Pulse.kr(0.5);
	var mod = SinOsc.ar(0.25);
	var sig = SinOsc.ar(freq, mul: 0.1);
	Out.ar(out, sig!2);
}).add;
)

(

var cps = 2;
var freq = 300;

var synth1 = Bus.audio;
var synth2 = Bus.audio;

var source1 = Synth.new(\synth, [freq: freq, out: synth1]);
var source2 = Synth.new(\synth, [freq: freq, out: synth2]);
//
// Synth.after(source1, \rings, [in: synth1, out: #[0,1], rate: cps/4]);
Synth.after(source1, \svf, [in: synth1, out: #[0,1], rate: cps]);

)


(
SynthDef(\svf_, {| freq = 110, cps = 1, low=0.1, band=0.0, high=0.1, notch=0.0, peak=0.0|
	// var sig = LFSaw.ar(LFSaw.kr(2).range(freq, freq*7/22));
	var sig = Pulse.ar(LFSaw.kr(2).range(freq, freq*7/22));
	var fMod = SinOsc.kr(cps/8).range(freq / 2, freq * 4).abs;
	var qMod= SinOsc.kr(cps/4).range(Saw.kr(cps/16).range(0.05, 0.3), 0.8).abs;
	var hMod = Pulse.kr(cps/4, mul: SinOsc.kr(cps/2).range(high, high *4));

	sig = SVF.ar(
		sig,
		fMod,
		qMod,
		low, band, hMod, notch, peak);
	Out.ar(0, sig ! 2);

}).add;
)
s.record;

(
Pbind(\instrument, \svf_,
	\freq, Pseq([-1, 0, 2, 1, 3, 2, 4, 3].collect((3/2).pow(_) * 400), inf),
	\dur, 8,
	\cps, Prand((1..4) ++ (1..4).reciprocal, inf)
).play;

)