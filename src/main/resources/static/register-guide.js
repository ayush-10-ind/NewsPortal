/* AgniPress registration guide — self-contained responsive 3D cat */
document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const guide = document.querySelector(".register-guide");
    const stage = document.querySelector(".cat-stage");
    const bubble = guide && guide.querySelector(".register-guide-bubble");
    const toggle = guide && guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide && guide.querySelector(".register-guide-toggle-label");
    const canvas = document.getElementById("cat-3d-canvas");
    if (!form || !guide || !stage) return;
    if (canvas) canvas.style.display = "none";

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
    let audio = null;
    let speaking = false;
    let speechTimer = null;
    let targetX = 0;
    let targetY = 0;
    let catX = 0;
    let catY = 0;
    let poseImpulse = 0;

    function el(tag, className, parent) {
        const node = document.createElement(tag);
        node.className = className;
        if (parent) parent.appendChild(node);
        return node;
    }

    function buildCat() {
        const old = stage.querySelector(".cat-3d");
        if (old) old.remove();

        const cat = el("div", "cat-3d", stage);
        cat.setAttribute("role", "img");
        cat.setAttribute("aria-label", "Animated AgniPress newsroom cat");

        const shadow = el("div", "cat-floor-shadow", cat);
        const tail = el("div", "cat-tail", cat);
        el("div", "cat-tail-tip", tail);

        const body = el("div", "cat-body", cat);
        el("div", "cat-belly", body);

        const leftArm = el("div", "cat-arm left", cat);
        el("div", "cat-paw", leftArm);
        const rightArm = el("div", "cat-arm right", cat);
        el("div", "cat-paw", rightArm);

        const head = el("div", "cat-head", cat);
        const earL = el("div", "cat-ear left", head);
        const earR = el("div", "cat-ear right", head);

        const eyeL = el("div", "cat-eye left", head);
        const pupilL = el("div", "cat-pupil", eyeL);
        const eyeR = el("div", "cat-eye right", head);
        const pupilR = el("div", "cat-pupil", eyeR);

        const muzzle = el("div", "cat-muzzle", head);
        el("div", "cat-nose", muzzle);
        el("div", "cat-mouth", muzzle);
        el("div", "cat-whiskers left", head);
        el("div", "cat-whiskers right", head);

        return { cat, body, head, earL, earR, leftArm, rightArm, tail, eyeL, eyeR, pupilL, pupilR, mouth: muzzle.querySelector(".cat-mouth"), shadow };
    }

    const cat = buildCat();

    function setState(next, speak) {
        if (!states[next]) return;
        state = next;
        if (bubble) bubble.textContent = states[next].message;
        poseImpulse = 1;
        if (speak !== false) speakLine(states[next].message, next);
    }

    function stopSpeech() {
        speaking = false;
        if (speechTimer) clearTimeout(speechTimer);
        speechTimer = null;
        if (window.speechSynthesis) window.speechSynthesis.cancel();
        if (audio) {
            try { audio.pause(); audio.currentTime = 0; } catch (e) {}
            audio = null;
        }
    }

    function speakLine(text, stateName) {
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
        const src = paths[stateName];
        if (src) {
            const player = new Audio(src);
            audio = player;
            player.onended = function () { speaking = false; };
            player.onerror = function () { fallbackVoice(text); };
            player.play().catch(function () { fallbackVoice(text); });
        } else {
            fallbackVoice(text);
        }
    }

    function fallbackVoice(text) {
        if (!soundEnabled || !window.speechSynthesis) {
            speaking = false;
            return;
        }
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = 0.94;
        utterance.pitch = 1.02;
        utterance.volume = 0.72;
        utterance.onend = function () { speaking = false; };
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(utterance);
        speechTimer = setTimeout(function () { speaking = false; }, Math.max(2200, text.length * 65));
    }

    function validEmail() {
        return emailPattern.test(fields.email.value.trim());
    }

    function updateFromForm() {
        const name = fields.name.value.trim();
        const username = fields.username.value.trim().toLowerCase();
        const email = fields.email.value.trim();

        if (username && taken.has(username)) {
            setState("error");
            return;
        }
        if (document.activeElement === fields.email) {
            if (validEmail() && name && username) setState("ready", false);
            else setState("email", false);
            return;
        }
        if (name && !username) {
            setState("name", false);
            return;
        }
        if (name && username && email && validEmail()) {
            setState("ready", false);
            return;
        }
        if (name) setState("name", false);
        else setState("initial", false);
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

    form.addEventListener("submit", function (event) {
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

    if (toggle) {
        toggle.addEventListener("click", function () {
            soundEnabled = !soundEnabled;
            toggle.classList.toggle("is-on", soundEnabled);
            if (toggleLabel) toggleLabel.textContent = soundEnabled ? "SOUND ON" : "SOUND OFF";
            if (soundEnabled) speakLine(states[state].message, state);
            else stopSpeech();
        });
    }

    stage.addEventListener("pointermove", function (event) {
        const rect = stage.getBoundingClientRect();
        targetX = ((event.clientX - rect.left) / rect.width - 0.5) * 2;
        targetY = ((event.clientY - rect.top) / rect.height - 0.5) * -2;
    }, { passive: true });

    stage.addEventListener("pointerleave", function () {
        targetX = 0;
        targetY = 0;
    });

    let last = performance.now();
    function animate(now) {
        const dt = Math.min(0.04, (now - last) / 1000);
        last = now;
        const t = now / 1000;
        const pose = states[state].pose;

        catX += (targetX - catX) * Math.min(1, dt * 5);
        catY += (targetY - catY) * Math.min(1, dt * 5);

        const breathing = Math.sin(t * 1.65) * 1.6;
        const bob = Math.sin(t * 2.0) * 1.4;
        const headTilt = Math.sin(t * .72) * .7;
        const lookX = catX * 5.5;
        const lookY = catY * 3.2;

        cat.cat.style.transform = `translateX(-50%) translateY(${bob + breathing * .15}px) rotateX(${catY * 2.2}deg) rotateY(${catX * 7}deg)`;
        cat.head.style.transform = `translateZ(18px) rotateZ(${headTilt + catX * 2.2}deg) rotateX(${catY * 1.5}deg)`;
        cat.body.style.transform = `translateZ(2px) scale(${1 + Math.sin(t * 1.65) * .006}, ${1 + Math.sin(t * 1.65) * .009})`;

        cat.pupilL.style.transform = `translate(${lookX}px, ${-lookY}px)`;
        cat.pupilR.style.transform = `translate(${lookX}px, ${-lookY}px)`;

        const blink = Math.sin(t * .33) > .995 ? .14 : 1;
        cat.eyeL.style.transform = `translateZ(25px) scaleY(${blink})`;
        cat.eyeR.style.transform = `translateZ(25px) scaleY(${blink})`;

        const tailWave = Math.sin(t * 1.7) * 4.5;
        cat.tail.style.transform = `rotate(${24 + tailWave}deg) translateZ(-4px)`;
        cat.earL.style.transform = `rotate(${-10 - Math.sin(t * 1.1) * 1.5}deg) translateZ(3px)`;
        cat.earR.style.transform = `rotate(${10 + Math.sin(t * 1.1 + .5) * 1.5}deg) translateZ(3px)`;

        let left = 14, right = -14;
        if (pose === "point") right = -52 + Math.sin(t * 2.6) * 3;
        if (pose === "email") left = 42 + Math.sin(t * 2.7) * 5;
        if (pose === "unimpressed") { left = 25; right = -25; }
        if (pose === "ready") right = -28;
        if (pose === "success") { left = 22; right = -34 + Math.sin(t * 3) * 4; }

        cat.leftArm.style.transform = `rotate(${left}deg) translateZ(10px)`;
        cat.rightArm.style.transform = `rotate(${right - poseImpulse * 5}deg) translateZ(10px)`;
        poseImpulse *= Math.pow(.05, dt);

        const mouthOpen = speaking ? (Math.sin(t * 18) > 0 ? 1 : .25) : 0;
        cat.mouth.style.height = `${9 + mouthOpen * 12}px`;
        cat.mouth.style.borderRadius = mouthOpen ? "50%" : "0 0 50% 50%";
        cat.mouth.style.background = mouthOpen ? "#2a2020" : "transparent";

        cat.shadow.style.transform = `translateZ(-8px) scale(${1 + Math.sin(t * 1.65) * .025}, .48)`;
        requestAnimationFrame(animate);
    }
    requestAnimationFrame(animate);

    setState("initial", false);
});
