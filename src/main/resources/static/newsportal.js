document.addEventListener("DOMContentLoaded", function () {

    "use strict";


    /* =========================================================
       WEATHER NAVIGATION
       ========================================================= */

    function addWeatherNavigation() {

        const desktopNavigation =
            document.querySelector(".desktop-navigation");

        if (!desktopNavigation) {
            console.warn(
                "News Portal: .desktop-navigation not found."
            );
            return;
        }

        if (
            desktopNavigation.querySelector(
                ".weather-nav-link"
            )
        ) {
            return;
        }

        const weatherLink =
            document.createElement("a");

        weatherLink.href = "/weather";
        weatherLink.className = "weather-nav-link";
        weatherLink.title = "Weather";

        const weatherLogo =
            document.createElement("img");

        weatherLogo.src =
            "/images/weather-logo.png";

        weatherLogo.alt = "Weather";
        weatherLogo.className = "weather-nav-logo";

        const weatherText =
            document.createElement("span");

        weatherText.textContent = "WEATHER";
        weatherText.className = "weather-nav-text";

        weatherLink.appendChild(weatherLogo);
        weatherLink.appendChild(weatherText);

        const navigationLinks =
            Array.from(
                desktopNavigation.querySelectorAll("a")
            );

        const sportsLink =
            navigationLinks.find(function (link) {

                return (
                    link.textContent
                        .trim()
                        .toUpperCase() === "SPORTS"
                );

            });

        if (sportsLink) {

            sportsLink.insertAdjacentElement(
                "afterend",
                weatherLink
            );

        } else {

            desktopNavigation.appendChild(
                weatherLink
            );
        }


        /* MOBILE WEATHER */

        const mobileNavigation =
            document.querySelector(
                ".mobile-navigation"
            );

        if (
            mobileNavigation &&
            !mobileNavigation.querySelector(
                ".weather-mobile-link"
            )
        ) {

            const mobileWeather =
                document.createElement("a");

            mobileWeather.href = "/weather";

            mobileWeather.className =
                "weather-mobile-link";

            mobileWeather.innerHTML =
                '<img ' +
                'src="/images/weather-logo.png" ' +
                'alt="Weather" ' +
                'class="weather-nav-logo">' +
                '<span>WEATHER</span>';

            mobileNavigation.appendChild(
                mobileWeather
            );
        }
    }


    addWeatherNavigation();


    /* =========================================================
       CURRENT DATE
       ========================================================= */

    const currentDate =
        document.getElementById(
            "currentDate"
        );

    if (currentDate) {

        const now = new Date();

        const options = {
            day: "2-digit",
            month: "short",
            year: "numeric"
        };

        currentDate.textContent =
            now
                .toLocaleDateString(
                    "en-IN",
                    options
                )
                .toUpperCase();
    }


    /* =========================================================
       IST CLOCK
       ========================================================= */

    const istClock =
        document.getElementById(
            "istClock"
        );

    function updateISTClock() {

        if (!istClock) {
            return;
        }

        const now = new Date();

        const time =
            now.toLocaleTimeString(
                "en-IN",
                {
                    timeZone: "Asia/Kolkata",
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit",
                    hour12: false
                }
            );

        istClock.textContent =
            time + " IST";
    }

    updateISTClock();

    setInterval(
        updateISTClock,
        1000
    );


    /* =========================================================
       BREAKING NEWS ANIMATION
       ========================================================= */

    const breakingTrack =
        document.getElementById(
            "breakingTrack"
        );

    if (breakingTrack) {

        let position = 0;
        let animationFrame;

        function animateBreakingNews() {

            position -= 0.35;

            const firstWidth =
                breakingTrack.scrollWidth / 2;

            if (
                Math.abs(position)
                >= firstWidth
            ) {
                position = 0;
            }

            breakingTrack.style.transform =
                "translateX(" +
                position +
                "px)";

            animationFrame =
                requestAnimationFrame(
                    animateBreakingNews
                );
        }

        animateBreakingNews();


        breakingTrack.addEventListener(
            "mouseenter",
            function () {

                cancelAnimationFrame(
                    animationFrame
                );

            }
        );


        breakingTrack.addEventListener(
            "mouseleave",
            function () {

                animateBreakingNews();

            }
        );
    }


    /* =========================================================
       USER DROPDOWN
       ========================================================= */

    const userMenu =
        document.querySelector(
            ".user-menu"
        );

    const userTrigger =
        document.querySelector(
            ".user-trigger"
        );

    const userDropdown =
        document.querySelector(
            ".user-dropdown"
        );


    if (
        userMenu &&
        userTrigger &&
        userDropdown
    ) {


        /* -----------------------------------------
           OPEN / CLOSE
           ----------------------------------------- */

        userTrigger.addEventListener(
            "click",
            function (event) {

                event.preventDefault();
                event.stopPropagation();

                userMenu.classList.toggle(
                    "open"
                );

            }
        );


        /* -----------------------------------------
           CLOSE WHEN CLICKING OUTSIDE
           ----------------------------------------- */

        document.addEventListener(
            "click",
            function (event) {

                if (
                    !userMenu.contains(event.target)
                ) {

                    userMenu.classList.remove(
                        "open"
                    );

                }

            }
        );


        /* -----------------------------------------
           KEEP OPEN WHEN CLICKING INSIDE
           ----------------------------------------- */

        userDropdown.addEventListener(
            "click",
            function (event) {

                event.stopPropagation();

            }
        );


        /* -----------------------------------------
           ESCAPE KEY
           ----------------------------------------- */

        document.addEventListener(
            "keydown",
            function (event) {

                if (
                    event.key === "Escape"
                ) {

                    userMenu.classList.remove(
                        "open"
                    );

                }

            }
        );

    }


    /* =========================================================
       MOBILE MENU
       ========================================================= */

    const mobileMenuButton =
        document.querySelector(
            ".mobile-menu-button"
        );

    const mobileNavigation =
        document.querySelector(
            ".mobile-navigation"
        );

    if (
        mobileMenuButton &&
        mobileNavigation
    ) {

        mobileMenuButton.addEventListener(
            "click",
            function () {

                mobileNavigation.classList.toggle(
                    "open"
                );

            }
        );
    }


    /* =========================================================
       IMAGE ERROR HANDLING
       ========================================================= */

    const images =
        document.querySelectorAll(
            "img"
        );

    images.forEach(function (image) {

        image.addEventListener(
            "error",
            function () {

                if (
                    image.dataset.fallbackHandled
                ) {
                    return;
                }

                image.dataset.fallbackHandled =
                    "true";

                image.style.display =
                    "none";

                const parent =
                    image.parentElement;

                if (!parent) {
                    return;
                }

                parent.classList.add(
                    "image-error"
                );

                const fallback =
                    parent.querySelector(
                        ".home-image-fallback, " +
                        ".home-featured-fallback, " +
                        ".image-fallback"
                    );

                if (fallback) {

                    fallback.style.display =
                        "flex";

                }

            }
        );

    });


    /* =========================================================
       SMOOTH PAGE TRANSITIONS
       ========================================================= */

    document
        .querySelectorAll(
            'a[href="/weather"]'
        )
        .forEach(function (link) {

            link.addEventListener(
                "click",
                function () {

                    document.body.classList.add(
                        "page-leaving"
                    );

                }
            );

        });

});