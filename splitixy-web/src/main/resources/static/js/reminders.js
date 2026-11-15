document.querySelectorAll(".reminder-form").forEach((form) => {
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const button = form.querySelector("button[type='submit']");
        if (!(button instanceof HTMLButtonElement) || button.disabled) {
            return;
        }

        const label = button.querySelector("span");
        const originalLabel = label ? label.textContent : "";
        button.disabled = true;
        button.classList.add("is-loading");
        if (label) {
            label.textContent = "Sending…";
        }

        try {
            const response = await fetch(form.action, {
                method: "POST",
                body: new URLSearchParams(new FormData(form)),
                credentials: "same-origin",
                headers: { Accept: "application/json" }
            });
            const contentType = response.headers.get("content-type") || "";
            const result = contentType.includes("application/json")
                ? await response.json()
                : null;

            if (!response.ok) {
                throw new Error(result?.message || "The reminder could not be sent. Please try again.");
            }
            if (!result?.message) {
                throw new Error("The server returned an unexpected response. Please try again.");
            }
            showReminderToast(result.message, "success");
        } catch (error) {
            showReminderToast(
                error instanceof Error ? error.message : "The reminder could not be sent. Please try again.",
                "error"
            );
        } finally {
            button.disabled = false;
            button.classList.remove("is-loading");
            if (label) {
                label.textContent = originalLabel;
            }
        }
    });
});

function showReminderToast(message, state) {
    let toast = document.querySelector(".reminder-toast");
    if (!toast) {
        toast = document.createElement("div");
        toast.className = "reminder-toast";
        toast.setAttribute("role", state === "error" ? "alert" : "status");
        toast.setAttribute("aria-live", state === "error" ? "assertive" : "polite");
        document.body.append(toast);
    }

    toast.textContent = message;
    toast.classList.toggle("reminder-toast-error", state === "error");
    toast.classList.toggle("reminder-toast-success", state === "success");
    toast.setAttribute("role", state === "error" ? "alert" : "status");
    toast.setAttribute("aria-live", state === "error" ? "assertive" : "polite");
    toast.classList.add("is-visible");
    window.clearTimeout(toast.hideTimer);
    toast.hideTimer = window.setTimeout(() => {
        toast.classList.remove("is-visible");
    }, 4200);
}
