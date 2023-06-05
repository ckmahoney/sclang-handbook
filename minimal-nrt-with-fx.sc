/**
Here we create a "sidechain compressor" effect.

Given two audio files, we are able to have one of the files change its volume in response to the other.
This is an extremely useful and versatile effect: All professional music mastering involves dynamic compression
at many scales.

In contemporary and art music, it is applied lightly. In dance music and popular styles, it is often heavy.

This is a heavy example, for drastic effect. Here we are:

1. Creating a source file of some melodic quality (source)
2. Creating a noise file to compress (side).
- Noise is a good candidate since it has no melodic quality, you can easily hear the changes in volume.
3. Create synth definitions to read audio files and compress dynamically (Compander).
4. Play an instance of only the sidechained audio.
*/

( // Create a melodic file for playback and reference
var synth = SynthDef(\sin,
	{ |out=0, freq=240, sustain=0.05, atk = 0.05, amp = 1, dur = 1|
		var env;

		env = EnvGen.kr(Env.perc, doneAction: 2);
		Out.ar([0,1], FSinOsc.ar(freq, 0, env));
});

// Create a list of note data for the instrument
var events = Array.fill(64, {|i|
	var freq, amp, dur;

	// Random harmonic from Overtone series sliced 1-7
	freq = ((1..7)*100).choose;

	// Stronger on beats 1 and 3; weaker on beats 2 and 4
	amp = if (i.mod(4) == 0, 1, if (i.mod(2) == 0, 0.6, 0.3));

	// play every downbeat; except on 3, when it is a coin toss
	dur = if (i.mod(4) == 2, {if (2.reciprocal.coin, -1, 1)}, 1);
	[freq, amp, dur];
});

// Holds all events for synthesizing the music
var score = Score.new([
	[0.0, ['/d_recv', synth.asBytes]]
]);

// Tracking current location in time
var timestamp = 0;

// Put all the notes in the score
events.do({|data, i|
	var freq, amp, dur;
	freq = data.at(0);
	amp = data.at(1);
	dur = data.at(2);

	if ( dur > 0,
		{
			a = Synth.basicNew(synth.name);
			score.add([timestamp, a.newMsg(args: [freq: freq, amp: amp])]);
			score.add([timestamp + (dur * 0.95), a.freeMsg]);
	});
	timestamp = dur.abs + timestamp;
});

// prepare for recording and record
score.sort;
score.recordNRT(outputFilePath: "the-score.aiff");
)

( // Create a noise file to sidechain against a melodic voice
var noise = SynthDef.new(\noise, {
	Out.ar([0, 1], WhiteNoise.ar);
}).add;

var instance = Synth.basicNew(\noise);

var score = Score.new([
	[0.0, ['/d_recv', noise.asBytes]],
	[0.001, instance.newMsg()],
	[60.0, instance.freeMsg]
]);

score.recordNRT(outputFilePath: "noise.aiff");
)


( // Add the synthdefs to the server

// Reads a file and passes result to provided destination
SynthDef(\buffer, {|input, out|
	Out.ar(out, BufRd.ar(2, input, LFSaw.ar(BufDur.ir(input).reciprocal).range(0, BufFrames.ir(input))));
}).add;

// Takes an internal bus and passes it to an audible public bus
SynthDef(\passthrough, {in = 10, |out = 0|
	var in1 = In.ar(10, 2);
	Out.ar(out, in1!2);
}).add;

SynthDef(\sidechain, {|source, sidechain|
	var in = In.ar(source, 2);
	var side = In.ar(sidechain, 2);
	var sig = Compander.ar(side, in, 0.3, slopeAbove: 1/512, clampTime: 0.001, relaxTime: 2);
	Out.ar([0,1], sig);
}).add;
)



( // reverb channel

var verb = SynthDef(\reverb, {|in, out = 0, fb = 0.15|
	var feedback = HPF.ar(LocalIn.ar(2) * fb, 10000);
	var source = PlayBuf.ar(2, in, BufRateScale.kr(in), doneAction: 2);
	var sig = FreeVerb2.ar(source, source, 1);

	Out.ar(out, sig + feedback);
	LocalOut.ar(sig + feedback);
}).add;

var hpf = SynthDef(\hpf, {|in, out, fb = 0.15|
	var feedback = HPF.ar(LocalIn.ar(2) * fb, 10000);
	var source = PlayBuf.ar(2, in, BufRateScale.kr(in), doneAction: 2);
	var sig = FreeVerb2.ar(source, source, 1);

	Out.ar(0, sig + feedback);
	LocalOut.ar(sig + feedback);
}).add;

Score.recordNRT(
	[
		[0.0, ["/b_allocRead", 10,
			"/home/naltroc/the-score.aiff",
			0, -1]],
		[0.001, ["/d_recv", verb.asBytes]],
		[0.002, [\s_new, \reverb, 1002, 0, 0, \in, 10]],
		[60.0, [\n_free, 1000]],
		[60.0, [\n_free, 1002]],
		[60.01, [\c_set, 0, 0]] // finish
	],
	outputFilePath: "reverb.aiff"
);

)

( // compressor channel; reverb channel stymied

var buffer = SynthDef(\buffer, {|buff, out|
	Out.ar(out, PlayBuf.ar(2, buff, BufRateScale.kr(buff), doneAction: 2));
});

var verb = SynthDef(\reverb, {|buff, out = 0, fb = 0.05, amp = 1|
	var feedback = HPF.ar(LocalIn.ar(2) * fb, 10000);
	var source = PlayBuf.ar(2, buff, BufRateScale.kr(buff), doneAction: 2);
	var sig = amp * FreeVerb2.ar(source, source, 1);

	Out.ar(out, sig + feedback);
	LocalOut.ar(sig + feedback);
});

var hpf = SynthDef(\hpf, {|in, out, q = 0.15, freq = 1000, amp = 1|
	var sig = RHPF.ar(In.ar(2), freq, q);
	Out.ar(out, amp * sig);
});

var compressor = SynthDef(\compressor, {|buff, out, thresh, amount, amp = 1|
	var sig = PlayBuf.ar(2, buff, BufRateScale.kr(buff), doneAction: 2);
	sig = CompanderD.ar(sig, thresh, slopeAbove: amount);
	Out.ar(out, amp * sig);
});

var exciter = SynthDef(\exciter, {|buff, out, thresh, amount, amp = 1|
	var sig = PlayBuf.ar(2, buff, BufRateScale.kr(buff), doneAction: 2);
	sig = CompanderD.ar(sig, thresh, slopeBelow: amount);
	Out.ar(out, amp * sig);
});


var effects = [verb, hpf, compressor, exciter];
var busses = Array.fill(effects.size, { Bus.new });
var units = [
	// [1, \exciter, [\buff, 10, \out, \MAIN, \thresh, 0.05, \amount, 1024]], // rich saturation!
	[1/3, \exciter, [\buff, 10, \out, \MAIN, \thresh, 0.85, \amount, 1/3]], // rich overdrive!

	[1/3, \compressor, [\buff, 10, \out, \MAIN, \thresh, 0.05, \amount, 1/2]], // subtle saturation
	// [1, \compressor, [\buff, 10, \out, \MAIN, \thresh, 0.05, \amount, 32]], // rich distortion
	[1/3, \reverb, [\buff, 10, \out, \MAIN]],
];
var score = Score.new;

var sigs = ["/home/naltroc/the-score.aiff"];

var indexOffset = 10;
var duration = 60;

var ids = units.collect({|fx, i|
	var id = 1000 + i;
	var msg = [\s_new, fx[1], id, 0, 0];
	var args = [\amp, fx[0]] ++ fx[2].collect({|y|
		switch (y,
			\MAIN, 0,
			y)
	});
	msg = msg ++ args;
	score = score.add([0.002, msg.debug("adding fx")]);
	score = score.add([duration + 2, [\n_free, id]]);
	id;
});

var buffers = sigs.collect({|path, i|
	var index = indexOffset + (2*i);
	var msg = [0, [\b_allocRead, index, path, 0, -1]];
	score.add(msg);
	index;
});

effects.do({|synthdef|
	score.add([0.001, [\d_recv, synthdef.asBytes]]);
});


score.add([duration + 2, [\c_set, 0, 0]]);
score.sort;

score.recordNRT(outputFilePath: "compressor-reverb.aiff");

)