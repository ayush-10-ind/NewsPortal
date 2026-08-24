document.addEventListener("DOMContentLoaded", function () {

    "use strict";


    /* =========================================================
       WEATHER NAVIGATION
       ========================================================= */

    function addWeatherNavigation() {

        /*
         * The navbar is loaded through:
         *
         * fragments/navbar.html
         *
         * Find the actual desktop navigation container.
         */

        const desktopNavigation =
            document.querySelector(".desktop-navigation");


        if (!desktopNavigation) {

            console.warn(
                "News Portal: .desktop-navigation not found."
            );

            return;

        }


        /*
         * Don't add it twice.
         */

        if (
            desktopNavigation.querySelector(
                ".weather-nav-link"
            )
        ) {

            return;

        }


        /*
         * Create Weather link.
         */

        const weatherLink =
            document.createElement("a");


        weatherLink.href = "/weather";

        weatherLink.className =
            "weather-nav-link";

        weatherLink.title =
            "Weather";


        /*
         * Weather logo.
         *
         * This uses the image you provided:
         *
         * static/images/weather-logo.png
         */

        const weatherLogo =
            document.createElement("img");


        weatherLogo.src =
            "/images/weather-logo.png";


        weatherLogo.alt =
            "Weather";


        weatherLogo.className =
            "weather-nav-logo";


        /*
         * Weather text.
         */

        const weatherText =
            document.createElement("span");


        weatherText.textContent =
            "WEATHER";


        weatherText.className =
            "weather-nav-text";


        /*
         * Build link.
         */

        weatherLink.appendChild(
            weatherLogo
        );

        weatherLink.appendChild(
            weatherText
        );


        /*
         * Find SPORTS.
         *
         * Weather will appear immediately
         * after SPORTS.
         */

        const navigationLinks =
            Array.from(
                desktopNavigation.querySelectorAll("a")
            );


        const sportsLink =
            navigationLinks.find(function (link) {

                return (
                    link.textContent
                        .trim()
                        .toUpperCase()
                    === "SPORTS"
                );

            });


        /*
         * Insert after SPORTS.
         */

        if (sportsLink) {

            sportsLink.insertAdjacentElement(
                "afterend",
                weatherLink
            );

        } else {

            /*
             * Fallback:
             * if SPORTS isn't found, put Weather
             * at the end of the desktop navigation.
             */

            desktopNavigation.appendChild(
                weatherLink
            );

        }


        /*
         * Add Weather to mobile navigation too.
         */

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


            mobileWeather.href =
                "/weather";


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


    /*
     * Run immediately.
     */

    addWeatherNavigation();



    /* =========================================================
       CURRENT DATE
       ========================================================= */

    const currentDate =
        document.getElementById(
            "currentDate"
        );


    if (currentDate) {

        const now =
            new Date();


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


        const now =
            new Date();


        const time =
            now.toLocaleTimeString(
                "en-IN",
                {
                    timeZone:
                        "Asia/Kolkata",

                    hour:
                        "2-digit",

                    minute:
                        "2-digit",

                    second:
                        "2-digit",

                    hour12:
                        false
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


        /*
         * Pause when mouse is over ticker.
         */

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

    const userTrigger =
        document.querySelector(
            ".user-trigger"
        );


    const userDropdown =
        document.querySelector(
            ".user-dropdown"
        );


    if (
        userTrigger &&
        userDropdown
    ) {


        userTrigger.addEventListener(
            "click",
            function (event) {

                event.stopPropagation();


                userDropdown.classList.toggle(
                    "open"
                );

            }
        );


        document.addEventListener(
            "click",
            function () {

                userDropdown.classList.remove(
                    "open"
                );

            }
        );


        userDropdown.addEventListener(
            "click",
            function (event) {

                event.stopPropagation();

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

                /*
                 * Don't repeatedly trigger
                 * the same error.
                 */

                if (
                    image.dataset.fallbackHandled
                ) {

                    return;

                }


                image.dataset.fallbackHandled =
                    "true";


                /*
                 * Hide broken image.
                 */

                image.style.display =
                    "none";


                /*
                 * Show parent's fallback
                 * if available.
                 */

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