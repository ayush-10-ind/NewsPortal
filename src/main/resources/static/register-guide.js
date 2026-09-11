document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const guide = document.querySelector(".register-guide");
    if (!form || !guide) return;

    const bubble = guide.querySelector(".register-guide-bubble");
    const caption = guide.querySelector(".caption-text");
    const toggle = guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide.querySelector(".register-guide-toggle-label");
    const frames = Array.from(guide.querySelectorAll(".cat-frame"));

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
        initial: {
            message: "Hi there. Let's create your account.",
            voice: "/audio/cat/intro.mp3",
            meow: false
        },
        name: {
            message: "Nice. Now a username.",
            voice: "/audio/cat/name-success.mp3",
            meow: true
        },
        error: {
            message: "Hmm... take another look at that.",
            voice: "/audio/cat/username-error.mp3",
            meow: false
        },
        email: {
            message: "Perfect. Now your email.",
            voice: "/audio/cat/email-intro.mp3",
            meow: false
        },
        ready: {
            message: "All set. Let's do this.",
            voice: "/audio/cat/ready.mp3",
            meow: true
        },
        success: {
            message: "Good things start with a verified mind.",
            voice: "/audio/cat/success.mp3",
            meow: true
        }
    };

    let currentState = "initial";
    let soundEnabled = false;
    let activeAudio = null;
    let audioUnlocked = false;
    let breathingTimer = null;
    let breathingToken = 0;
    let lastPlayedState = null;
    let captionTimer = null;
    let submitLocked = false;

    function clearBreathingTimer() {
        if (breathingTimer) {
            window.clearTimeout(breathingTimer);
            breathingTimer = null;
        }
    }

    function restartBreathing() {
        clearBreathingTimer();
        breathingToken += 1;
        const token = breathingToken;

        frames.forEach(function (frame) {
            frame.classList.remove("breathing");
        });

        breathingTimer = window.setTimeout(function () {
            if (token !== breathingToken) return;
            const active = guide.querySelector(".cat-frame.active");
            if (active) active.classList.add("breathing");
        }, 2000);
    }

    function stopAudio() {
        if (activeAudio) {
            activeAudio.pause();
            activeAudio.currentTime = 0;
            activeAudio = null;
        }

        if (window.speechSynthesis) {
            window.speechSynthesis.cancel();
        }
    }

    function fallbackSpeak(text) {
        if (!soundEnabled || !window.speechSynthesis) return;

        window.speechSynthesis.cancel();

        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = 0.84;
        utterance.pitch = 0.72;
        utterance.volume = 0.8;

        window.speechSynthesis.speak(utterance);
    }

    function playMeow() {
        if (!soundEnabled || !audioUnlocked) return;

        const meow = new Audio("/audio/cat/meow.mp3");
        meow.volume = 0.42;
        meow.play().catch(function () {
            // Optional asset: never allow a missing meow to break registration.
        });
    }

    function playVoice(stateName) {
        if (!soundEnabled || !audioUnlocked) return;

        const config = states[stateName];
        if (!config || !config.voice) return;

        stopAudio();

        const audio = new Audio(config.voice);
        activeAudio = audio;
        audio.preload = "auto";
        audio.volume = 0.86;

        audio.addEventListener("ended", function () {
            if (activeAudio === audio) activeAudio = null;
            if (config.meow) window.setTimeout(playMeow, 120);
        }, { once: true });

        audio.addEventListener("error", function () {
            if (activeAudio === audio) activeAudio = null;
            fallbackSpeak(config.message);
            if (config.meow) window.setTimeout(playMeow, 220);
        }, { once: true });

        audio.play().catch(function () {
            if (activeAudio === audio) activeAudio = null;
            fallbackSpeak(config.message);
            if (config.meow) window.setTimeout(playMeow, 220);
        });
    }

    function animateCaption(text) {
        if (!caption) return;

        if (captionTimer) window.clearTimeout(captionTimer);

        caption.classList.remove("caption-visible");
        caption.classList.add("caption-enter");

        captionTimer = window.setTimeout(function () {
            caption.textContent = text;
            caption.classList.remove("caption-enter");
            caption.classList.add("caption-visible");
        }, 100);
    }

    function setState(nextState, options) {
        options = options || {};

        if (!states[nextState]) nextState = "initial";

        const changed = nextState !== currentState;
        currentState = nextState;

        frames.forEach(function (frame) {
            frame.classList.toggle(
                "active",
                frame.dataset.cat === nextState
            );
        });

        const config = states[nextState];

        if (bubble) bubble.textContent = config.message;
        animateCaption(config.message);
        restartBreathing();

        if (changed && options.speak !== false && soundEnabled && audioUnlocked) {
            if (lastPlayedState !== nextState) {
                lastPlayedState = nextState;
                playVoice(nextState);
            }
        }
    }

    function enableAudio() {
        audioUnlocked = true;
    }

    function isNameValid() {
        return fields.name.value.trim().length >= 2;
    }

    function getUsernameStatus() {
        const value = fields.username.value.trim().toLowerCase();

        if (!value) return "empty";
        if (value.length < 3) return "short";
        if (TAKEN_USERNAMES.has(value)) return "taken";

        return "valid";
    }

    function isEmailValid() {
        return EMAIL_PATTERN.test(fields.email.value.trim());
    }

    /* --------------------------------------------------------
       NAME
       -------------------------------------------------------- */

    fields.name.addEventListener("focus", function () {
        enableAudio();

        if (!fields.name.value.trim()) {
            setState("initial");
        }
    });

    fields.name.addEventListener("input", function () {
        enableAudio();

        if (isNameValid()) {
            setState("name");
        } else {
            setState("initial", { speak: false });
        }
    });

    /* --------------------------------------------------------
       USERNAME
       -------------------------------------------------------- */

    fields.username.addEventListener("focus", function () {
        enableAudio();

        const status = getUsernameStatus();

        if (status === "taken") {
            setState("error");
        } else if (isNameValid()) {
            setState("name", { speak: false });
        }
    });

    fields.username.addEventListener("input", function () {
        enableAudio();

        const status = getUsernameStatus();

        if (status === "taken") {
            setState("error");
            return;
        }

        if (status === "valid") {
            setState("email");
            return;
        }

        if (isNameValid()) {
            setState("name", { speak: false });
        } else {
            setState("initial", { speak: false });
        }
    });

    /* --------------------------------------------------------
       EMAIL
       -------------------------------------------------------- */

    fields.email.addEventListener("focus", function () {
        enableAudio();

        if (getUsernameStatus() === "valid") {
            setState("email");
        }
    });

    fields.email.addEventListener("input", function () {
        enableAudio();

        if (getUsernameStatus() === "taken") {
            setState("error");
            return;
        }

        if (getUsernameStatus() === "valid" && isEmailValid()) {
            setState("ready");
        } else if (getUsernameStatus() === "valid") {
            setState("email", { speak: false });
        }
    });

    /* --------------------------------------------------------
       BLUR VALIDATION
       -------------------------------------------------------- */

    fields.name.addEventListener("blur", function () {
        if (fields.name.value.trim() && !isNameValid()) {
            setState("error");
        }
    });

    fields.username.addEventListener("blur", function () {
        const status = getUsernameStatus();

        if (status === "taken") {
            setState("error");
        }
    });

    fields.email.addEventListener("blur", function () {
        if (fields.email.value.trim() && !isEmailValid()) {
            setState("error");
        }
    });

    /* --------------------------------------------------------
       SOUND TOGGLE
       -------------------------------------------------------- */

    toggle.addEventListener("click", function () {
        audioUnlocked = true;
        soundEnabled = !soundEnabled;

        toggleLabel.textContent = soundEnabled
            ? "SOUND ON"
            : "SOUND OFF";

        if (!soundEnabled) {
            stopAudio();
            return;
        }

        /* Play the current state's voice exactly once when sound is enabled. */
        lastPlayedState = currentState;
        playVoice(currentState);
    });

    /* --------------------------------------------------------
       SUBMIT
       -------------------------------------------------------- */

    form.addEventListener("submit", function (event) {
        enableAudio();

        const valid =
            isNameValid() &&
            getUsernameStatus() === "valid" &&
            isEmailValid();

        if (!valid) {
            event.preventDefault();

            if (!isNameValid()) {
                setState("error");
                fields.name.focus();
                return;
            }

            if (getUsernameStatus() !== "valid") {
                setState("error");
                fields.username.focus();
                return;
            }

            setState("error");
            fields.email.focus();
            return;
        }

        if (submitLocked) {
            event.preventDefault();
            return;
        }

        /* Let the success photograph be visible before navigation. */
        event.preventDefault();
        submitLocked = true;
        setState("success");

        const button = form.querySelector(".auth-submit");
        if (button) {
            button.disabled = true;
            button.innerHTML = '<span>CHECKING DETAILS...</span><span>→</span>';
        }

        window.setTimeout(function () {
            form.submit();
        }, 650);
    });

    /* --------------------------------------------------------
       STARTUP
       -------------------------------------------------------- */

    setState("initial", { speak: false });

    /* Preload the six real character photographs. */
    frames.forEach(function (frame) {
        const image = frame.querySelector("img");
        if (!image) return;
        const preload = new Image();
        preload.src = image.src;
    });
});