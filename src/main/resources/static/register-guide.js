document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const section = document.querySelector(".register-page-form-section");
    const guide = document.querySelector(".register-guide");
    if (!form || !section || !guide) return;

    const bubble = guide.querySelector(".register-guide-bubble");
    const toggle = guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide.querySelector(".register-guide-toggle-label");
    const pointer = guide.querySelector(".register-guide-pointer");
    const frames = Array.from(guide.querySelectorAll(".cat-frame"));

    const fields = {
        name: document.getElementById("name"),
        username: document.getElementById("username"),
        email: document.getElementById("email")
    };

    const voicePack = {
        intro: "/audio/cat/intro.mp3",
        nameSuccess: "/audio/cat/name-success.mp3",
        usernameSuccess: "/audio/cat/username-success.mp3",
        usernameError: "/audio/cat/username-error.mp3",
        emailIntro: "/audio/cat/email-intro.mp3",
        ready: "/audio/cat/ready.mp3",
        success: "/audio/cat/success.mp3",
        meow: "/audio/cat/meow.mp3"
    };

    const copy = {
        initial: "Hey there. Let's create your account.",
        name: "Good. Now your username.",
        username: "Nice. Almost there. Add your email.",
        error: "Hmm. Take another look at that.",
        email: "Almost there. Add your email.",
        ready: "Perfect. You're ready to join.",
        success: "All set. I'll see you in the newsroom."
    };

    let soundOn = false;
    let activeAudio = null;
    let state = "initial";
    const spoken = {};

    function stopAudio() {
        if (activeAudio) {
            activeAudio.pause();
            activeAudio.currentTime = 0;
            activeAudio = null;
        }
        if (window.speechSynthesis) window.speechSynthesis.cancel();
    }

    function fallbackSpeak(text) {
        if (!soundOn || !window.speechSynthesis) return;
        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = 0.84;
        utterance.pitch = 0.72;
        utterance.volume = 0.78;
        window.speechSynthesis.speak(utterance);
    }

    function meow() {
        if (!soundOn) return;
        const audio = new Audio(voicePack.meow);
        activeAudio = audio;
        audio.volume = 0.45;
        audio.onerror = function () {
            try {
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                if (!AudioContextClass) return;
                const ctx = new AudioContextClass();
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                const now = ctx.currentTime;
                osc.type = "sine";
                osc.frequency.setValueAtTime(500, now);
                osc.frequency.exponentialRampToValueAtTime(760, now + .13);
                osc.frequency.exponentialRampToValueAtTime(430, now + .34);
                gain.gain.setValueAtTime(.0001, now);
                gain.gain.exponentialRampToValueAtTime(.045, now + .03);
                gain.gain.exponentialRampToValueAtTime(.0001, now + .38);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + .4);
            } catch (e) { /* optional sound */ }
        };
        audio.play().catch(function () {});
    }

    function playVoice(key, text, withMeow) {
        if (!soundOn) return;
        stopAudio();
        const audio = new Audio(voicePack[key]);
        activeAudio = audio;
        audio.preload = "auto";
        audio.volume = .86;
        audio.addEventListener("ended", function () {
            activeAudio = null;
            if (withMeow) window.setTimeout(meow, 90);
        }, { once: true });
        audio.addEventListener("error", function () {
            activeAudio = null;
            fallbackSpeak(text);
            if (withMeow) window.setTimeout(meow, 250);
        }, { once: true });
        audio.play().catch(function () {
            fallbackSpeak(text);
            if (withMeow) window.setTimeout(meow, 250);
        });
    }

    function setState(next, message, voiceKey, speak, withMeow) {
        state = next;
        frames.forEach(function (frame) {
            frame.classList.toggle("active", frame.dataset.cat === next);
        });
        if (message) bubble.textContent = message;
        if (speak && soundOn && voiceKey) playVoice(voiceKey, message, !!withMeow);
        guide.classList.toggle("guide-speaking", !!speak);
        window.setTimeout(function () { guide.classList.remove("guide-speaking"); }, 900);
    }

    function pointTo(field) {
        if (!field || !pointer) return;
        const guideRect = guide.getBoundingClientRect();
        const fieldRect = field.getBoundingClientRect();
        const startX = guideRect.left + guideRect.width * .61;
        const startY = guideRect.top + guideRect.height * .78;
        const endX = fieldRect.left + fieldRect.width * .06;
        const endY = fieldRect.top + fieldRect.height * .5;
        const dx = endX - startX;
        const dy = endY - startY;
        const distance = Math.max(34, Math.sqrt(dx * dx + dy * dy));
        const angle = Math.atan2(dy, dx) * 180 / Math.PI;
        const localX = startX - guideRect.left;
        const localY = startY - guideRect.top;
        pointer.style.left = localX + "px";
        pointer.style.top = localY + "px";
        pointer.style.width = distance + "px";
        pointer.style.transform = "rotate(" + angle + "deg)";
        guide.classList.add("guide-pointing");
    }

    function speakOnce(key, text, voiceKey, withMeow) {
        if (spoken[key]) return;
        spoken[key] = true;
        setState(key, text, voiceKey, true, withMeow);
    }

    toggle.addEventListener("click", function () {
        soundOn = !soundOn;
        toggleLabel.textContent = soundOn ? "SOUND ON" : "SOUND OFF";
        if (soundOn) {
            setState(state, copy[state] || copy.initial, "intro", true, false);
        } else {
            stopAudio();
            guide.classList.remove("guide-speaking");
        }
    });

    fields.name.addEventListener("focus", function () {
        pointTo(fields.name);
        setState("initial", copy.initial, "intro", soundOn && !spoken.intro, false);
        spoken.intro = true;
    });

    fields.name.addEventListener("input", function () {
        if (fields.name.value.trim().length >= 2) {
            setState("name", copy.name, "nameSuccess", soundOn && !spoken.name, true);
            spoken.name = true;
            pointTo(fields.username);
        }
    });

    fields.username.addEventListener("focus", function () {
        pointTo(fields.username);
        setState("name", copy.name, "nameSuccess", soundOn && !spoken.usernameFocus, true);
        spoken.usernameFocus = true;
    });

    fields.username.addEventListener("input", function () {
        if (fields.username.value.trim().length >= 3) {
            setState("email", copy.username, "usernameSuccess", soundOn && !spoken.username, true);
            spoken.username = true;
            pointTo(fields.email);
        }
    });

    fields.email.addEventListener("focus", function () {
        pointTo(fields.email);
        setState("email", copy.email, "emailIntro", soundOn && !spoken.emailFocus, false);
        spoken.emailFocus = true;
    });

    fields.email.addEventListener("input", function () {
        if (fields.email.checkValidity() && fields.email.value.trim()) {
            setState("ready", copy.ready, "ready", soundOn && !spoken.ready, true);
            spoken.ready = true;
            guide.classList.remove("guide-pointing");
        }
    });

    [fields.name, fields.username, fields.email].forEach(function (field) {
        field.addEventListener("blur", function () {
            if (!field.value.trim()) return;
            if (!field.checkValidity()) {
                setState("error", copy.error, "usernameError", soundOn, false);
                guide.classList.remove("guide-pointing");
            }
        });
    });

    form.addEventListener("submit", function (event) {
        const valid = Object.values(fields).every(function (field) {
            return field && field.value.trim() && field.checkValidity();
        });
        if (valid) {
            setState("success", copy.success, "success", soundOn, true);
            guide.classList.remove("guide-pointing");
        } else {
            event.preventDefault();
            setState("error", copy.error, "usernameError", soundOn, false);
            const firstInvalid = Object.values(fields).find(function (field) {
                return !field.value.trim() || !field.checkValidity();
            });
            if (firstInvalid) {
                firstInvalid.focus();
                pointTo(firstInvalid);
            }
        }
    });

    window.addEventListener("resize", function () {
        const active = document.activeElement;
        if (active && (active === fields.name || active === fields.username || active === fields.email)) pointTo(active);
    });

    // Initial nonchalant state. No autoplay; sound starts only after the user opts in.
    setState("initial", copy.initial, null, false, false);
});