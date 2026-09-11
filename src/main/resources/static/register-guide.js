/* AgniPress — real-time 3D newsroom cat
   Built from Three.js geometry so the character has actual depth, lighting,
   shadows and responsive motion instead of a flat CSS illustration. */

document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const guide = document.querySelector(".register-guide");
    const stage = document.querySelector(".cat-stage");
    const canvas = document.getElementById("cat-3d-canvas");
    const bubble = guide && guide.querySelector(".register-guide-bubble");
    const toggle = guide && guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide && guide.querySelector(".register-guide-toggle-label");

    if (!form || !guide || !stage || !canvas || !window.THREE) return;

    const THREE = window.THREE;
    const fields = {
        name: document.getElementById("name"),
        username: document.getElementById("username"),
        email: document.getElementById("email")
    };
    if (!fields.name || !fields.username || !fields.email) return;

    const states = {
        initial: { message: "Hi there. Let's create your account.", pose: "idle" },
        name: { message: "Nice. Now a username.", pose: "point" },
        error: { message: "Hmm... take another look at that.", pose: "unimpressed" },
        email: { message: "Perfect. Now your email.", pose: "email" },
        ready: { message: "All set. Let's do this.", pose: "ready" },
        success: { message: "Good things start with a verified mind.", pose: "success" }
    };

    const taken = new Set([
        "admin", "administrator", "agnipress", "ayush", "editor",
        "editorial", "news", "newsroom", "root", "test", "user"
    ]);
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

    let state = "initial";
    let soundEnabled = false;
    let activeAudio = null;
    let speaking = false;
    let speechTimer = null;

    const scene = new THREE.Scene();
    const camera = new THREE.OrthographicCamera(-2.55, 2.55, 2.05, -2.05, 0.1, 30);
    camera.position.set(0, 1.45, 7.2);
    camera.lookAt(0, 1.05, 0);

    const renderer = new THREE.WebGLRenderer({ canvas: canvas, alpha: true, antialias: true, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.setClearColor(0x000000, 0);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    const hemi = new THREE.HemisphereLight(0xfffbf3, 0x5e5a54, 2.25);
    scene.add(hemi);

    const key = new THREE.DirectionalLight(0xfff5df, 3.4);
    key.position.set(-3.5, 5.5, 5.5);
    key.castShadow = true;
    key.shadow.mapSize.set(1024, 1024);
    key.shadow.camera.left = -3;
    key.shadow.camera.right = 3;
    key.shadow.camera.top = 3;
    key.shadow.camera.bottom = -3;
    scene.add(key);

    const rim = new THREE.DirectionalLight(0xc8d7ee, 1.15);
    rim.position.set(4, 3, -4);
    scene.add(rim);

    const warm = new THREE.PointLight(0xffe5c1, 1.0, 8);
    warm.position.set(0, 2.5, 3);
    scene.add(warm);

    const cat = new THREE.Group();
    cat.position.set(0, -0.52, 0);
    scene.add(cat);

    const body = new THREE.Group();
    const head = new THREE.Group();
    const face = new THREE.Group();
    const leftArm = new THREE.Group();
    const rightArm = new THREE.Group();
    const leftEar = new THREE.Group();
    const rightEar = new THREE.Group();
    const tail = new THREE.Group();
    const props = new THREE.Group();
    cat.add(body, head, leftArm, rightArm, tail, props);
    head.add(face, leftEar, rightEar);

    const fur = new THREE.MeshStandardMaterial({ color: 0x676763, roughness: .92, metalness: .0, flatShading: false });
    const furDark = new THREE.MeshStandardMaterial({ color: 0x4f4e4b, roughness: .96 });
    const furLight = new THREE.MeshStandardMaterial({ color: 0x868580, roughness: .94 });
    const cream = new THREE.MeshStandardMaterial({ color: 0xe9e4db, roughness: .94 });
    const creamDark = new THREE.MeshStandardMaterial({ color: 0xd1cbc1, roughness: .96 });
    const black = new THREE.MeshStandardMaterial({ color: 0x151515, roughness: .52 });
    const pink = new THREE.MeshStandardMaterial({ color: 0xa56d68, roughness: .65 });
    const innerEar = new THREE.MeshStandardMaterial({ color: 0xb98683, roughness: .88 });
    const bookDark = new THREE.MeshStandardMaterial({ color: 0x55514b, roughness: .9 });
    const bookLight = new THREE.MeshStandardMaterial({ color: 0xc8bca9, roughness: .96 });
    const paper = new THREE.MeshStandardMaterial({ color: 0xe8e1d4, roughness: 1 });
    const mugMat = new THREE.MeshStandardMaterial({ color: 0xebe4d6, roughness: .78 });

    function add(geometry, material, parent, position, scale, rotation) {
        const mesh = new THREE.Mesh(geometry, material);
        if (position) mesh.position.set(position[0], position[1], position[2]);
        if (scale) mesh.scale.set(scale[0], scale[1], scale[2]);
        if (rotation) mesh.rotation.set(rotation[0], rotation[1], rotation[2]);
        mesh.castShadow = true;
        mesh.receiveShadow = true;
        (parent || cat).add(mesh);
        return mesh;
    }

    // Grounding props — the reference cat feels like a character sitting in a tiny editorial set.
    const book1 = add(new THREE.BoxGeometry(1.65, .18, 1.12), bookDark, props, [-.02, .04, -.05], [1, 1, 1], [0, 0.02, -.035]);
    const book2 = add(new THREE.BoxGeometry(1.46, .16, 1.0), bookLight, props, [.08, .20, -.01], [1, 1, 1], [0, -.035, .045]);
    add(new THREE.BoxGeometry(1.18, .07, .82), paper, props, [.0, .31, .02], [1, 1, 1], [0, .025, -.03]);

    // Mug + handle: a small visual anchor borrowed from the supplied newsroom-cat reference.
    const mug = add(new THREE.CylinderGeometry(.18, .16, .25, 24), mugMat, props, [.95, .28, .12], [1, 1, 1]);
    add(new THREE.TorusGeometry(.11, .028, 10, 24, Math.PI * 1.75), mugMat, props, [1.12, .29, .12], [1, 1, 1], [Math.PI / 2, 0, 0]);
    add(new THREE.CircleGeometry(.145, 24), bookDark, props, [.95, .41, .12], [1, 1, 1], [-Math.PI / 2, 0, 0]);

    // Seated body: broad haunches, chest and a small visible belly.
    add(new THREE.SphereGeometry(.75, 40, 28), fur, body, [0, .82, 0], [1.02, 1.13, .82]);
    add(new THREE.SphereGeometry(.43, 32, 24), cream, body, [0, .76, .61], [1.05, 1.26, .43]);
    add(new THREE.SphereGeometry(.48, 32, 24), furDark, body, [-.48, .49, .06], [1.0, .86, .88]);
    add(new THREE.SphereGeometry(.48, 32, 24), furDark, body, [.48, .49, .06], [1.0, .86, .88]);

    // Head and cheek structure.
    add(new THREE.SphereGeometry(.73, 44, 32), fur, head, [0, 1.78, .02], [1.10, .96, .92]);
    add(new THREE.SphereGeometry(.34, 30, 22), cream, face, [-.19, 1.60, .66], [1.12, .82, .56]);
    add(new THREE.SphereGeometry(.34, 30, 22), cream, face, [.19, 1.60, .66], [1.12, .82, .56]);
    add(new THREE.SphereGeometry(.11, 22, 16), pink, face, [0, 1.68, 1.02], [1.2, .78, .72]);

    const mouth = new THREE.Mesh(new THREE.SphereGeometry(.11, 22, 16), black);
    mouth.position.set(0, 1.56, 1.015);
    mouth.scale.set(1.35, .26, .72);
    mouth.castShadow = true;
    face.add(mouth);

    function eye(x) {
        const g = new THREE.Group();
        g.position.set(x, 1.92, .69);
        face.add(g);
        add(new THREE.SphereGeometry(.145, 28, 22), cream, g, [0, 0, 0], [1.05, 1.0, .58]);
        const pupil = add(new THREE.SphereGeometry(.072, 24, 18), black, g, [0, 0, .085], [.78, 1.18, .35]);
        return { group: g, pupil: pupil };
    }
    const eyeL = eye(-.285);
    const eyeR = eye(.285);

    function ear(parent, x, side) {
        const g = new THREE.Group();
        g.position.set(x, 2.39, .02);
        parent.add(g);
        const outer = add(new THREE.ConeGeometry(.34, .68, 4), fur, g, [0, 0, 0], [.92, 1, .76], [0, Math.PI / 4, 0]);
        const inner = add(new THREE.ConeGeometry(.22, .45, 4), innerEar, g, [0, -.025, .045], [.88, 1, .72], [0, Math.PI / 4, 0]);
        g.rotation.z = side * -.08;
        return g;
    }
    const earL = ear(leftEar, -.49, -1);
    const earR = ear(rightEar, .49, 1);

    function arm(parent, x) {
        const g = parent;
        const forearm = add(new THREE.CapsuleGeometry(.15, .58, 7, 18), furLight, g, [x, .78, .56], [1, 1, 1]);
        const paw = add(new THREE.SphereGeometry(.20, 28, 20), cream, g, [x, .39, .69], [1.08, .72, 1.2]);
        return { forearm, paw };
    }
    const armL = arm(leftArm, -.63);
    const armR = arm(rightArm, .63);

    // Curved tail made from real 3D segments; the segments are animated as a soft wave.
    const tailSegments = [];
    for (let i = 0; i < 10; i++) {
        const p = i / 9;
        const seg = add(new THREE.SphereGeometry(.16 - p * .045, 22, 18), furDark, tail, [.76 + p * .72, .58 + p * .23, -.08 - p * .10], [1.0, 1.0, 1.0]);
        tailSegments.push(seg);
    }

    // Whiskers are true 3D lines rather than flat CSS strokes.
    function whiskers(side) {
        for (let i = 0; i < 3; i++) {
            const y = 1.57 + (i - 1) * .10;
            const start = new THREE.Vector3(side * .14, y, 1.02);
            const end = new THREE.Vector3(side * (.62 + i * .04), y + (i - 1) * .035, 1.04);
            const geometry = new THREE.BufferGeometry().setFromPoints([start, end]);
            const line = new THREE.Line(geometry, new THREE.LineBasicMaterial({ color: 0x595650, transparent: true, opacity: .6 }));
            face.add(line);
        }
    }
    whiskers(-1);
    whiskers(1);

    // Soft contact shadow keeps the cat visually planted on the books.
    const contact = new THREE.Mesh(new THREE.CircleGeometry(1.18, 48), new THREE.MeshBasicMaterial({ color: 0x292622, transparent: true, opacity: .11, depthWrite: false }));
    contact.rotation.x = -Math.PI / 2;
    contact.position.set(0, -.03, .08);
    contact.scale.set(1.28, .42, 1);
    props.add(contact);

    const pointer = { x: 0, y: 0 };
    let lookX = 0;
    let lookY = 0;
    let last = performance.now();
    let blinkTimer = 2.8;
    let blink = 0;
    let posePulse = 0;

    stage.addEventListener("pointermove", function (event) {
        const r = stage.getBoundingClientRect();
        pointer.x = ((event.clientX - r.left) / r.width - .5) * 2;
        pointer.y = -(((event.clientY - r.top) / r.height - .5) * 2);
    }, { passive: true });
    stage.addEventListener("pointerleave", function () { pointer.x = 0; pointer.y = 0; }, { passive: true });

    function resize() {
        const r = stage.getBoundingClientRect();
        const aspect = Math.max(.5, r.width / Math.max(1, r.height));
        const view = 2.05;
        camera.top = view;
        camera.bottom = -view;
        camera.left = -view * aspect;
        camera.right = view * aspect;
        camera.updateProjectionMatrix();
        renderer.setSize(Math.max(1, r.width), Math.max(1, r.height), false);
    }
    window.addEventListener("resize", resize, { passive: true });
    resize();

    function setState(next, speak) {
        if (!states[next]) return;
        state = next;
        if (bubble) bubble.textContent = states[next].message;
        posePulse = 1;
        if (speak !== false) speakLine(states[next].message, next);
    }

    function stopSpeech() {
        speaking = false;
        if (speechTimer) clearTimeout(speechTimer);
        speechTimer = null;
        if (window.speechSynthesis) window.speechSynthesis.cancel();
        if (activeAudio) {
            try { activeAudio.pause(); activeAudio.currentTime = 0; } catch (e) {}
            activeAudio = null;
        }
    }

    function fallbackVoice(text) {
        if (!soundEnabled || !window.speechSynthesis) { speaking = false; return; }
        const u = new SpeechSynthesisUtterance(text);
        u.rate = .94; u.pitch = 1.0; u.volume = .72;
        u.onend = function () { speaking = false; };
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(u);
        speechTimer = setTimeout(function () { speaking = false; }, Math.max(2200, text.length * 65));
    }

    function speakLine(text, name) {
        if (!soundEnabled) return;
        stopSpeech();
        speaking = true;
        const paths = {
            initial: "/audio/cat/intro.mp3",
            name: "/audio/cat/name-success.mp3",
            error: "/audio/cat/username-error.mp3",
            email: "/audio/cat/email-intro.mp3",
            ready: "/audio/cat/ready.mp3",
            success: "/audio/cat/success.mp3"
        };
        if (!paths[name]) { fallbackVoice(text); return; }
        const player = new Audio(paths[name]);
        activeAudio = player;
        player.onended = function () { speaking = false; };
        player.onerror = function () { fallbackVoice(text); };
        player.play().catch(function () { fallbackVoice(text); });
    }

    function validEmail() { return emailPattern.test(fields.email.value.trim()); }

    function updateFromForm() {
        const name = fields.name.value.trim();
        const username = fields.username.value.trim().toLowerCase();
        const email = fields.email.value.trim();
        if (username && taken.has(username)) { setState("error"); return; }
        if (document.activeElement === fields.email) {
            if (name && username && validEmail()) setState("ready", false);
            else setState("email", false);
            return;
        }
        if (name && !username) { setState("name", false); return; }
        if (name && username && email && validEmail()) { setState("ready", false); return; }
        setState(name ? "name" : "initial", false);
    }

    [fields.name, fields.username, fields.email].forEach(function (field) {
        field.addEventListener("input", updateFromForm);
        field.addEventListener("focus", function () {
            if (field === fields.email) setState("email", false);
            else updateFromForm();
        });
    });

    fields.username.addEventListener("blur", function () {
        const value = fields.username.value.trim().toLowerCase();
        if (value && taken.has(value)) setState("error");
    });

    form.addEventListener("submit", function () {
        const name = fields.name.value.trim();
        const username = fields.username.value.trim().toLowerCase();
        const email = fields.email.value.trim();
        if (!name || !username || !email || !validEmail() || taken.has(username)) {
            setState("error");
            return;
        }
        setState("success");
        if (soundEnabled) speakLine(states.success.message, "success");
    });

    if (toggle) toggle.addEventListener("click", function () {
        soundEnabled = !soundEnabled;
        toggle.classList.toggle("is-on", soundEnabled);
        if (toggleLabel) toggleLabel.textContent = soundEnabled ? "SOUND ON" : "SOUND OFF";
        if (soundEnabled) speakLine(states[state].message, state); else stopSpeech();
    });

    function pose(now, dt) {
        const t = now / 1000;
        const p = states[state].pose;
        lookX += (pointer.x - lookX) * Math.min(1, dt * 5);
        lookY += (pointer.y - lookY) * Math.min(1, dt * 5);

        cat.rotation.y = THREE.MathUtils.lerp(cat.rotation.y, lookX * .10, .06);
        cat.rotation.x = THREE.MathUtils.lerp(cat.rotation.x, -lookY * .045, .05);
        head.rotation.y = THREE.MathUtils.lerp(head.rotation.y, lookX * .13, .075);
        head.rotation.x = THREE.MathUtils.lerp(head.rotation.x, -lookY * .06, .075);
        head.rotation.z = THREE.MathUtils.lerp(head.rotation.z, Math.sin(t * .7) * .018, .06);

        const breathe = 1 + Math.sin(t * 1.55) * .012;
        body.scale.set(1.0, breathe, 1.0);
        cat.position.y = -.52 + Math.sin(t * 1.55) * .009;

        eyeL.pupil.position.x = THREE.MathUtils.lerp(eyeL.pupil.position.x, lookX * .055, .12);
        eyeR.pupil.position.x = THREE.MathUtils.lerp(eyeR.pupil.position.x, lookX * .055, .12);
        eyeL.pupil.position.y = THREE.MathUtils.lerp(eyeL.pupil.position.y, lookY * .035, .12);
        eyeR.pupil.position.y = THREE.MathUtils.lerp(eyeR.pupil.position.y, lookY * .035, .12);

        blinkTimer -= dt;
        if (blinkTimer <= 0) blinkTimer = 2.7 + Math.random() * 3.1;
        const blinkTarget = blinkTimer < .11 ? 1 : 0;
        blink += (blinkTarget - blink) * Math.min(1, dt * 18);
        eyeL.group.scale.y = Math.max(.08, 1 - blink * .92);
        eyeR.group.scale.y = Math.max(.08, 1 - blink * .92);

        let l = -.16, r = .16, tilt = 0;
        if (p === "point") r = -.92;
        if (p === "email") l = .68;
        if (p === "unimpressed") { l = .34; r = -.34; tilt = -.14; }
        if (p === "ready") r = -.38;
        if (p === "success") { l = .25; r = -.58; tilt = Math.sin(t * 1.9) * .035; }

        leftArm.rotation.z = THREE.MathUtils.lerp(leftArm.rotation.z, l, .08);
        rightArm.rotation.z = THREE.MathUtils.lerp(rightArm.rotation.z, r - posePulse * .06, .08);
        head.rotation.z = THREE.MathUtils.lerp(head.rotation.z, tilt + Math.sin(t * .7) * .018, .07);
        posePulse *= Math.pow(.06, dt);

        earL.rotation.z = THREE.MathUtils.lerp(earL.rotation.z, -.08 + (p === "unimpressed" ? -.12 : 0), .08);
        earR.rotation.z = THREE.MathUtils.lerp(earR.rotation.z, .08 + (p === "unimpressed" ? .12 : 0), .08);

        tailSegments.forEach(function (seg, i) {
            const q = i / 9;
            seg.position.x = .76 + q * .78;
            seg.position.y = .58 + q * .25 + Math.sin(t * 1.5 + i * .55) * (.018 + q * .035);
            seg.position.z = -.08 - q * .10 + Math.cos(t * 1.15 + i) * .018;
        });

        const open = speaking ? .55 + Math.max(0, Math.sin(t * 17)) * .45 : 0;
        mouth.scale.y = THREE.MathUtils.lerp(mouth.scale.y, .26 + open * .68, .25);
    }

    function animate(now) {
        const dt = Math.min(.04, (now - last) / 1000);
        last = now;
        pose(now, dt);
        renderer.render(scene, camera);
        requestAnimationFrame(animate);
    }

    setState("initial", false);
    requestAnimationFrame(animate);
});
