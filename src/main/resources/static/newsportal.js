document.addEventListener("DOMContentLoaded", function () {

    "use strict";

    function loadHomepagePolish() {
        if (!document.querySelector(".home-hero")) return;
        if (document.querySelector('link[data-home-polish="true"]')) return;

        const stylesheet = document.createElement("link");
        stylesheet.rel = "stylesheet";
        stylesheet.href = "/home-polish.css";
        stylesheet.dataset.homePolish = "true";
        document.head.appendChild(stylesheet);

        document.title = "AgniPress — Latest News";

        document.querySelectorAll(".home-hero-topline span:first-child").forEach(function (element) {
            element.textContent = "AGNIPRESS";
        });

        document.querySelectorAll(".home-featured-fallback").forEach(function (element) {
            element.textContent = "AGNIPRESS";
        });

        document.querySelectorAll(".footer-brand").forEach(function (element) {
            element.textContent = "AGNIPRESS";
        });

        document.querySelectorAll(".footer-bottom span:first-child").forEach(function (element) {
            element.textContent = "© 2026 AgniPress";
        });
    }

    function loadBreakingTickerStyles() {
        if (!document.querySelector(".breaking-ticker")) return;
        if (document.querySelector('link[data-breaking-home="true"]')) return;

        const stylesheet = document.createElement("link");
        stylesheet.rel = "stylesheet";
        stylesheet.href = "/breaking-home.css";
        stylesheet.dataset.breakingHome = "true";
        document.head.appendChild(stylesheet);
    }

    function applyUserAvatars() {
        const palette = [
            "#0d9488",
            "#f4511e",
            "#2563eb",
            "#7c3aed",
            "#d9465f",
            "#15803d",
            "#b45309"
        ];

        document.querySelectorAll(".editorial-nav .user-initial").forEach(function (avatar) {
            const identity = avatar.textContent.trim();
            if (!identity) return;

            const firstLetter = identity.replace(/[^A-Za-z0-9]/g, "").charAt(0).toUpperCase();
            avatar.textContent = firstLetter || "U";

            let hash = 0;
            for (let i = 0; i < identity.length; i++) {
                hash = ((hash << 5) - hash) + identity.charCodeAt(i);
                hash |= 0;
            }

            const color = palette[Math.abs(hash) % palette.length];

            avatar.style.setProperty("background", color, "important");
            avatar.style.setProperty("background-image", "none", "important");
            avatar.style.setProperty("color", "#ffffff", "important");
            avatar.style.setProperty("border", "1px solid rgba(255,255,255,.72)", "important");
            avatar.style.setProperty("box-shadow", "0 1px 3px rgba(17,17,15,.16), inset 0 0 0 1px rgba(255,255,255,.16)", "important");
            avatar.style.setProperty("font-weight", "800", "important");
        });
    }

    loadHomepagePolish();
    loadBreakingTickerStyles();
    applyUserAvatars();

    function addWeatherNavigation() {
        const desktopNavigation = document.querySelector(".desktop-navigation");
        if (!desktopNavigation) return;
        if (desktopNavigation.querySelector(".weather-nav-link")) return;

        const weatherLink = document.createElement("a");
        weatherLink.href = "/weather";
        weatherLink.className = "weather-nav-link";
        weatherLink.title = "Weather";

        const weatherLogo = document.createElement("img");
        weatherLogo.src = "/images/weather-logo.png";
        weatherLogo.alt = "Weather";
        weatherLogo.className = "weather-nav-logo";

        const weatherText = document.createElement("span");
        weatherText.textContent = "WEATHER";
        weatherText.className = "weather-nav-text";

        weatherLink.appendChild(weatherLogo);
        weatherLink.appendChild(weatherText);

        const sportsLink = Array.from(desktopNavigation.querySelectorAll("a"))
            .find(function (link) {
                return link.textContent.trim().toUpperCase() === "SPORTS";
            });

        if (sportsLink) {
            sportsLink.insertAdjacentElement("afterend", weatherLink);
        } else {
            desktopNavigation.appendChild(weatherLink);
        }

        const mobileNavigation = document.querySelector(".mobile-navigation");
        if (mobileNavigation && !mobileNavigation.querySelector('a[href="/weather"]')) {
            const mobileWeather = document.createElement("a");
            mobileWeather.href = "/weather";
            mobileWeather.className = "weather-mobile-link";
            mobileWeather.innerHTML =
                '<img src="/images/weather-logo.png" alt="Weather" class="weather-nav-logo">' +
                '<span>WEATHER</span>';
            mobileNavigation.appendChild(mobileWeather);
        }
    }

    addWeatherNavigation();

    const currentDate = document.getElementById("currentDate");
    if (currentDate) {
        const now = new Date();
        currentDate.textContent = now.toLocaleDateString("en-IN", {
            day: "2-digit",
            month: "short",
            year: "numeric"
        }).toUpperCase();
    }

    const istClock = document.getElementById("istClock");
    function updateISTClock() {
        if (!istClock) return;
        istClock.textContent = new Date().toLocaleTimeString("en-IN", {
            timeZone: "Asia/Kolkata",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false
        }) + " IST";
    }

    updateISTClock();
    setInterval(updateISTClock, 1000);

    const breakingTrack = document.getElementById("breakingTrack");
    if (breakingTrack) {
        let position = 0;
        let animationFrame;
        let paused = false;

        function animateBreakingNews() {
            if (paused) return;
            position -= 0.35;
            const firstWidth = breakingTrack.scrollWidth / 2;
            if (firstWidth > 0 && Math.abs(position) >= firstWidth) position = 0;
            breakingTrack.style.transform = "translate3d(" + position + "px,0,0)";
            animationFrame = requestAnimationFrame(animateBreakingNews);
        }

        animateBreakingNews();

        breakingTrack.addEventListener("mouseenter", function () {
            paused = true;
            cancelAnimationFrame(animationFrame);
        });

        breakingTrack.addEventListener("mouseleave", function () {
            if (!paused) return;
            paused = false;
            animateBreakingNews();
        });

        breakingTrack.addEventListener("focusin", function () {
            paused = true;
            cancelAnimationFrame(animationFrame);
        });

        breakingTrack.addEventListener("focusout", function () {
            if (!paused) return;
            paused = false;
            animateBreakingNews();
        });
    }

    function handleImageError(image) {
        if (!image || image.dataset.fallbackHandled) return;

        image.dataset.fallbackHandled = "true";
        image.style.display = "none";

        const parent = image.parentElement;
        if (!parent) return;

        parent.classList.add("image-error");

        const fallback = parent.querySelector(
            ".home-image-fallback, " +
            ".home-featured-fallback, " +
            ".image-fallback, " +
            ".article-image-fallback, " +
            ".article-related-fallback"
        );

        if (fallback) {
            fallback.style.display = "flex";
            return;
        }

        const generatedFallback = document.createElement("div");
        generatedFallback.className = parent.classList.contains("article-hero-image")
                ? "article-image-fallback generated-image-fallback"
                : parent.classList.contains("article-related-image")
                        ? "article-related-fallback generated-image-fallback"
                        : "image-fallback generated-image-fallback";
        generatedFallback.textContent = "AGNIPRESS";
        parent.appendChild(generatedFallback);
    }

    document.querySelectorAll("img").forEach(function (image) {
        image.addEventListener("error", function () {
            handleImageError(image);
        });

        if (image.complete && image.naturalWidth === 0 && image.currentSrc) {
            handleImageError(image);
        }
    });

    document.querySelectorAll('a[href="/weather"]').forEach(function (link) {
        link.addEventListener("click", function () {
            document.body.classList.add("page-leaving");
        });
    });

});
