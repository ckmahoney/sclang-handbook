(
// major scale
var freqs= [ 1.0, 1.0, 1.125, 1.25, 1.3333333333333, 1.5, 1.5, 1.6666666666667, 1.875 ];

// whare are ur snares
var samples = PathName("~/stage/assets/samples/snare").files;

// for Promise-like behavior in supercollider, use a FlowVar
var flows = Array.fill(samples.size, {FlowVar.new});

SynthDef(\sampler, { arg out = 0, bufnum, freq = 1;
	var sig = PlayBuf.ar(2, bufnum, BufRateScale.kr(bufnum) * freq);
	Out.ar( out, sig );

	DetectSilence.ar(sig, doneAction: 2);
}).add;

fork {

	var buffs = samples.collect({|pn, i|
		Buffer.read(s, pn.asAbsolutePath, action: {|bufnum| flows.at(i).value = bufnum })
	});

	flows.do({|f| f.value; }); // force synchronous eval of the flowvars
};

Pbindef(\samp,
	\instrument, \sampler,
	\bufnum, Pfunc({flows.size.rand}),
	\dur, Prand([1,1/2,1/4], inf),
	\monic, Pshuf(freqs, inf)
).play;
s.record;
)
