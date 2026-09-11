document.addEventListener("DOMContentLoaded", function () {

    "use strict";


    /* =========================================================
       AGNIPRESS HOMEPAGE POLISH
       ========================================================= */

    function loadHomepagePolish() {

        if (!document.querySelector(".home-hero")) {
            return;
        }

        if (document.querySelector('link[data-home-polish="true"]')) {
            return;
        }

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

    loadHomepagePolish();


    /* =========================================================
       WEATHER NAVIGATION
       ========================================================= */

    function addWeatherNavigation() {

        const desktopNavigation =
            document.querySelector(".desktop-navigation");

        if (!desktopNavigation) {
            console.warn("AgniPress: .desktop-navigation not found.");
            return;
        }

        if (desktopNavigation.querySelector(".weather-nav-link")) {
            return;
        }

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

        const navigationLinks = Array.from(
            desktopNavigation.querySelectorAll("a")
        );

        const sportsLink = navigationLinks.find(function (link) {
            return link.textContent.trim().toUpperCase() === "SPORTS";
        });

        if (sportsLink) {
            sportsLink.insertAdjacentElement("afterend", weatherLink);
        } else {
            desktopNavigation.appendChild(weatherLink);
        }

        const mobileNavigation = document.querySelector(".mobile-navigation");

        if (
            mobileNavigation &&
            !mobileNavigation.querySelector('a[href="/weather"]')
        ) {
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


    /* =========================================================
       CURRENT DATE
       ========================================================= */

    const currentDate = document.getElementById("currentDate");

    if (currentDate) {
        const now = new Date();
        const options = {
            day: "2-digit",
            month: "short",
            year: "numeric"
        };

        currentDate.textContent = now
            .toLocaleDateString("en-IN", options)
            .toUpperCase();
    }


    /* =========================================================
       IST CLOCK
       ========================================================= */

    const istClock = document.getElementById("istClock");

    function updateISTClock() {
        if (!istClock) {
            return;
        }

        const now = new Date();

        const time = now.toLocaleTimeString("en-IN", {
            timeZone: "Asia/Kolkata",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false
        });

        istClock.textContent = time + " IST";
    }

    updateISTClock();
    setInterval(updateISTClock, 1000);


    /* =========================================================
       BREAKING NEWS ANIMATION
       ========================================================= */

    const breakingTrack = document.getElementById("breakingTrack");

    if (breakingTrack) {

        let position = 0;
        let animationFrame;
        let paused = false;

        function animateBreakingNews() {

            if (paused) {
                return;
            }

            position -= 0.35;

            const firstWidth = breakingTrack.scrollWidth / 2;

            if (firstWidth > 0 && Math.abs(position) >= firstWidth) {
                position = 0;
            }

            breakingTrack.style.transform =
                "translate3d(" + position + "px,0,0)";

            animationFrame = requestAnimationFrame(animateBreakingNews);
        }

        animateBreakingNews();

        breakingTrack.addEventListener("mouseenter", function () {
            paused = true;
            cancelAnimationFrame(animationFrame);
        });

        breakingTrack.addEventListener("mouseleave", function () {
            if (!paused) {
                return;
            }
            paused = false;
            animateBreakingNews();
        });

        breakingTrack.addEventListener("focusin", function () {
            paused = true;
            cancelAnimationFrame(animationFrame);
        });

        breakingTrack.addEventListener("focusout", function () {
            if (!paused) {
                return;
            }
            paused = false;
            animateBreakingNews();
        });
    }


    /* =========================================================
       IMAGE ERROR HANDLING
       ========================================================= */

    const images = document.querySelectorAll("img");

    images.forEach(function (image) {

        image.addEventListener("error", function () {

            if (image.dataset.fallbackHandled) {
                return;
            }

            image.dataset.fallbackHandled = "true";
            image.style.display = "none";

            const parent = image.parentElement;

            if (!parent) {
                return;
            }

            parent.classList.add("image-error");

            const fallback = parent.querySelector(
                ".home-image-fallback, .home-featured-fallback, .image-fallback"
            );

            if (fallback) {
                fallback.style.display = "flex";
            }
        });
    });


    /* =========================================================
       SMOOTH PAGE TRANSITIONS
       ========================================================= */

    document.querySelectorAll('a[href="/weather"]').forEach(function (link) {

        link.addEventListener("click", function () {
            document.body.classList.add("page-leaving");
        });
    });

});
