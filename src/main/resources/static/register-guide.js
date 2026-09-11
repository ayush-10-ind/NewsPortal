/* AgniPress registration guide — live 3D cat */

document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const guide = document.querySelector(".register-guide");
    const canvas = document.getElementById("cat-3d-canvas");
    if (!form || !guide || !canvas || !window.THREE) return;

    const THREE = window.THREE;
    const bubble = guide.querySelector(".register-guide-bubble");
    const toggle = guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide.querySelector(".register-guide-toggle-label");

    const fields = {
        name: document.getElementById("name"),
        username: document.getElementById("username"),
        email: document.getElementById("email")
    };
    if (!fields.name || !fields.username || !fields.email) return;

    const TAKEN_USERNAMES = new Set([
        "admin", "administrator", "agnipress", "ayush", "editor",
        "editorial", "news", "newsroom", "root", "test", "user"
    ]);
    const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

    const states = {
        initial: { message: "Hi there. Let's create your account.", gesture: "idle" },
        name: { message: "Nice. Now a username.", gesture: "point" },
        error: { message: "Hmm... take another look at that.", gesture: "unimpressed" },
        email: { message: "Perfect. Now your email.", gesture: "email" },
        ready: { message: "All set. Let's do this.", gesture: "ready" },
        success: { message: "Good things start with a verified mind.", gesture: "success" }
    };

    let currentState = "initial";
    let soundEnabled = false;
    let audioUnlocked = false;
    let activeAudio = null;
    let speechActive = false;
    let submitLocked = false;
    let lastPlayedState = null;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(28, 1, 0.1, 30);
    camera.position.set(0, 1.05, 5.4);
    camera.lookAt(0, 0.72, 0);

    const renderer = new THREE.WebGLRenderer({
        canvas,
        antialias: true,
        alpha: true,
        powerPreference: "high-performance"
    });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    renderer.setClearColor(0x000000, 0);

    scene.add(new THREE.HemisphereLight(0xfffbf3, 0x777067, 2.1));
    const key = new THREE.DirectionalLight(0xfff7e9, 3.1);
    key.position.set(-3, 5, 5);
    key.castShadow = true;
    scene.add(key);
    const rim = new THREE.DirectionalLight(0xd7e4ff, 1.3);
    rim.position.set(4, 2.5, -3);
    scene.add(rim);

    const cat = new THREE.Group();
    cat.position.y = -0.58;
    scene.add(cat);

    const bodyGroup = new THREE.Group();
    const headGroup = new THREE.Group();
    const faceGroup = new THREE.Group();
    const leftEar = new THREE.Group();
    const rightEar = new THREE.Group();
    const leftArm = new THREE.Group();
    const rightArm = new THREE.Group();
    const tailGroup = new THREE.Group();
    cat.add(bodyGroup, headGroup, leftArm, rightArm, tailGroup);
    headGroup.add(faceGroup, leftEar, rightEar);

    const fur = new THREE.MeshStandardMaterial({ color: 0x696967, roughness: 0.84, metalness: 0.02 });
    const furLight = new THREE.MeshStandardMaterial({ color: 0x8b8a86, roughness: 0.88 });
    const white = new THREE.MeshStandardMaterial({ color: 0xe7e2d9, roughness: 0.9 });
    const dark = new THREE.MeshStandardMaterial({ color: 0x161514, roughness: 0.48 });
    const noseMat = new THREE.MeshStandardMaterial({ color: 0x9b665e, roughness: 0.58 });
    const innerEarMat = new THREE.MeshStandardMaterial({ color: 0xb47c78, roughness: 0.8 });
    const mouthDark = new THREE.MeshStandardMaterial({ color: 0x251b1b, roughness: 0.8 });

    function mesh(geometry, material, scale, position, parent) {
        const m = new THREE.Mesh(geometry, material);
        if (scale) m.scale.set(scale[0], scale[1], scale[2]);
        if (position) m.position.set(position[0], position[1], position[2]);
        m.castShadow = true;
        m.receiveShadow = true;
        (parent || cat).add(m);
        return m;
    }

    mesh(new THREE.SphereGeometry(0.86, 32, 24), fur, [1.03, 1.12, 0.82], [0, 0.48, 0], bodyGroup);
    mesh(new THREE.SphereGeometry(0.5, 28, 20), white, [0.82, 1.25, 0.35], [0, 0.35, 0.66], bodyGroup);
    mesh(new THREE.SphereGeometry(0.72, 40, 28), fur, [1.12, 0.98, 0.94], [0, 1.43, 0.04], headGroup);
    mesh(new THREE.SphereGeometry(0.38, 28, 20), white, [1.34, 0.66, 0.62], [0, 1.22, 0.69], faceGroup);
    mesh(new THREE.SphereGeometry(0.095, 20, 16), noseMat, [1.12, 0.82, 0.68], [0, 1.30, 1.08], faceGroup);

    const mouth = new THREE.Group();
    mouth.position.set(0, 1.14, 1.08);
    faceGroup.add(mouth);
    const mouthShape = mesh(new THREE.SphereGeometry(0.105, 20, 14), mouthDark, [1.8, 0.28, 0.32], [0, 0, 0], mouth);
    const lowerJaw = mesh(new THREE.SphereGeometry(0.15, 20, 14), white, [1.25, 0.5, 0.7], [0, -0.075, -0.01], mouth);

    function makeEye(x) {
        const eye = new THREE.Group();
        eye.position.set(x, 1.53, 0.72);
        faceGroup.add(eye);
        mesh(new THREE.SphereGeometry(0.115, 24, 18), white, [1, 1, 0.58], [0, 0, 0], eye);
        const pupil = mesh(new THREE.SphereGeometry(0.06, 20, 16), dark, [0.8, 1.15, 0.35], [0, 0, 0.055], eye);
        return { eye, pupil };
    }
    const leftEye = makeEye(-0.285);
    const rightEye = makeEye(0.285);

    function makeEar(parent, x, side) {
        const ear = new THREE.Group();
        ear.position.set(x, 2.08, 0.01);
        ear.rotation.z = side * -0.08;
        parent.add(ear);
        const outer = mesh(new THREE.ConeGeometry(0.31, 0.62, 4), fur, [0.92, 1.02, 0.82], [0, 0, 0], ear);
        outer.rotation.y = Math.PI / 4;
        const inner = mesh(new THREE.ConeGeometry(0.20, 0.43, 4), innerEarMat, [0.88, 1.02, 0.72], [0, -0.025, 0.045], ear);
        inner.rotation.y = Math.PI / 4;
        return ear;
    }
    const earL = makeEar(leftEar, -0.48, -1);
    const earR = makeEar(rightEar, 0.48, 1);

    function makeArm(parent, x) {
        const arm = mesh(new THREE.CapsuleGeometry(0.16, 0.58, 6, 14), furLight, [1, 1, 1], [x, 0.40, 0.55], parent);
        arm.rotation.z = x < 0 ? -0.16 : 0.16;
        const paw = mesh(new THREE.SphereGeometry(0.19, 24, 18), white, [1.1, 0.72, 1.18], [x, 0.08, 0.64], parent);
        return { arm, paw };
    }
    makeArm(leftArm, -0.66);
    makeArm(rightArm, 0.66);

    const tailSegments = [];
    for (let i = 0; i < 8; i++) {
        const s = mesh(new THREE.SphereGeometry(0.17 - i * 0.012, 20, 16), fur, [1.1, 1, 1], [0.72 + i * 0.17, 0.34 + i * 0.03, -0.12 - i * 0.02], tailGroup);
        tailSegments.push(s);
    }

    function addWhiskers(side) {
        for (let i = 0; i < 3; i++) {
            const y = 1.19 + (i - 1) * 0.095;
            const z = 1.08;
            const start = new THREE.Vector3(side * 0.13, y, z);
            const end = new THREE.Vector3(side * (0.62 + i * 0.045), y + (i - 1) * 0.035, z + 0.025);
            const geo = new THREE.BufferGeometry().setFromPoints([start, end]);
            faceGroup.add(new THREE.Line(geo, new THREE.LineBasicMaterial({ color: 0x4b4844, transparent: true, opacity: 0.52 })));
        }
    }
    addWhiskers(-1);
    addWhiskers(1);

    const shadow = new THREE.Mesh(new THREE.CircleGeometry(1.12, 48), new THREE.MeshBasicMaterial({ color: 0x25231f, transparent: true, opacity: 0.10 }));
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.set(0, -0.68, 0.25);
    shadow.scale.set(1.25, 0.48, 1);
    scene.add(shadow);

    const mouse = { x: 0, y: 0, active: false };
    let targetHeadY = 0;
    let targetHeadX = 0;
    let lastTime = performance.now();
    let blinkTimer = 1.8;
    let blinkAmount = 0;
    let talkTimer = 0;
    let talkAmount = 0;
    let gesturePulse = 0;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    window.addEventListener("pointermove", function (event) {
        const rect = canvas.getBoundingClientRect();
        if (!rect.width || !rect.height) return;
        mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        mouse.y = -(((event.clientY - rect.top) / rect.height) * 2 - 1);
        mouse.active = true;
    }, { passive: true });

    function resize() {
        const rect = canvas.getBoundingClientRect();
        const width = Math.max(1, rect.width);
        const height = Math.max(1, rect.height);
        renderer.setSize(width, height, false);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
        camera.lookAt(0, 0.72, 0);
    }
    window.addEventListener("resize", resize, { passive: true });
    resize();

    function statePose(now) {
        const t = now * 0.001;
        const cfg = states[currentState];
        const idle = reducedMotion ? 0 : 1;
        let headTilt = Math.sin(t * 0.75) * 0.012 * idle;
        let bodyY = Math.sin(t * 1.7) * 0.018 * idle;
        let tailWave = Math.sin(t * 1.35) * 0.13 * idle;
        let leftGesture = 0;
        let rightGesture = 0;
        let earDrop = 0;
        if (cfg.gesture === "point") rightGesture = 0.55 + Math.sin(t * 3.1) * 0.035 * idle;
        if (cfg.gesture === "unimpressed") { headTilt = -0.17; earDrop = 0.16; }
        if (cfg.gesture === "email") leftGesture = 0.38 + Math.sin(t * 2.8) * 0.08 * idle;
        if (cfg.gesture === "ready") { headTilt = Math.sin(t * 1.2) * 0.025; rightGesture = 0.12; }
        if (cfg.gesture === "success") { headTilt = Math.sin(t * 2.0) * 0.055; rightGesture = 0.18 + Math.sin(t * 3) * 0.03; }

        bodyGroup.position.y = THREE.MathUtils.lerp(bodyGroup.position.y, bodyY, 0.08);
        headGroup.rotation.z = THREE.MathUtils.lerp(headGroup.rotation.z, headTilt, 0.075);
        headGroup.rotation.y = THREE.MathUtils.lerp(headGroup.rotation.y, targetHeadY, 0.065);
        headGroup.rotation.x = THREE.MathUtils.lerp(headGroup.rotation.x, targetHeadX, 0.065);
        leftArm.rotation.z = THREE.MathUtils.lerp(leftArm.rotation.z, -0.10 - leftGesture, 0.08);
        rightArm.rotation.z = THREE.MathUtils.lerp(rightArm.rotation.z, 0.10 + rightGesture, 0.08);
        leftArm.rotation.x = THREE.MathUtils.lerp(leftArm.rotation.x, leftGesture * 0.22, 0.08);
        rightArm.rotation.x = THREE.MathUtils.lerp(rightArm.rotation.x, -rightGesture * 0.22, 0.08);
        earL.rotation.z = THREE.MathUtils.lerp(earL.rotation.z, -0.08 - earDrop, 0.08);
        earR.rotation.z = THREE.MathUtils.lerp(earR.rotation.z, 0.08 + earDrop, 0.08);

        tailSegments.forEach(function (segment, i) {
            const p = i / (tailSegments.length - 1);
            segment.position.x = 0.72 + p * 1.12;
            segment.position.y = 0.34 + p * 0.22 + Math.sin(t * 1.35 + i * 0.7) * (0.035 + p * 0.05) + tailWave * p;
            segment.position.z = -0.12 - p * 0.14 + Math.cos(t * 1.1 + i) * 0.025;
        });

        gesturePulse *= 0.94;
        if (gesturePulse > 0.01) rightArm.rotation.x -= gesturePulse * 0.18;
    }

    function animateFace(dt) {
        if (!reducedMotion) {
            blinkTimer -= dt;
            if (blinkTimer <= 0) blinkTimer = 2.8 + Math.random() * 3.4;
            const target = blinkTimer < 0.12 ? 1 : 0;
            blinkAmount = THREE.MathUtils.lerp(blinkAmount, target, Math.min(1, dt * 7));
        } else blinkAmount = 0;
        leftEye.eye.scale.y = Math.max(0.12, 1 - blinkAmount * 0.88);
        rightEye.eye.scale.y = Math.max(0.12, 1 - blinkAmount * 0.88);

        if (mouse.active) {
            const px = mouse.x * 0.045;
            const py = mouse.y * 0.035;
            leftEye.pupil.position.x = THREE.MathUtils.lerp(leftEye.pupil.position.x, px, 0.12);
            leftEye.pupil.position.y = THREE.MathUtils.lerp(leftEye.pupil.position.y, py, 0.12);
            rightEye.pupil.position.x = THREE.MathUtils.lerp(rightEye.pupil.position.x, px, 0.12);
            rightEye.pupil.position.y = THREE.MathUtils.lerp(rightEye.pupil.position.y, py, 0.12);
            targetHeadY = THREE.MathUtils.clamp(mouse.x * 0.26, -0.26, 0.26);
            targetHeadX = THREE.MathUtils.clamp(-mouse.y * 0.14, -0.14, 0.14);
        } else {
            targetHeadY = 0;
            targetHeadX = 0;
        }

        if (speechActive) {
            talkTimer -= dt;
            if (talkTimer <= 0) {
                talkTimer = 0.055 + Math.random() * 0.10;
                talkAmount = Math.random() > 0.22 ? 0.72 + Math.random() * 0.28 : 0.08;
            }
        } else talkAmount = THREE.MathUtils.lerp(talkAmount, 0, Math.min(1, dt * 10));
        mouthShape.scale.y = 0.28 + talkAmount * 1.85;
        mouthShape.scale.x = 1.8 + talkAmount * 0.22;
        lowerJaw.position.y = -0.075 - talkAmount * 0.028;
    }

    function render(now) {
        const dt = Math.min(0.05, (now - lastTime) / 1000);
        lastTime = now;
        statePose(now);
        animateFace(dt);
        cat.rotation.y = THREE.MathUtils.lerp(cat.rotation.y, mouse.active ? mouse.x * 0.06 : 0, 0.04);
        renderer.render(scene, camera);
        requestAnimationFrame(render);
    }
    requestAnimationFrame(render);

    function stopAudio() {
        if (activeAudio) {
            activeAudio.pause();
            activeAudio.currentTime = 0;
            activeAudio = null;
        }
        speechActive = false;
        if (window.speechSynthesis) window.speechSynthesis.cancel();
    }

    function fallbackSpeak(text) {
        if (!soundEnabled || !window.speechSynthesis) return;
        stopAudio();
        speechActive = true;
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = 0.86;
        utterance.pitch = 0.72;
        utterance.volume = 0.8;
        utterance.onend = function () { speechActive = false; };
        utterance.onerror = function () { speechActive = false; };
        window.speechSynthesis.speak(utterance);
    }

    function playMeow() {
        if (!soundEnabled || !audioUnlocked) return;
        const meow = new Audio("/audio/cat/meow.mp3");
        meow.volume = 0.36;
        meow.play().catch(function () {});
    }

    function playVoice(stateName) {
        if (!soundEnabled || !audioUnlocked) return;
        const config = states[stateName];
        const path = "/audio/cat/" + ({ initial: "intro", name: "name-success", error: "username-error", email: "email-intro", ready: "ready", success: "success" }[stateName] || "") + ".mp3";
        if (!config || path.endsWith("/.mp3")) return;
        stopAudio();
        const audio = new Audio(path);
        activeAudio = audio;
        audio.preload = "auto";
        audio.volume = 0.84;
        speechActive = true;
        audio.addEventListener("ended", function () {
            speechActive = false;
            if (activeAudio === audio) activeAudio = null;
            if (stateName === "name" || stateName === "ready" || stateName === "success") window.setTimeout(playMeow, 130);
        }, { once: true });
        audio.addEventListener("error", function () {
            speechActive = false;
            if (activeAudio === audio) activeAudio = null;
            fallbackSpeak(config.message);
        }, { once: true });
        audio.play().catch(function () {
            speechActive = false;
            if (activeAudio === audio) activeAudio = null;
            fallbackSpeak(config.message);
        });
    }

    function setState(nextState, options) {
        options = options || {};
        if (!states[nextState]) nextState = "initial";
        const changed = nextState !== currentState;
        currentState = nextState;
        if (bubble) bubble.textContent = states[nextState].message;
        if (changed) {
            gesturePulse = 1;
            if (options.speak !== false && soundEnabled && audioUnlocked && lastPlayedState !== nextState) {
                lastPlayedState = nextState;
                playVoice(nextState);
            }
        }
    }

    function enableAudio() { audioUnlocked = true; }
    function isNameValid() { return fields.name.value.trim().length >= 2; }
    function getUsernameStatus() {
        const value = fields.username.value.trim().toLowerCase();
        if (!value) return "empty";
        if (value.length < 3) return "short";
        if (TAKEN_USERNAMES.has(value)) return "taken";
        return "valid";
    }
    function isEmailValid() { return EMAIL_PATTERN.test(fields.email.value.trim()); }

    fields.name.addEventListener("focus", function () { enableAudio(); if (!fields.name.value.trim()) setState("initial"); });
    fields.name.addEventListener("input", function () { enableAudio(); setState(isNameValid() ? "name" : "initial", { speak: false }); });
    fields.username.addEventListener("focus", function () { enableAudio(); const status = getUsernameStatus(); if (status === "taken") setState("error"); else if (isNameValid()) setState("name", { speak: false }); });
    fields.username.addEventListener("input", function () { enableAudio(); const status = getUsernameStatus(); if (status === "taken") { setState("error"); return; } if (status === "valid") { setState("email"); return; } setState(isNameValid() ? "name" : "initial", { speak: false }); });
    fields.email.addEventListener("focus", function () { enableAudio(); if (getUsernameStatus() === "valid") setState("email"); });
    fields.email.addEventListener("input", function () { enableAudio(); if (getUsernameStatus() === "taken") { setState("error"); return; } if (getUsernameStatus() === "valid" && isEmailValid()) setState("ready"); else if (getUsernameStatus() === "valid") setState("email", { speak: false }); });
    fields.name.addEventListener("blur", function () { if (fields.name.value.trim() && !isNameValid()) setState("error"); });
    fields.username.addEventListener("blur", function () { if (getUsernameStatus() === "taken") setState("error"); });
    fields.email.addEventListener("blur", function () { if (fields.email.value.trim() && !isEmailValid()) setState("error"); });

    toggle.addEventListener("click", function () {
        audioUnlocked = true;
        soundEnabled = !soundEnabled;
        toggleLabel.textContent = soundEnabled ? "SOUND ON" : "SOUND OFF";
        if (!soundEnabled) stopAudio();
        else { lastPlayedState = currentState; playVoice(currentState); }
    });

    form.addEventListener("submit", function (event) {
        enableAudio();
        const valid = isNameValid() && getUsernameStatus() === "valid" && isEmailValid();
        if (!valid) {
            event.preventDefault();
            if (!isNameValid()) { setState("error"); fields.name.focus(); return; }
            if (getUsernameStatus() !== "valid") { setState("error"); fields.username.focus(); return; }
            setState("error"); fields.email.focus(); return;
        }
        if (submitLocked) { event.preventDefault(); return; }
        event.preventDefault();
        submitLocked = true;
        setState("success");
        const button = form.querySelector(".auth-submit");
        if (button) { button.disabled = true; button.innerHTML = '<span>CHECKING DETAILS...</span><span>→</span>'; }
        window.setTimeout(function () { form.submit(); }, 1000);
    });

    setState("initial", { speak: false });
});