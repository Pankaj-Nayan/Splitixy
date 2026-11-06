document.querySelectorAll("[data-toggle-password]").forEach((button) => {
    button.addEventListener("click", () => {
        const input = document.getElementById(button.getAttribute("aria-controls"));
        if (!(input instanceof HTMLInputElement)) {
            return;
        }

        const isVisible = input.type === "password";
        input.type = isVisible ? "text" : "password";
        button.textContent = isVisible ? "Hide" : "Show";
        button.setAttribute("aria-label", isVisible ? "Hide password" : "Show password");
        button.setAttribute("aria-pressed", String(isVisible));
    });
});
