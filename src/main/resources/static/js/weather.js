document.addEventListener("DOMContentLoaded", () => {

    // =====================================================
    // ELEMENTS
    // =====================================================

    const locationButton =
        document.getElementById("location-button");

    const cityButton =
        document.getElementById("city-button");

    const cityInput =
        document.getElementById("city-input");

    const refreshButton =
        document.getElementById("refresh-weather");

    const locationError =
        document.getElementById("location-error");

    const locationSection =
        document.getElementById("location-section");

    const loadingSection =
        document.getElementById("loading-section");

    const weatherContent =
        document.getElementById("weather-content");


    // =====================================================
    // SAVED LOCATION
    // =====================================================

    const LOCATION_KEY =
        "newsportal_weather_location";


    let lastLatitude = null;
    let lastLongitude = null;


    // =====================================================
    // INITIAL LOAD
    // =====================================================

    initializeWeather();


    async function initializeWeather() {

        const savedLocation =
            getSavedLocation();


        /*
         * If we already have a saved location,
         * DO NOT ask the browser for location again.
         *
         * Simply use the saved coordinates.
         */

        if (savedLocation) {

            lastLatitude =
                savedLocation.latitude;

            lastLongitude =
                savedLocation.longitude;


            console.log(
                "Using saved weather location:",
                lastLatitude,
                lastLongitude
            );


            fetchWeather(
                lastLatitude,
                lastLongitude
            );


            return;
        }


        /*
         * First visit.
         *
         * Show the location request screen.
         */

        if (locationSection) {
            locationSection.classList.remove("hidden");
        }

        if (weatherContent) {
            weatherContent.classList.add("hidden");
        }

        if (loadingSection) {
            loadingSection.classList.add("hidden");
        }

    }


    // =====================================================
    // USE MY LOCATION
    // =====================================================

    if (locationButton) {

        locationButton.addEventListener(
            "click",
            requestUserLocation
        );

    }


    function requestUserLocation() {

        if (locationError) {
            locationError.textContent = "";
        }


        if (!navigator.geolocation) {

            if (locationError) {

                locationError.textContent =
                    "Location services are not supported by this browser.";

            }

            return;
        }


        showLoading();


        navigator.geolocation.getCurrentPosition(

            position => {

                lastLatitude =
                    position.coords.latitude;

                lastLongitude =
                    position.coords.longitude;


                /*
                 * Save the coordinates.
                 *
                 * This is the important part:
                 * next time the weather page opens,
                 * we use these coordinates instead
                 * of asking for location again.
                 */

                saveLocation(
                    lastLatitude,
                    lastLongitude
                );


                console.log(
                    "Location saved:",
                    lastLatitude,
                    lastLongitude
                );


                fetchWeather(
                    lastLatitude,
                    lastLongitude
                );

            },


            error => {

                hideLoading();


                if (!locationError) {
                    return;
                }


                if (
                    error.code ===
                    error.PERMISSION_DENIED
                ) {

                    locationError.textContent =
                        "Location access was denied. You can search for a city instead.";

                } else if (
                    error.code ===
                    error.POSITION_UNAVAILABLE
                ) {

                    locationError.textContent =
                        "Your location could not be determined.";

                } else {

                    locationError.textContent =
                        "Location request timed out. Please try again.";

                }

            },


            {
                enableHighAccuracy: false,
                timeout: 10000,
                maximumAge: 300000
            }

        );

    }


    // =====================================================
    // SAVE LOCATION
    // =====================================================

    function saveLocation(
        latitude,
        longitude
    ) {

        const location = {

            latitude: Number(latitude),
            longitude: Number(longitude),
            savedAt: Date.now()

        };


        localStorage.setItem(
            LOCATION_KEY,
            JSON.stringify(location)
        );

    }


    // =====================================================
    // GET SAVED LOCATION
    // =====================================================

    function getSavedLocation() {

        try {

            const saved =
                localStorage.getItem(
                    LOCATION_KEY
                );


            if (!saved) {
                return null;
            }


            const location =
                JSON.parse(saved);


            if (
                typeof location.latitude !== "number" ||
                typeof location.longitude !== "number" ||
                !Number.isFinite(location.latitude) ||
                !Number.isFinite(location.longitude)
            ) {

                return null;

            }


            return location;

        } catch (error) {

            console.error(
                "Could not read saved location:",
                error
            );


            return null;

        }

    }


    // =====================================================
    // REFRESH WEATHER
    // =====================================================

    if (refreshButton) {

        refreshButton.addEventListener(
            "click",
            () => {

                /*
                 * Refresh uses the already-known
                 * coordinates.
                 *
                 * It does NOT ask for permission again.
                 */

                if (
                    lastLatitude !== null &&
                    lastLongitude !== null
                ) {

                    fetchWeather(
                        lastLatitude,
                        lastLongitude
                    );

                } else {

                    const savedLocation =
                        getSavedLocation();


                    if (savedLocation) {

                        lastLatitude =
                            savedLocation.latitude;

                        lastLongitude =
                            savedLocation.longitude;


                        fetchWeather(
                            lastLatitude,
                            lastLongitude
                        );

                    } else {

                        requestUserLocation();

                    }

                }

            }
        );

    }


    // =====================================================
    // CITY SEARCH
    // =====================================================

    if (cityButton) {

        cityButton.addEventListener(
            "click",
            () => {

                const city =
                    cityInput
                        ? cityInput.value.trim()
                        : "";


                if (!city) {

                    if (locationError) {

                        locationError.textContent =
                            "Please enter a city name.";

                    }

                    return;
                }


                fetchCityWeather(city);

            }
        );

    }


    // =====================================================
    // ENTER KEY FOR CITY SEARCH
    // =====================================================

    if (cityInput) {

        cityInput.addEventListener(
            "keydown",
            event => {

                if (event.key === "Enter") {

                    event.preventDefault();


                    if (cityButton) {
                        cityButton.click();
                    }

                }

            }
        );

    }


    // =====================================================
    // FETCH WEATHER BY COORDINATES
    // =====================================================

    async function fetchWeather(
        latitude,
        longitude
    ) {

        try {

            showLoading();


            const response =
                await fetch(
                    `/api/weather?lat=${encodeURIComponent(latitude)}&lon=${encodeURIComponent(longitude)}`
                );


            if (!response.ok) {

                throw new Error(
                    `Weather request failed: ${response.status}`
                );

            }


            const data =
                await response.json();


            console.log(
                "WEATHER DATA RECEIVED:",
                data
            );


            console.log(
                "FORECAST ITEMS RECEIVED BY BROWSER:",
                Array.isArray(data.forecast)
                    ? data.forecast.length
                    : "NOT AN ARRAY"
            );


            renderWeather(data);


        } catch (error) {

            console.error(
                "Weather error:",
                error
            );


            hideLoading();


            if (locationError) {

                locationError.textContent =
                    "Unable to load weather data. Please try again.";

            }

        }

    }


    // =====================================================
    // CITY WEATHER
    // =====================================================

    async function fetchCityWeather(city) {

        try {

            showLoading();


            const response =
                await fetch(
                    `/api/weather/city?city=${encodeURIComponent(city)}`
                );


            if (!response.ok) {

                throw new Error(
                    `City weather request failed: ${response.status}`
                );

            }


            const data =
                await response.json();


            console.log(
                "CITY WEATHER DATA:",
                data
            );


            console.log(
                "CITY FORECAST ITEMS:",
                Array.isArray(data.forecast)
                    ? data.forecast.length
                    : "NOT AN ARRAY"
            );


            renderWeather(data);


        } catch (error) {

            console.error(
                "City weather error:",
                error
            );


            hideLoading();


            if (locationError) {

                locationError.textContent =
                    "Could not find weather for that city.";

            }

        }

    }


    // =====================================================
    // RENDER WEATHER
    // =====================================================

    function renderWeather(data) {

        hideLoading();


        if (locationSection) {

            locationSection.classList.add(
                "hidden"
            );

        }


        if (weatherContent) {

            weatherContent.classList.remove(
                "hidden"
            );

        }


        const location =
            data.location || {};

        const current =
            data.current || {};

        const forecast =
            Array.isArray(data.forecast)
                ? data.forecast
                : [];


        // =================================================
        // LOCATION
        // =================================================

        const locationName =
            document.getElementById(
                "location-name"
            );


        if (locationName) {

            locationName.textContent =
                location.city ||
                "Unknown";

        }


        const locationCountry =
            document.getElementById(
                "location-country"
            );


        if (locationCountry) {

            locationCountry.textContent =
                [
                    location.state,
                    location.country
                ]
                    .filter(Boolean)
                    .join(", ");

        }


        // =================================================
        // TEMPERATURE
        // =================================================

        const temperature =
            document.getElementById(
                "temperature"
            );


        if (temperature) {

            temperature.textContent =
                Math.round(
                    Number(
                        current.temperature || 0
                    )
                );

        }


        // =================================================
        // CONDITION
        // =================================================

        const condition =
            document.getElementById(
                "condition"
            );


        if (condition) {

            condition.textContent =
                current.condition ||
                "Unknown";

        }


        // =================================================
        // DESCRIPTION
        // =================================================

        const description =
            document.getElementById(
                "description"
            );


        if (description) {

            description.textContent =
                capitalize(
                    current.description ||
                    current.condition ||
                    ""
                );

        }


        // =================================================
        // DETAILS
        // =================================================

        const feelsLike =
            document.getElementById(
                "feels-like"
            );


        if (feelsLike) {

            feelsLike.textContent =
                `${Math.round(
                    Number(
                        current.feelsLike || 0
                    )
                )}°C`;

        }


        const humidity =
            document.getElementById(
                "humidity"
            );


        if (humidity) {

            humidity.textContent =
                `${current.humidity ?? "—"}%`;

        }


        const wind =
            document.getElementById(
                "wind"
            );


        if (wind) {

            wind.textContent =
                `${current.windSpeed ?? "—"} m/s`;

        }


        const pressure =
            document.getElementById(
                "pressure"
            );


        if (pressure) {

            pressure.textContent =
                `${current.pressure ?? "—"} hPa`;

        }


        // =================================================
        // CURRENT WEATHER ICON
        // =================================================

        const weatherIcon =
            document.getElementById(
                "weather-icon"
            );


        if (
            weatherIcon &&
            current.icon
        ) {

            weatherIcon.src =
                `https://openweathermap.org/img/wn/${current.icon}@2x.png`;

            weatherIcon.alt =
                current.description ||
                "Current weather";

        }


        // =================================================
        // WEATHER ANIMATION
        // =================================================

        updateWeatherAnimation(
            current.condition,
            current.icon
        );


        // =================================================
        // FORECAST
        // =================================================

        renderForecast(
            forecast
        );


        // =================================================
        // SUMMARY
        // =================================================

        generateSummary(
            current
        );

    }


    // =====================================================
    // WEATHER ANIMATION
    // =====================================================

    function updateWeatherAnimation(
        condition,
        icon
    ) {

        const scene =
            document.getElementById(
                "weather-scene"
            );


        if (!scene) {
            return;
        }


        scene.className =
            "weather-scene";


        const weather =
            String(
                condition || ""
            ).toLowerCase();


        const iconCode =
            String(
                icon || ""
            ).toLowerCase();


        if (
            weather.includes("thunder") ||
            weather.includes("storm") ||
            iconCode.startsWith("11")
        ) {

            scene.classList.add(
                "thunderstorm"
            );

            return;

        }


        if (
            weather.includes("rain") ||
            weather.includes("drizzle") ||
            iconCode.startsWith("09") ||
            iconCode.startsWith("10")
        ) {

            scene.classList.add(
                "rain"
            );

            return;

        }


        if (
            weather.includes("snow") ||
            iconCode.startsWith("13")
        ) {

            scene.classList.add(
                "snow"
            );

            return;

        }


        if (
            weather.includes("mist") ||
            weather.includes("fog") ||
            weather.includes("haze") ||
            weather.includes("smoke") ||
            iconCode.startsWith("50")
        ) {

            scene.classList.add(
                "fog"
            );

            return;

        }


        if (
            weather.includes("cloud") ||
            iconCode.startsWith("02") ||
            iconCode.startsWith("03") ||
            iconCode.startsWith("04")
        ) {

            scene.classList.add(
                "cloudy"
            );

            return;

        }


        if (
            iconCode.endsWith("n")
        ) {

            scene.classList.add(
                "night"
            );

            return;

        }


        scene.classList.add(
            "sunny"
        );

    }


    // =====================================================
    // FORECAST
    // =====================================================

    function renderForecast(
        forecast
    ) {

        const container =
            document.getElementById(
                "forecast-list"
            );


        if (!container) {
            return;
        }


        container.innerHTML = "";


        if (
            !Array.isArray(forecast) ||
            forecast.length === 0
        ) {

            container.innerHTML =
                "<p>No forecast available.</p>";

            return;

        }


        /*
         * Backend returns 40 forecast entries.
         *
         * We display the first 12.
         */

        const forecastItems =
            forecast.slice(0, 12);


        console.log(
            "FORECAST CARDS TO RENDER:",
            forecastItems.length
        );


        forecastItems.forEach(
            (item, index) => {

                try {

                    const safeItem =
                        item || {};


                    // =============================================
                    // WEATHER TYPE
                    // =============================================

                    const weatherType =
                        getWeatherType(
                            safeItem.condition,
                            safeItem.description,
                            safeItem.icon
                        );


                    const card =
                        document.createElement(
                            "article"
                        );


                    card.className =
                        `forecast-card forecast-${weatherType}`;


                    // =============================================
                    // TIME
                    // =============================================

                    const timestamp =
                        Number(
                            safeItem.timestamp
                        );


                    const date =
                        Number.isFinite(timestamp)
                            ? new Date(timestamp * 1000)
                            : new Date();


                    const time =
                        date.toLocaleTimeString(
                            [],
                            {
                                hour: "numeric"
                            }
                        );


                    // =============================================
                    // VALUES
                    // =============================================

                    const temperature =
                        Math.round(
                            Number(
                                safeItem.temperature || 0
                            )
                        );


                    const description =
                        safeItem.description ||
                        safeItem.condition ||
                        "Weather";


                    const icon =
                        safeItem.icon ||
                        "01d";


                    // =============================================
                    // CARD HTML
                    // =============================================

                    card.innerHTML = `

                        <div class="forecast-atmosphere">

                            <!-- SUN -->

                            <div class="forecast-sun"></div>


                            <!-- MOON -->

                            <div class="forecast-moon"></div>


                            <!-- CLOUDS -->

                            <div class="forecast-cloud cloud-a"></div>

                            <div class="forecast-cloud cloud-b"></div>


                            <!-- RAIN -->

                            <div class="forecast-rain">

                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>
                                <i></i>

                            </div>


                            <!-- LIGHTNING -->

                            <div class="forecast-lightning"></div>


                            <!-- SNOW -->

                            <div class="forecast-snow">

                                <i>•</i>
                                <i>•</i>
                                <i>•</i>
                                <i>•</i>
                                <i>•</i>
                                <i>•</i>
                                <i>•</i>

                            </div>

                        </div>


                        <div class="forecast-content">

                            <span class="forecast-time">
                                ${time}
                            </span>


                            <div class="forecast-icon">

                                <img
                                    src="https://openweathermap.org/img/wn/${icon}@2x.png"
                                    alt="${description}"
                                >

                            </div>


                            <strong>
                                ${temperature}°C
                            </strong>


                            <span class="forecast-description">
                                ${capitalize(description)}
                            </span>

                        </div>

                    `;


                    // =============================================
                    // ADD CARD
                    // =============================================

                    container.appendChild(
                        card
                    );


                    console.log(
                        `Forecast card ${index + 1} rendered: ${time}`
                    );


                } catch (error) {

                    /*
                     * If one forecast item is bad,
                     * don't stop the other 11 cards.
                     */

                    console.error(
                        `Forecast item ${index + 1} failed:`,
                        item,
                        error
                    );

                }

            }
        );


        console.log(
            "ACTUAL FORECAST CARDS IN DOM:",
            container.children.length
        );

    }


    // =====================================================
    // WEATHER TYPE
    // =====================================================

    function getWeatherType(
        condition,
        description,
        icon
    ) {

        const text =
            `${condition || ""} ${description || ""}`
                .toLowerCase();


        const iconCode =
            String(
                icon || ""
            ).toLowerCase();


        // THUNDERSTORM

        if (
            text.includes("thunder") ||
            text.includes("storm") ||
            iconCode.startsWith("11")
        ) {

            return "thunderstorm";

        }


        // SNOW

        if (
            text.includes("snow") ||
            text.includes("sleet") ||
            iconCode.startsWith("13")
        ) {

            return "snow";

        }


        // RAIN

        if (
            text.includes("rain") ||
            text.includes("drizzle") ||
            text.includes("shower") ||
            iconCode.startsWith("09") ||
            iconCode.startsWith("10")
        ) {

            return "rain";

        }


        // FOG

        if (
            text.includes("fog") ||
            text.includes("mist") ||
            text.includes("haze") ||
            text.includes("smoke") ||
            iconCode.startsWith("50")
        ) {

            return "fog";

        }


        // CLOUDY

        if (
            text.includes("cloud") ||
            iconCode.startsWith("02") ||
            iconCode.startsWith("03") ||
            iconCode.startsWith("04")
        ) {

            return "cloudy";

        }


        // NIGHT

        if (
            iconCode.endsWith("n")
        ) {

            return "night";

        }


        // DEFAULT

        return "sunny";

    }


    // =====================================================
    // SUMMARY
    // =====================================================

    function generateSummary(
        current
    ) {

        const summary =
            document.getElementById(
                "weather-summary"
            );


        if (!summary) {
            return;
        }


        const temperature =
            Math.round(
                Number(
                    current.temperature || 0
                )
            );


        const feelsLike =
            Math.round(
                Number(
                    current.feelsLike || 0
                )
            );


        const humidity =
            current.humidity ?? "—";


        const description =
            current.description ||
            current.condition ||
            "current conditions";


        let text =
            `Currently ${description}, ${temperature}°C.`;


        if (
            feelsLike !== 0
        ) {

            text +=
                ` It feels like ${feelsLike}°C.`;

        }


        if (
            humidity !== "—"
        ) {

            text +=
                ` Humidity is ${humidity}%.`;

        }


        if (
            current.windSpeed !== undefined
        ) {

            text +=
                ` Winds are around ${current.windSpeed} m/s.`;

        }


        summary.textContent =
            capitalize(text);

    }


    // =====================================================
    // LOADING
    // =====================================================

    function showLoading() {

        if (locationSection) {

            locationSection.classList.add(
                "hidden"
            );

        }


        if (weatherContent) {

            weatherContent.classList.add(
                "hidden"
            );

        }


        if (loadingSection) {

            loadingSection.classList.remove(
                "hidden"
            );

        }

    }


    function hideLoading() {

        if (loadingSection) {

            loadingSection.classList.add(
                "hidden"
            );

        }

    }


    // =====================================================
    // CAPITALIZE
    // =====================================================

    function capitalize(
        value
    ) {

        if (!value) {
            return "";
        }


        const text =
            String(value);


        return (
            text.charAt(0).toUpperCase()
            +
            text.slice(1)
        );

    }

});