document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    if (!form) return;

    const fields = [
        {
            id: "name",
            message: "Hey there. Let's create your account.",
            next: "Good. Now your username."
        },
        {
            id: "username",
            message: "First, choose a username.",
            next: "Nice. Now add your email."
        },
        {
            id: "email",
            message: "Almost there. Add your email address.",
            next: "Perfect. You're ready to join AgniPress."
        }
    ];

    let activeIndex = 0;
    let audioEnabled = false;
    let audioContext = null;

    const guide = document.createElement("aside");
    guide.className = "register-guide";
    guide.setAttribute("aria-live", "polite");
    guide.innerHTML = `
        <button class="register-guide-toggle" type="button" aria-label="Turn guide sounds on or off">
            <span class="register-guide-toggle-icon">◖</span>
            <span class="register-guide-toggle-label">SOUND ON</span>
        </button>
        <div class="register-guide-bubble">Hey there. Let's create your account.</div>
        <div class="register-guide-cat" aria-hidden="true">
            <div class="cat-ear cat-ear-left"></div>
            <div class="cat-ear cat-ear-right"></div>
            <div class="cat-head">
                <div class="cat-eye cat-eye-left"></div>
                <div class="cat-eye cat-eye-right"></div>
                <div class="cat-nose"></div>
                <div class="cat-mouth"></div>
            </div>
            <div class="cat-body"></div>
            <div class="cat-tail"></div>
            <div class="cat-paw"></div>
        </div>
    `;

    const formSection = document.querySelector(".register-page-form-section");
    if (formSection) {
        formSection.appendChild(guide);
    } else {
        document.body.appendChild(guide);
    }

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
        const oscillator = ctx.createOscillator();
        const gain = ctx.createGain();
        const filter = ctx.createBiquadFilter();

        oscillator.type = "sine";
        oscillator.frequency.setValueAtTime(620, now);
        oscillator.frequency.exponentialRampToValueAtTime(930, now + 0.12);
        oscillator.frequency.exponentialRampToValueAtTime(520, now + 0.42);

        filter.type = "lowpass";
        filter.frequency.value = 1800;

        gain.gain.setValueAtTime(0.0001, now);
        gain.gain.exponentialRampToValueAtTime(0.055, now + 0.025);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.48);

        oscillator.connect(filter);
        filter.connect(gain);
        gain.connect(ctx.destination);

        oscillator.start(now);
        oscillator.stop(now + 0.5);
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
        utterance.rate = 0.9;
        utterance.pitch = 1.05;
        utterance.volume = 0.72;
        utterance.onstart = function () {
            guide.classList.add("guide-speaking");
        };
        utterance.onend = function () {
            guide.classList.remove("guide-speaking");
            if (playful) meow();
        };

        window.speechSynthesis.speak(utterance);
    }

    function setGuideMessage(message, mood, speakIt) {
        bubble.textContent = message;
        guide.classList.remove("guide-happy", "guide-sad", "guide-speaking");
        if (mood === "happy") guide.classList.add("guide-happy");
        if (mood === "sad") guide.classList.add("guide-sad");

        if (speakIt) {
            speak(message, mood === "happy");
        }
    }

    toggle.addEventListener("click", function () {
        audioEnabled = !audioEnabled;

        if (audioEnabled) {
            getAudioContext();
            toggleLabel.textContent = "SOUND ON";
            speak("Hey there. Let's create your account.", false);
        } else {
            window.speechSynthesis.cancel();
            toggleLabel.textContent = "SOUND OFF";
            guide.classList.remove("guide-speaking");
        }
    });

    fields.forEach(function (item, index) {
        const field = document.getElementById(item.id);
        if (!field) return;

        field.addEventListener("focus", function () {
            activeIndex = index;

            if (index === 0) {
                setGuideMessage(item.message, "neutral", audioEnabled);
            } else {
                setGuideMessage(item.message, "neutral", audioEnabled);
            }
        });

        field.addEventListener("input", function () {
            if (field.value.trim().length === 0) {
                guide.classList.remove("guide-happy", "guide-sad");
                return;
            }

            const valid = field.checkValidity();
            if (valid) {
                setGuideMessage(item.next, "happy", audioEnabled);
            }
        });

        field.addEventListener("blur", function () {
            if (!field.value.trim()) return;

            if (!field.checkValidity()) {
                setGuideMessage(
                    item.id === "username"
                        ? "Hmm. That username needs another look."
                        : item.id === "email"
                            ? "Hmm. That email doesn't look right yet."
                            : "Hmm. Let's check that name.",
                    "sad",
                    audioEnabled
                );
                return;
            }

            if (index < fields.length - 1) {
                window.setTimeout(function () {
                    const nextField = document.getElementById(fields[index + 1].id);
                    if (nextField && document.activeElement !== nextField) {
                        setGuideMessage(item.next, "happy", audioEnabled);
                    }
                }, 180);
            }
        });
    });

    form.addEventListener("submit", function () {
        const allValid = fields.every(function (item) {
            const field = document.getElementById(item.id);
            return field && field.checkValidity() && field.value.trim();
        });

        if (allValid) {
            setGuideMessage("All set. I'll see you in the newsroom.", "happy", audioEnabled);
        } else {
            setGuideMessage("Almost. Let's finish the missing details first.", "sad", audioEnabled);
        }
    });
});
