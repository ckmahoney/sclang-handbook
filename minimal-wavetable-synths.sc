(
var clear = Buffer.freeAll;
var saw = {|i|
	var freq = i;
	var amp = 1/(i+1);
	var phase = if (i.even, 0, pi);
	[freq, amp, phase];
};

var tri = {|i|
	var freq = i;
	var amp = if (i.even, 0, (1/(i+1).squared));
	var phase = if (i.even, 0, pi);
	[freq, amp, phase];
};

var sqr = {|i|
	var freq = if (i.even, nil, i);
	var amp = 1/(i+1);
	var phase = 0;
	[freq, amp, phase];
};
// var start = 32;
var start = 0;
var stop = 2.pow(10);
var freqs = (start..stop+start- 1).asInt;

var fill = {|lists|
	var start = lists[0].size.collect( { [] });
	([start] ++ lists).reduce({|args, list|
		list.do({|x, i|
			args[i] = args[i].add(x);
		});
		args;
	});
};
var shapes = (
	tri: fill.value(freqs.collect(tri)),
	saw: fill.value(freqs.collect(saw)),
	sqr: fill.value(freqs.collect(sqr)),
);

var buffs = ();

shapes.keysValuesDo({|shape, sines|
	var buff = Buffer.alloc(Server.default, sines[0].size);
	buff.sine3(*sines);
	buffs.put(shape, buff);
});
h=shapes;
b = buffs;
y = buffs.collect(_.bufnum).minItem;
z = buffs.collect(_.bufnum).maxItem;
)

(
// a static waveform represented using sinefill data
var index = (y..z).choose;
var cps = 1.1;
Ndef(\player, {|freq =500|
	var sig = Osc.ar(index, freq);
	Out.ar(0, sig!2);
}).play;
)


(
// uses a single audio buffer and modulates the phase
// sounds like a moving resonator, like a sitar
var index = (y..z).choose;
~cps = 1.1;
~cpc = 4;
Ndef(\player, {|freq =500|
	var pos = SinOsc.kr(freq/2).range(0,SinOsc.kr(~cps.reciprocal/~cpc).range(pi/2, 2pi));
	var sig = Osc.ar(index, freq, pos);
	Out.ar(0, sig!2);
}).play;
)


(
// fx for the sitar
Ndef(\verb, { FreeVerb2.ar(In.ar(0), In.ar(1), room: 1, damp: 0) }).play;

5.do {|i|
	var name = "delay_" ++ i.asString;

	Ndef(name, {
		var delayTime = (i+1) * (~cps.reciprocal);
		var decayTime = 4 * delayTime;
		AllpassL.ar(In.ar(0, 2), ~cps.reciprocal * ~cpc, delayTime, decayTime)
	}).play;
};
)


(
// A dynamic index in a wavetable
var cps = 1.1;
var cpc = 4;
Ndef(\player, {|freq =500|
	var pos = LFSaw.kr(cps/cpc).range(y, z);
	var sig = VOsc.ar(pos, freq);
	Out.ar(0, sig!2);
}).play;
)

(
// A dynamic index in a wavetable with modulated phase
var cps = 1.1;
var cpc = 4;
Ndef(\player, {|freq =500|
	var pos = LFSaw.kr(cps/cpc).range(y, z);
	var phase = SinOsc.kr(freq/2).range(0,SinOsc.kr(cps/cpc).range(pi/2, 2pi));
	var sig = VOsc.ar(pos, freq, phase);
	Out.ar(0, sig!2);
}).play;
)

(
// the low and high cutoff points of resonance
// are modulated
var cps = 1.1;
var cpc = 1;
var rate = cps/cpc;

var loRate = [1,3,5,7].choose;
var hiRate = [2,4,5,6,7,8].choose;
var offset = 0;

hiRate=hiRate+offset;
loRate=loRate+offset;
Ndef(\player, {|freq = 500|
	var lo = SinOsc.ar(rate * loRate).range(0, pi);
	var hi = SinOsc.ar(rate * hiRate).range(pi, 2pi);

	var pos = LFSaw.kr(cps/cpc).range(y, z);
	var phase = SinOsc.ar(freq/2).range(lo, hi);
	var sig = VOsc.ar(pos, freq, phase);
	Out.ar(0, sig!2);
}).play;
)
