document.addEventListener('DOMContentLoaded', () => {
    let audioCtx, sourceNode, analyser, masterGain;
    let enhBox, mixerBox;
    const audioPlayer = new Audio();
    let playlist = []; let currentIndex = 0;
    let currentMode = "Normal";

    const powerSwitch = document.getElementById('powerSwitch');
    const enhToggle = document.getElementById('enhancementToggle');
    const mixerToggle = document.getElementById('mixerToggle');
    const playPauseBtn = document.getElementById('playPauseBtn');
    const mPlay = document.getElementById('mPlay');
    const nowPlayingText = document.getElementById('nowPlaying');
    const mainContainer = document.querySelector('main');
    const fileInput = document.getElementById('musicFile');

    function initAudio() {
        if (audioCtx) return;
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        masterGain = audioCtx.createGain();
        analyser = audioCtx.createAnalyser();
        analyser.fftSize = 256;
        sourceNode = audioCtx.createMediaElementSource(audioPlayer);

        enhBox = createEnhancementBlock();
        mixerBox = createMixerBlock();

        sourceNode.connect(enhBox.input);
        enhBox.output.connect(mixerBox.input);
        mixerBox.output.connect(analyser);
        analyser.connect(masterGain);
        masterGain.connect(audioCtx.destination);

        updateModularState();
        setupVisualizer();
        run8DLoop();
    }

    function createEnhancementBlock() {
        const input = audioCtx.createGain(), output = audioCtx.createGain(), bypass = audioCtx.createGain(), effect = audioCtx.createGain();
        
        // 32D / 8D PRO ENGINE
        const mainPanner = audioCtx.createStereoPanner(); // Background Space
        const vocalPanner = audioCtx.createStereoPanner(); // Moving Vocals
        const filter = audioCtx.createBiquadFilter(); filter.type = "lowshelf"; filter.frequency.value = 110;
        const depthFilter = audioCtx.createBiquadFilter(); depthFilter.type = "lowpass"; depthFilter.frequency.value = 20000;
        const verticalFilter = audioCtx.createBiquadFilter(); verticalFilter.type = "peaking"; verticalFilter.frequency.value = 8000; verticalFilter.gain.value = 0;
        
        const splitter = audioCtx.createChannelSplitter(2), merger = audioCtx.createChannelMerger(2);
        const centerStage = audioCtx.createGain(); // VOCAL EXTRACTION
        const reverb = audioCtx.createDelay(0.1); reverb.delayTime.value = 0.04;
        const revGain = audioCtx.createGain(); revGain.gain.value = 0;
        
        const inv = audioCtx.createGain();
        const dly1 = audioCtx.createDelay(0.3), dly2 = audioCtx.createDelay(0.3);

        input.connect(bypass).connect(output);
        input.connect(depthFilter).connect(verticalFilter).connect(mainPanner).connect(filter).connect(splitter);
        
        // REVERB PATH
        input.connect(reverb).connect(revGain).connect(merger, 0, 0);
        revGain.connect(merger, 0, 1);
        
        // STABLE BACKGROUND STEREO
        splitter.connect(merger, 0, 0); 
        splitter.connect(merger, 1, 1);
        
        // MOVING VOCAL CENTER
        splitter.connect(centerStage, 0); splitter.connect(centerStage, 1);
        centerStage.connect(vocalPanner).connect(merger, 0, 0);
        vocalPanner.connect(merger, 0, 1);
        
        // 3D SPATIAL ENHANCEMENT
        splitter.connect(dly1, 0).connect(dly2).connect(inv).connect(merger, 0, 1);

        merger.connect(effect).connect(output);
        return { input, output, bypass, effect, inv, filter, dly1, dly2, panner: mainPanner, vocalPanner, centerStage, depthFilter, verticalFilter, revGain };
    }

    function createMixerBlock() {
        const input = audioCtx.createGain(), output = audioCtx.createGain(), bypass = audioCtx.createGain();
        const bG = audioCtx.createGain(), vG = audioCtx.createGain(), tG = audioCtx.createGain();
        const bF = createF("lowpass", 190), vF = createF("bandpass", 1300, 1.2), tF = createF("highpass", 5000);
        input.connect(bypass).connect(output);
        input.connect(bF).connect(bG).connect(output); input.connect(vF).connect(vG).connect(output); input.connect(tF).connect(tG).connect(output);
        return { input, output, bypass, bG, vG, tG };
    }

    function createF(type, f, q = 1) {
        const fl = audioCtx.createBiquadFilter(); fl.type = type; fl.frequency.value = f; fl.Q.value = q; return fl;
    }

    function setupKnob(id, min, max, initial, unit = "%", onChange) {
        const knob = document.getElementById(id);
        if(!knob) return;
        const label = knob.querySelector('span');
        const ring = knob.querySelector('.knob-ring');
        let val = initial;

        function update() {
            let display = "";
            if (val <= min) display = "MIN";
            else if (val >= max) display = "MAX";
            else display = Math.round(val) + (unit === "%" ? "%" : "");
            label.textContent = display;
            
            const pc = ((val - min) / (max - min)) * 360;
            ring.style.background = `conic-gradient(var(--accent-blue) ${pc}deg, #222 0deg)`;
            knob.dataset.value = val;
            onChange(val);
        }

        knob.addEventListener('mousedown', (e) => {
            let startY = e.clientY;
            let startVal = val;
            const move = (me) => {
                let diff = (startY - me.clientY) * 0.5;
                val = Math.min(max, Math.max(min, parseFloat(startVal) + diff));
                update();
            };
            const up = () => { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); };
            document.addEventListener('mousemove', move);
            document.addEventListener('mouseup', up);
        });
        update();
    }

    function updateModularState() {
        if (!powerSwitch.checked) { mainContainer.classList.add('disabled-all'); }
        else { mainContainer.classList.remove('disabled-all'); }
        const enhSub = document.getElementById('enhSub');
        enhToggle.checked ? enhSub.classList.remove('disabled') : enhSub.classList.add('disabled');
        const mixSub = document.getElementById('mixSub');
        mixerToggle.checked ? mixSub.classList.remove('disabled') : mixSub.classList.add('disabled');

        if (!audioCtx) return;
        if (audioCtx.state === 'suspended') audioCtx.resume();

        const vKnob = document.getElementById('volKnob');
        const bKnob = document.getElementById('bassKnob');
        const masterVol = vKnob ? vKnob.dataset.value / 100 : 0.7;
        const bS_val = bKnob ? parseFloat(bKnob.dataset.value) : 0;

        if (enhToggle.checked && powerSwitch.checked) {
            enhBox.bypass.gain.setTargetAtTime(0, audioCtx.currentTime, 0.05);
            enhBox.effect.gain.setTargetAtTime(1, audioCtx.currentTime, 0.05);

            // ULTIMATE SPATIAL MAPPING (Scaled by Surround Width Slider)
            const sW = document.getElementById('surroundSlider').value / 100;
            
            if (currentMode === "5.1") { 
                // 5.1: Focused Center + LFE Subwoofer Simulation
                enhBox.inv.gain.setTargetAtTime(-0.8 * sW, audioCtx.currentTime, 0.05); 
                enhBox.centerStage.gain.setTargetAtTime(0.25 * sW, audioCtx.currentTime, 0.05); // Isolated Center
                enhBox.dly1.delayTime.value = 0.022; enhBox.dly2.delayTime.value = 0.01; 
                enhBox.filter.gain.setTargetAtTime(bS_val + 6, audioCtx.currentTime, 0.05);
            } else if (currentMode === "7.1") {
                // 7.1: Cinematic Surround (Front, Side, Rear distribution)
                enhBox.inv.gain.setTargetAtTime(-1.1 * sW, audioCtx.currentTime, 0.05); 
                enhBox.centerStage.gain.setTargetAtTime(0.35 * sW, audioCtx.currentTime, 0.05); // Clear cinematic vocals
                enhBox.revGain.gain.setTargetAtTime(0.2, audioCtx.currentTime, 0.05); // Surround Reverb
                enhBox.dly1.delayTime.value = 0.035; // Side Channel Shift
                enhBox.dly2.delayTime.value = 0.025; // Rear Channel Shift
                enhBox.filter.gain.setTargetAtTime(bS_val + 9, audioCtx.currentTime, 0.05); // Subwoofer LFE
            } else if (currentMode === "32D") {
                // 32D: Extreme Spatial Trajectory + Echo + Ambient Reverb
                enhBox.inv.gain.setTargetAtTime(-1.4 * sW, audioCtx.currentTime, 0.05); 
                enhBox.centerStage.gain.setTargetAtTime(0.15 * sW, audioCtx.currentTime, 0.05); 
                enhBox.revGain.gain.setTargetAtTime(0.4, audioCtx.currentTime, 0.05); // High Ambience
                enhBox.dly1.delayTime.setTargetAtTime(0.04, audioCtx.currentTime, 0.05);
                enhBox.dly2.delayTime.setTargetAtTime(0.035, audioCtx.currentTime, 0.05);
                enhBox.filter.gain.setTargetAtTime(bS_val, audioCtx.currentTime, 0.05);
            } else if (currentMode === "8D") {
                // 8D: Immersive Circular Panning + Reverb
                enhBox.inv.gain.setTargetAtTime(-0.35 * sW, audioCtx.currentTime, 0.05); 
                enhBox.centerStage.gain.setTargetAtTime(0.2, audioCtx.currentTime, 0.05); // KEEP VOCALS CENTER
                enhBox.revGain.gain.setTargetAtTime(0.25, audioCtx.currentTime, 0.05); // Atmospheric Reverb
                enhBox.dly1.delayTime.value = 0.015; enhBox.dly2.delayTime.value = 0;
                enhBox.filter.gain.setTargetAtTime(bS_val, audioCtx.currentTime, 0.05);
            } else { // Normal Mode
                enhBox.inv.gain.value = 0; 
                enhBox.centerStage.gain.value = 0;
                enhBox.revGain.gain.setTargetAtTime(0, audioCtx.currentTime, 0.05);
                enhBox.depthFilter.frequency.setTargetAtTime(20000, audioCtx.currentTime, 0.05);
                enhBox.dly1.delayTime.value = 0; enhBox.dly2.delayTime.value = 0;
                enhBox.filter.gain.setTargetAtTime(bS_val, audioCtx.currentTime, 0.05);
            }
        } else {
            enhBox.bypass.gain.setTargetAtTime(1, audioCtx.currentTime, 0.05);
            enhBox.effect.gain.setTargetAtTime(0, audioCtx.currentTime, 0.05);
            if(enhBox.revGain) enhBox.revGain.gain.value = 0;
        }

        if (mixerToggle.checked && powerSwitch.checked) {
            mixerBox.bypass.gain.setTargetAtTime(0, audioCtx.currentTime, 0.05);
            let bV = document.getElementById('mixBass').value / 100;
            let vV = document.getElementById('mixVoice').value / 100 * 1.6;
            let tV = document.getElementById('mixTimbre').value / 100 * 1.3;
            
            if (currentMode === "8D" || currentMode === "32D") {
                vV *= 1.2; tV *= 1.2;
            }

            mixerBox.bG.gain.setTargetAtTime(bV, audioCtx.currentTime, 0.05);
            mixerBox.vG.gain.setTargetAtTime(vV, audioCtx.currentTime, 0.05);
            mixerBox.tG.gain.setTargetAtTime(tV, audioCtx.currentTime, 0.05);
        } else {
            mixerBox.bypass.gain.setTargetAtTime(1, audioCtx.currentTime, 0.05);
            mixerBox.bG.gain.value = mixerBox.vG.gain.value = mixerBox.tG.gain.value = 0;
        }
        masterGain.gain.value = masterVol;
    }

    function run8DLoop() {
        requestAnimationFrame(run8DLoop);
        if (!audioCtx || !powerSwitch.checked || !enhToggle.checked) return;

        if (currentMode === "8D") {
            // VOCALS MOVE SLOWLY L -> R
            const time = audioCtx.currentTime * 1.2;
            const x = Math.sin(time);
            enhBox.vocalPanner.pan.value = x;
            
            // BACKGROUND STAYS STABLE (Panner at 0)
            enhBox.panner.pan.setTargetAtTime(0, audioCtx.currentTime, 0.2);
            enhBox.depthFilter.frequency.setTargetAtTime(20000, audioCtx.currentTime, 0.2);
        } 
        else if (currentMode === "32D") {
            // VOCALS MOVE IN 3D SPHERE
            const t = audioCtx.currentTime * 1.0;
            const x = Math.sin(t);
            const y = Math.sin(t * 0.7);
            const z = Math.cos(t * 1.1);
            
            enhBox.vocalPanner.pan.value = x;
            enhBox.verticalFilter.gain.value = y * 6;
            
            // BACKGROUND STAYS STABLE
            enhBox.panner.pan.setTargetAtTime(0, audioCtx.currentTime, 0.2);

            if (z < 0) {
                enhBox.depthFilter.frequency.value = 18000 + (z * 14000);
                enhBox.effect.gain.value = 0.75 + (z * 0.25);
            } else {
                enhBox.depthFilter.frequency.value = 20000;
                enhBox.effect.gain.value = 1.0;
            }
        }
        else {
            if(enhBox) {
                enhBox.panner.pan.setTargetAtTime(0, audioCtx.currentTime, 0.2); 
                enhBox.vocalPanner.pan.setTargetAtTime(0, audioCtx.currentTime, 0.2);
                enhBox.depthFilter.frequency.setTargetAtTime(20000, audioCtx.currentTime, 0.2);
                enhBox.verticalFilter.gain.setTargetAtTime(0, audioCtx.currentTime, 0.2);
            }
        }
    }

    function loadTrack(index) {
        if (playlist.length === 0) return;
        const file = playlist[index];
        audioPlayer.src = URL.createObjectURL(file);
        nowPlayingText.textContent = "NOW PLAYING: " + file.name.substring(0, 24).toUpperCase();
        audioPlayer.play(); playPauseBtn.textContent = mPlay.textContent = "⏸";
        document.querySelector('.player-controls').classList.add('playing');
        updateModularState();
        updateStatus("Streaming " + file.name.substring(0, 12));
    }

    fileInput.addEventListener('change', (e) => {
        playlist = Array.from(e.target.files);
        if (playlist.length > 0) { initAudio(); currentIndex = 0; loadTrack(currentIndex);
            powerSwitch.checked = true; enhToggle.checked = true; mixerToggle.checked = true; updateModularState(); }
    });

    [playPauseBtn, mPlay].forEach(btn => btn.addEventListener('click', () => {
        if (!audioCtx) initAudio(); if (audioCtx.state === 'suspended') audioCtx.resume();
        if (audioPlayer.paused) { audioPlayer.play(); playPauseBtn.textContent = mPlay.textContent = "⏸"; document.querySelector('.player-controls').classList.add('playing'); }
        else { audioPlayer.pause(); playPauseBtn.textContent = mPlay.textContent = "▶"; document.querySelector('.player-controls').classList.remove('playing'); }
    }));

    const skNext = () => { currentIndex = (currentIndex + 1) % playlist.length; loadTrack(currentIndex); };
    const skPrev = () => { currentIndex = (currentIndex - 1 + playlist.length) % playlist.length; loadTrack(currentIndex); };
    [document.getElementById('nextBtn'), document.getElementById('mNext')].forEach(b => b.addEventListener('click', skNext));
    [document.getElementById('prevBtn'), document.getElementById('mPrev')].forEach(b => b.addEventListener('click', skPrev));
    [powerSwitch, enhToggle, mixerToggle].forEach(sw => sw.addEventListener('change', updateModularState));
    document.querySelectorAll('input[type=range]').forEach(sl => sl.addEventListener('input', updateModularState));
    document.querySelectorAll('.mode-btn').forEach(btn => { btn.addEventListener('click', () => {
        document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active'); currentMode = btn.dataset.mode; 
        updateModularState();
        updateStatus("Mode: " + currentMode);
    });});

    function updateStatus(text) {
        const st = document.getElementById('statusText');
        const dot = document.getElementById('statusDot');
        if(st) st.textContent = text.toUpperCase();
        if(dot) {
            dot.style.background = powerSwitch.checked ? 'var(--accent-blue)' : '#555';
            dot.style.boxShadow = powerSwitch.checked ? '0 0 10px var(--accent-blue)' : 'none';
        }
    }

    setupKnob('volKnob', 0, 100, 70, "%", updateModularState);
    setupKnob('bassKnob', -15, 30, 0, "dB", updateModularState);

    function setupVisualizer() {
        const canvas = document.querySelector('canvas') || document.createElement('canvas');
        if (!canvas.parentElement) document.querySelector('.app-container').appendChild(canvas);
        const ctx = canvas.getContext('2d'); const data = new Uint8Array(128);
        function draw() {
            requestAnimationFrame(draw); if(analyser) analyser.getByteFrequencyData(data);
            ctx.clearRect(0,0,380,80); if (!powerSwitch.checked) return;
            for(let i=0; i<42; i++) {
                const h = data[i] / 4; ctx.fillStyle = `hsl(${220 - i*2.5}, 100%, 50%)`;
                ctx.fillRect(i*9, 80-h, 7, h);
            }
        }
        draw();
    }
});
