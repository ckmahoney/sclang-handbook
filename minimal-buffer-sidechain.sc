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


( // Play a sidechained sample given the source and side components
var paths = ["the-score.aiff", "noise.aiff"];
paths.do({|path, i|
	// Uses a callback action to cue and play a buffer
	Buffer.read(path: path, action: {|buff|
		Synth.new(\buffer, [input: buff, out: 10 + (i * 2)]);
	});

	if (i == (paths.size - 1), {
		// initialze the effect when the final element is loaded.
		Synth(\sidechain, [source: 10, sidechain: 12]);

		// uncomment to add the source component to the mix
		// Synth(\passthrough);
	});
});

)


( // Record sidechain effect using Non-Realtime synthesis
// help provided by
// https://github.com/omercho/SC-OC-iMac/blob/7a05d8280dde035b82c12a33a2a049ed6f7aa2c9/quarks.local.projects/AudioFeatures/SoundFileProcessing/00_plot_etc.scd#L74

var buff = SynthDef(\buffered, { | bufnum = 0, out= 14 |
	Out.ar(out, PlayBuf.ar(2, bufnum, BufRateScale.kr(bufnum), doneAction: 2));
}).add;

var side = SynthDef(\sidechain, {|source, chain|
	var in = PlayBuf.ar(2, source, BufRateScale.kr(source), doneAction: 2);
	var side = PlayBuf.ar(2, chain, BufRateScale.kr(chain), doneAction: 2);
	var sig = Compander.ar(side, in, 0.3, slopeAbove: 1/512, clampTime: 0.001, relaxTime: 2);
	Out.ar(0, sig);
}).add;

Score.recordNRT(
	[
		[0.0, ["/b_allocRead", 10,
			"/home/naltroc/the-score.aiff",
			0, -1]],
		[0.0, ["/b_allocRead", 14,
			"/home/naltroc/noise.aiff",
			0, -1]],
		[0.001, ["/d_recv", buff.asBytes]],
		[0.001, ["/d_recv", side.asBytes]],
		[0.001, [\s_new, \buffered, 1000, 0, 0, \bufnum, 10, \out, 12]],
		[0.001, [\s_new, \buffered, 1001, 0, 0, \bufnum, 14, \out, 16]],
		[0.002, [\s_new, \sidechain, 1002, 0, 0, \source, 10, \chain, 14]],
		[60.0, [\n_free, 1000]],
		[60.0, [\n_free, 1001]],
		[60.0, [\n_free, 1002]],
		[60.01, [\c_set, 0, 0]] // finish
	],
	outputFilePath: "sidechain.aiff"
);

)