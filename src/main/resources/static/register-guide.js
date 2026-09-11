document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const form = document.querySelector(".register-form-force");
    const formSection = document.querySelector(".register-page-form-section");
    if (!form || !formSection) return;

    const fields = [
        { id: "name", focus: "Hey there. Let's create your account.", success: "Good. Now your username.", error: "Hmm. Let's check that name." },
        { id: "username", focus: "Pick something unique.", success: "Nice. Now your email.", error: "Hmm. That one's taken. Try another." },
        { id: "email", focus: "Almost there. Add your email.", success: "Perfect. You're ready to join.", error: "Hmm. That email needs another look." }
    ];

    let audioEnabled = false;
    let audioContext = null;
    let lastValidState = {};

    // The cat is already rendered in register.html. Reuse it; never create a duplicate.
    const guide = formSection.querySelector(".register-guide");
    if (!guide) return;

    const bubble = guide.querySelector(".register-guide-bubble");
    const toggle = guide.querySelector(".register-guide-toggle");
    const toggleLabel = guide.querySelector(".register-guide-toggle-label");

    function getAudioContext() {
        if (!audioContext) {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass) return null;
            audioContext = new AudioContextClass();
        }
        if (audioContext.state === "suspended") audioContext.resume().catch(function () {});
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
        const pointer = guide.querySelector(".register-guide-pointer");
        if (!field || !pointer) return;
        const sectionRect = formSection.getBoundingClientRect();
        const fieldRect = field.getBoundingClientRect();
        const targetX = fieldRect.left - sectionRect.left;
        const targetY = fieldRect.top - sectionRect.top + fieldRect.height / 2;
        const startX = guide.offsetLeft + 105;
        const startY = guide.offsetTop + 160;
        const dx = targetX - startX;
        const dy = targetY - startY;
        const distance = Math.max(45, Math.sqrt(dx * dx + dy * dy));
        const angle = Math.atan2(dy, dx) * 180 / Math.PI;
        pointer.style.width = distance + "px";
        pointer.style.transform = "rotate(" + angle + "deg)";
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
            setMessage(field.checkValidity() ? item.success : item.error, field.checkValidity() ? "happy" : "sad", audioEnabled);
        });
    });

    form.addEventListener("submit", function () {
        const valid = fields.every(function (item) {
            const field = document.getElementById(item.id);
            return field && field.value.trim() && field.checkValidity();
        });
        setMessage(valid ? "All set. I'll see you in the newsroom." : "Almost. Let's finish the details first.", valid ? "happy" : "sad", audioEnabled);
    });

    window.addEventListener("resize", function () {
        const active = document.activeElement;
        if (active && active.matches("#name, #username, #email")) pointTo(active);
    });
});
