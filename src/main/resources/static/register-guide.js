document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const formSection = document.querySelector(".register-page-form-section");
    if (!form || !formSection) return;

    const guide = formSection.querySelector(".register-guide");
    if (!guide) return;

    const bubble = guide.querySelector(".register-guide-bubble");
    const toggle = guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide.querySelector(".register-guide-toggle-label");

    const fields = [
        { id: "name", focus: "Hey there. Let's create your account.", success: "Good. Now your username.", error: "Hmm. Let's check that name.", state: "initial", nextState: "name" },
        { id: "username", focus: "Pick something unique.", success: "Nice. Now your email.", error: "Hmm. That one needs another look.", state: "name", nextState: "email" },
        { id: "email", focus: "Almost there. Add your email.", success: "Perfect. You're ready to join.", error: "Hmm. That email needs another look.", state: "email", nextState: "ready" }
    ];

    // Optional prerecorded voice pack. Add these files under /static/audio/cat/
    // whenever you want the cat to use real voice performances instead of TTS.
    const VOICE_PACK = {
        intro: "/audio/cat/intro.mp3",
        nameSuccess: "/audio/cat/name-success.mp3",
        usernameSuccess: "/audio/cat/username-success.mp3",
        usernameError: "/audio/cat/username-error.mp3",
        emailIntro: "/audio/cat/email-intro.mp3",
        ready: "/audio/cat/ready.mp3",
        success: "/audio/cat/success.mp3",
        meow: "/audio/cat/meow.mp3"
    };

    let audioEnabled = false;
    let audioContext = null;
    let activeVoice = null;
    let lastValidState = {};
    let currentState = "initial";

    function getAudioContext() {
        if (!audioContext) {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass) return null;
            audioContext = new AudioContextClass();
        }
        if (audioContext.state === "suspended") audioContext.resume().catch(function () {});
        return audioContext;
    }

    function stopVoice() {
        if (activeVoice) {
            activeVoice.pause();
            activeVoice.currentTime = 0;
            activeVoice = null;
        }
        if ("speechSynthesis" in window) window.speechSynthesis.cancel();
    }

    function meow() {
        if (!audioEnabled) return;
        const voice = new Audio(VOICE_PACK.meow);
        activeVoice = voice;
        voice.volume = 0.42;
        voice.play().catch(function () {
            const ctx = getAudioContext();
            if (!ctx) return;
            const now = ctx.currentTime;
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.type = "sine";
            osc.frequency.setValueAtTime(540, now);
            osc.frequency.exponentialRampToValueAtTime(820, now + 0.12);
            osc.frequency.exponentialRampToValueAtTime(490, now + 0.34);
            gain.gain.setValueAtTime(0.0001, now);
            gain.gain.exponentialRampToValueAtTime(0.04, now + 0.03);
            gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.4);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start(now);
            osc.stop(now + 0.42);
        });
    }

    function setState(state) {
        currentState = state;
        ["guide-name", "guide-error", "guide-email", "guide-ready", "guide-success"].forEach(function (className) {
            guide.classList.remove(className);
        });
        if (state !== "initial") guide.classList.add("guide-" + state);
    }

    function pointTo(field) {
        const pointer = guide.querySelector(".register-guide-pointer");
        if (!field || !pointer) return;
        const sectionRect = formSection.getBoundingClientRect();
        const fieldRect = field.getBoundingClientRect();
        const targetX = fieldRect.left - sectionRect.left;
        const targetY = fieldRect.top - sectionRect.top + fieldRect.height / 2;
        const startX = guide.offsetLeft + Math.min(111, guide.offsetWidth * 0.46);
        const startY = guide.offsetTop + Math.min(183, guide.offsetHeight * 0.61);
        const dx = targetX - startX;
        const dy = targetY - startY;
        const distance = Math.max(45, Math.sqrt(dx * dx + dy * dy));
        const angle = Math.atan2(dy, dx) * 180 / Math.PI;
        pointer.style.width = distance + "px";
        pointer.style.transform = "rotate(" + angle + "deg)";
        guide.classList.add("guide-pointing");
    }

    function playVoice(key, fallbackText, playful) {
        if (!audioEnabled) return;
        stopVoice();
        const audio = new Audio(VOICE_PACK[key]);
        activeVoice = audio;
        audio.preload = "auto";
        audio.volume = 0.82;
        audio.addEventListener("ended", function () {
            if (playful) meow();
            guide.classList.remove("guide-speaking");
        }, { once: true });
        audio.addEventListener("error", function () {
            // Voice packs are optional. If a file is missing, fall back to browser speech.
            if (!("speechSynthesis" in window)) {
                if (playful) meow();
                return;
            }
            const utterance = new SpeechSynthesisUtterance(fallbackText);
            utterance.rate = 0.88;
            utterance.pitch = 1.02;
            utterance.volume = 0.72;
            utterance.onend = function () {
                guide.classList.remove("guide-speaking");
                if (playful) meow();
            };
            window.speechSynthesis.speak(utterance);
        }, { once: true });
        audio.play().catch(function () {
            audio.dispatchEvent(new Event("error"));
        });
        guide.classList.add("guide-speaking");
    }

    function say(message, state, voiceKey, playful) {
        bubble.textContent = message;
        setState(state);
        if (!audioEnabled) return;
        playVoice(voiceKey, message, !!playful);
    }

    toggle.addEventListener("click", function () {
        audioEnabled = !audioEnabled;
        toggleLabel.textContent = audioEnabled ? "SOUND ON" : "SOUND OFF";
        if (audioEnabled) {
            getAudioContext();
            say("Hey there. Let's create your account.", "initial", "intro", false);
        } else {
            stopVoice();
            guide.classList.remove("guide-speaking");
        }
    });

    fields.forEach(function (item, index) {
        const field = document.getElementById(item.id);
        if (!field) return;
        lastValidState[item.id] = false;

        field.addEventListener("focus", function () {
            pointTo(field);
            if (item.id === "name") say(item.focus, "initial", "intro", false);
            if (item.id === "username") say(item.focus, "name", "nameSuccess", true);
            if (item.id === "email") say(item.focus, "email", "emailIntro", false);
        });

        field.addEventListener("input", function () {
            const hasValue = field.value.trim().length > 0;
            const valid = hasValue && field.checkValidity();
            if (!hasValue) {
                lastValidState[item.id] = false;
                return;
            }
            if (valid && !lastValidState[item.id]) {
                lastValidState[item.id] = true;
                if (item.id === "name") say(item.success, "name", "nameSuccess", true);
                if (item.id === "username") say(item.success, "email", "usernameSuccess", true);
                if (item.id === "email") say(item.success, "ready", "ready", true);
            } else if (!valid) {
                lastValidState[item.id] = false;
            }
        });

        field.addEventListener("blur", function () {
            if (!field.value.trim()) return;
            if (field.checkValidity()) {
                if (item.id === "name") setState("name");
                if (item.id === "username") setState("email");
                if (item.id === "email") setState("ready");
            } else {
                setState("error");
                if (item.id === "username") say(item.error, "error", "usernameError", false);
                else say(item.error, "error", "usernameError", false);
            }
        });
    });

    form.addEventListener("submit", function (event) {
        const valid = fields.every(function (item) {
            const field = document.getElementById(item.id);
            return field && field.value.trim() && field.checkValidity();
        });
        if (valid) {
            say("All set. I'll see you in the newsroom.", "success", "success", true);
        } else {
            say("Almost. Let's finish the details first.", "error", "usernameError", false);
        }
    });

    window.addEventListener("resize", function () {
        const active = document.activeElement;
        if (active && active.matches("#name, #username, #email")) pointTo(active);
    });
});
