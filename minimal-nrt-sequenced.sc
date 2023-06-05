(
// Any audio output will do
var synth = SynthDef(\sin,
	{ |out=0, freq=240, sustain=0.05, atk = 0.05, amp = 1, dur = 1|
		var env;

		env = EnvGen.kr(Env.perc, doneAction: 2);
		Out.ar([0,1], FSinOsc.ar(freq, 0, env));
});

// For loading an audio file to an OSC score
var playBuff = SynthDef(\playback, { | bufnum = 0, out= 14 |
	Out.ar(out, PlayBuf.ar(2, bufnum, BufRateScale.kr(bufnum), doneAction: 2));
});


var root = 200;

// Placeholder for your score writer
var writeScore = {|name, i|
	var data = FlowVar.new;
	var harmonic = i + 1;
	var score = Score.new([
		[0.0, ['/d_recv', synth.asBytes]]
	]);

	var numHits = 4;
	var events = Array.fill(numHits, {|i|
		var freq, amp, dur;
		freq = (harmonic *root);
		amp = if (i.mod(4) == 0, {1}, if (i.mod(2) == 0, {0.6}, {0.3}));
		dur = if (i.mod(4) == 2, {if (2.reciprocal.coin, -1, 1)}, 1);
		[freq, amp, dur/numHits];
	});

	var timestamp = 0;
	var path = "arc-" ++ name ++ ".aiff";

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

	score.sort;
	score.recordNRT(
		outputFilePath: path,
		sampleFormat: "int16",
		action: { data.value = path }
	);

	data;
};

var remove = {|path| ("rm " ++ path.standardizePath).unixCmd; };

// Render a list of audio files as one sequenced audio
var sequence = {|title, paths, cleanup = true|
	var flow = FlowVar.new;
	var score;
	var list = [];

	var getLength = {|path|
		var f = SoundFile.openRead(path);
		var results = f.duration;
		f.close;
		results;
	};

	var totalLength = 0;

	var loadBuffMsg = {|path, index|
		[0.0, ["/b_allocRead", index, path,	0, -1]]
	};

	var playMsg = {|when, index|
		var synth = Synth.basicNew(playBuff.name);
		[when, synth.newMsg(args: [bufnum: index, out: 0])];
	};

	var freeMsg = {|duration, index|
		[duration + 0.001, ["/b_free", index]]
	};

	var finishMsg = {|list, duration|
		list = list.add([duration + 0.001, ["/c_set", 0, 0]]);
		list = list.add([duration + 0.002, ["/b_close", 0]]);
	};

	paths = paths.collect(_.standardizePath);

	list.add([0.001, ["/d_recv", playBuff.asBytes]]);

	paths.do({|path, i|
		var index = i;
		var duration = getLength.(path);
		list = list.add(loadBuffMsg.(path, index));
		list = list.add(playMsg.(0.001 + totalLength, index));
		list = list.add(freeMsg.(duration + totalLength, index));
		totalLength = totalLength + duration;
	});

	list = finishMsg.(list, totalLength);

	score = Score.new(list).sort;

	score.recordNRT(outputFilePath: title, action: {
		if (cleanup, { paths.collect(remove) } );
		flow.value = title.standardizePath;
	}
	);

	flow.value;
};

// Render an audio file for a list of titles
var write = {|paths|
	var retVal = FlowVar.new;
	var outs;

	paths = paths.collect {|p, i| writeScore.value(p, i) };
	fork {
		paths.do {|p, index|
			var name = p.value;
			outs = outs.add(name);
		};
		retVal.value = outs;
	};
	retVal.value;
};

var syncho = {|paths, flowOut|
	var f;
	var result;
	var x = fork {
		{
			result = write.value(paths);
			result.debug("has these paths");
			f = sequence.("sequence.aiff", result);

			"has return value from sequence:".postln;
			f.debug("f");
			flowOut.value = f;


		}.value;
	};
};

var topFlow = FlowVar.new;
var paths = ["a", "b", "c", "d"];

var await = {|fn|
	fork { fn.value };
};

syncho.(paths, topFlow);
// await.({ topFlow.value.debug("program exit"); });
)