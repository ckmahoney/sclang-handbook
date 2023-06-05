(
var synth = SynthDef(\sin,
	{ |out=0, freq=240, sustain=0.05, atk = 0.05, amp = 1, dur = 1|
		var env;

		env = EnvGen.kr(Env.perc, doneAction: 2);
		Out.ar([0,1], FSinOsc.ar(freq, 0, env));
});

var hat = SynthDef(\hat, {|freq, amp, out|
	var sig = HPF.ar(WhiteNoise.ar() * Env.perc.ar(doneAction: 2) * amp, freq * 4);
	Out.ar(out, sig!2);
});


var score = Score.new([
	[0.0, ['/d_recv', synth.asBytes]]
]);

var numHits = 1;
var events = Array.fill(64*numHits, {|i|
	var freq, amp, dur;
	freq = ((1..7)*100).choose;
	amp = if (i.mod(4) == 0, {1}, if (i.mod(2) == 0, {0.6}, {0.3}));
	dur = if (i.mod(4) == 2, {if (2.reciprocal.coin, -1, 1)}, 1);
	[freq, amp, dur/numHits];
});

var timestamp = 0;

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
	("time " ++ timestamp).postln;
});

score.sort;
score.recordNRT(
	outputFilePath: "minimal-nrt-song.aiff",
	sampleFormat: "int16",);

)