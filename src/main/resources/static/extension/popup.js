const checkBtn = document.getElementById("checkBtn");
const statusEl = document.getElementById("status");

checkBtn.addEventListener("click", () => {
    statusEl.textContent = "Checking...";

    chrome.runtime.sendMessage({ type: "CHECK_HEALTH" }, (response) => {
        if (chrome.runtime.lastError) {
            statusEl.textContent = `Error: ${chrome.runtime.lastError.message}`;
            return;
        }

        if (response && response.ok) {
            statusEl.textContent = response.data; // Displays "OK"
        } else {
            statusEl.textContent = `Failed: ${response ? response.error : "No response"}`;
        }
    });
});