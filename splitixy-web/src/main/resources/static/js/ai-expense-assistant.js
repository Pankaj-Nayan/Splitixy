const extractForm = document.getElementById("extractForm");
if (extractForm) {
    extractForm.addEventListener("submit", function () {
        const button = document.getElementById("extractButton");
        button.disabled = true;
        button.textContent = "Processing…";
        document.getElementById("loadingMessage").hidden = false;
    });
}

const splitTypeInput = document.getElementById("splitType");
const customShares = document.getElementById("customShares");
if (splitTypeInput && customShares) {
    const participants = Array.from(
        document.querySelectorAll('input[name="participantIds"]')
    );
    const shareFields = Array.from(document.querySelectorAll(".share-field"));

    function updateShareFields() {
        const isCustom = splitTypeInput.value === "CUSTOM";
        customShares.hidden = !isCustom;
        shareFields.forEach(function (field) {
            const memberId = field.dataset.memberId;
            const participant = participants.find(function (input) {
                return input.value === memberId;
            });
            field.hidden = !isCustom || !participant || !participant.checked;
            const input = field.querySelector("input");
            input.required = isCustom && participant !== undefined && participant.checked;
        });
    }

    splitTypeInput.addEventListener("change", updateShareFields);
    participants.forEach(function (input) {
        input.addEventListener("change", updateShareFields);
    });
    updateShareFields();
}
