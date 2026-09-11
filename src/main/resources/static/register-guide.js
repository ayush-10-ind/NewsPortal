document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const formSection = document.querySelector(".register-page-form-section");
    if (!form || !formSection) return;

    const fields = [
        {
            id: "name",
            focus: "Hey there. Let's create your account.",
            success: "Good. Now your username.",
            error: "Hmm. Let's check that name."
        },
        {
            id: "username",
            focus: "Pick something unique.",
            success: "Nice. Now your email.",
            error: "Hmm. That one's taken. Try another."
        },
        {
            id: "email",
            focus: "Almost there. Add your email.",
            success: "Perfect. You're ready to join.",
            error: "Hmm. That email needs another look."
        }
    ];

    let audioEnabled = false;
    let audioContext = null;
    let lastValidState = {};

    const guide = document.createElement("aside");
    guide.className = "register-guide";
    guide.setAttribute("aria-live", "polite");
    guide.innerHTML = `
        <button class="register-guide-toggle" type="button" aria-label="Turn guide sounds on or off">
            <span class="register-guide-toggle-icon">♪</span>
            <span class="register-guide-toggle-label">SOUND OFF</span>
        </button>
        <div class="register-guide-bubble">Hey there. Let's create your account.</div>
        <div class="register-guide-pointer" aria-hidden="true"></div>
        <div class="register-guide-cat" aria-hidden="true">
            <svg viewBox="0 0 180 170" role="presentation">
                <g class="cat-tail-art">
                    <path d="M132 129 C169 134 168 87 144 86" />
                </g>
                <ellipse class="cat-body-art" cx="91" cy="123" rx="49" ry="35" />
                <path class="cat-ear-art" d="M45 69 L49 20 L82 51 Z" />
                <path class="cat-ear-art" d="M112 51 L143 20 L139 73 Z" />
                <path class="cat-head-art" d="M43 65 C43 38 65 27 92 27 C121 27 141 43 140 70 C139 99 120 111 91 111 C62 111 43 94 43 65 Z" />
                <path class="cat-face-detail" d="M68 73 Q77 66 84 73" />
                <path class="cat-face-detail" d="M100 73 Q109 66 118 73" />
                <ellipse class="cat-eye-art" cx="77" cy="70" rx="4.5" ry="3" />
                <ellipse class="cat-eye-art" cx="109" cy="70" rx="4.5" ry="3" />
                <path class="cat-nose-art" d="M89 80 Q92 78 95 80 Q92 84 89 80 Z" />
                <path class="cat-mouth-art" d="M92 84 Q87 89 83 86 M92 84 Q97 89 101 86" />
                <path class="cat-paw-art" d="M51 128 Q43 139 53 146 Q63 149 69 137" />
            </svg>
        </div>
    `;

    formSection.appendChild(guide);

    const bubble = guide.querySelector(".register-guide-bubble");
    const toggle = guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide.querySelector(".register-guide-toggle-label");

    function getAudioContext() {
        if (!audioContext) {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass) return null;
            audioContext = new AudioContextClass();
        }
        if (audioContext.state === "suspended") {
            audioContext.resume().catch(function () {});
        }
        return audioContext;
    }

    function meow() {
        if (!audioEnabled) return;
        const ctx = getAudioContext();
        if (!ctx) return;

        const now = ctx.currentTime;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        const filter = ctx.createBiquadFilter();

        osc.type = "sine";
        osc.frequency.setValueAtTime(560, now);
        osc.frequency.exponentialRampToValueAtTime(860, now + 0.13);
        osc.frequency.exponentialRampToValueAtTime(500, now + 0.38);

        filter.type = "lowpass";
        filter.frequency.value = 1900;

        gain.gain.setValueAtTime(0.0001, now);
        gain.gain.exponentialRampToValueAtTime(0.045, now + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.43);

        osc.connect(filter);
        filter.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.46);
    }

    function speak(message, playful) {
        bubble.textContent = message;
        guide.classList.remove("guide-happy", "guide-sad", "guide-speaking");
        void guide.offsetWidth;
        guide.classList.add(playful ? "guide-happy" : "guide-speaking");

        if (!audioEnabled || !("speechSynthesis" in window)) {
            if (playful) meow();
            return;
        }

        window.speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(message);
        utterance.rate = 0.88;
        utterance.pitch = 1.08;
        utterance.volume = 0.7;
        utterance.onend = function () {
            guide.classList.remove("guide-speaking");
            if (playful) meow();
        };
        window.speechSynthesis.speak(utterance);
    }

    function setMessage(message, mood, shouldSpeak) {
        bubble.textContent = message;
        guide.classList.remove("guide-happy", "guide-sad", "guide-speaking");
        if (mood === "happy") guide.classList.add("guide-happy");
        if (mood === "sad") guide.classList.add("guide-sad");
        if (shouldSpeak) speak(message, mood === "happy");
    }

    function pointTo(field) {
        if (!field) return;
        const sectionRect = formSection.getBoundingClientRect();
        const fieldRect = field.getBoundingClientRect();
        const targetY = fieldRect.top - sectionRect.top + fieldRect.height / 2;
        const targetX = fieldRect.left - sectionRect.left;
        guide.style.setProperty("--target-x", Math.max(0, targetX - 18) + "px");
        guide.style.setProperty("--target-y", Math.max(0, targetY) + "px");
        guide.classList.add("guide-pointing");
    }

    toggle.addEventListener("click", function () {
        audioEnabled = !audioEnabled;
        toggleLabel.textContent = audioEnabled ? "SOUND ON" : "SOUND OFF";

        if (audioEnabled) {
            getAudioContext();
            setMessage("Hey there. Let's create your account.", "neutral", true);
        } else {
            if ("speechSynthesis" in window) window.speechSynthesis.cancel();
            guide.classList.remove("guide-speaking");
        }
    });

    fields.forEach(function (item) {
        const field = document.getElementById(item.id);
        if (!field) return;
        lastValidState[item.id] = false;

        field.addEventListener("focus", function () {
            pointTo(field);
            setMessage(item.focus, "neutral", audioEnabled);
        });

        field.addEventListener("input", function () {
            const hasValue = field.value.trim().length > 0;
            const valid = hasValue && field.checkValidity();

            if (!hasValue) {
                lastValidState[item.id] = false;
                guide.classList.remove("guide-happy", "guide-sad");
                return;
            }

            if (valid && !lastValidState[item.id]) {
                lastValidState[item.id] = true;
                setMessage(item.success, "happy", audioEnabled);
            } else if (!valid) {
                lastValidState[item.id] = false;
            }
        });

        field.addEventListener("blur", function () {
            if (!field.value.trim()) return;

            if (!field.checkValidity()) {
                setMessage(item.error, "sad", audioEnabled);
            } else {
                setMessage(item.success, "happy", audioEnabled);
            }
        });
    });

    form.addEventListener("submit", function () {
        const valid = fields.every(function (item) {
            const field = document.getElementById(item.id);
            return field && field.value.trim() && field.checkValidity();
        });

        if (valid) {
            setMessage("All set. I'll see you in the newsroom.", "happy", audioEnabled);
        } else {
            setMessage("Almost. Let's finish the details first.", "sad", audioEnabled);
        }
    });
});
