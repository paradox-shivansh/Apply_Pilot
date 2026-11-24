// --- Tab Switching ---
const tabButtons = document.querySelectorAll(".tab-btn");
const tabPanels = document.querySelectorAll(".tab-panel");

tabButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
        tabButtons.forEach((b) => b.classList.remove("active"));
        tabPanels.forEach((p) => p.classList.remove("active"));

        btn.classList.add("active");
        document.getElementById(btn.dataset.tab).classList.add("active");
    });
});

// --- Health Check ---
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
            statusEl.textContent = response.data;
        } else {
            statusEl.textContent = `Failed: ${response ? response.error : "No response"}`;
        }
    });
});

// --- Settings Persistence ---
const providerSelect = document.getElementById("aiProvider");
const apiKeyInput = document.getElementById("apiKey");
const saveSettingsBtn = document.getElementById("saveSettingsBtn");
const settingsStatus = document.getElementById("settingsStatus");

// Restore saved settings when popup opens
document.addEventListener("DOMContentLoaded", () => {
    chrome.storage.local.get(["aiProvider", "apiKey"], (result) => {
        if (result.aiProvider) {
            providerSelect.value = result.aiProvider;
        }
        if (result.apiKey) {
            apiKeyInput.value = result.apiKey;
        }
    });
});

// Save settings on click
saveSettingsBtn.addEventListener("click", () => {
    const aiProvider = providerSelect.value;
    const apiKey = apiKeyInput.value.trim();

    chrome.storage.local.set({ aiProvider, apiKey }, () => {
        if (chrome.runtime.lastError) {
            settingsStatus.textContent = `Error: ${chrome.runtime.lastError.message}`;
            return;
        }
        settingsStatus.textContent = "Saved!";
        setTimeout(() => {
            settingsStatus.textContent = "";
        }, 2000);
    });
});
const runAiBtn = document.getElementById("runAiBtn");
const testPromptInput = document.getElementById("testPrompt");
const aiResultEl = document.getElementById("aiResult");

runAiBtn.addEventListener("click", () => {
    const prompt = testPromptInput.value.trim() || "Return JSON with key status set to ok";
    aiResultEl.textContent = "Calling Spring Boot...";

    chrome.runtime.sendMessage({ type: "GENERATE_AI", prompt }, (res) => {
        if (chrome.runtime.lastError) {
            aiResultEl.textContent = `Extension error: ${chrome.runtime.lastError.message}`;
            return;
        }

        if (!res.ok) {
            aiResultEl.textContent = `Error (${res.status || "network"}): ${res.error}`;
            return;
        }

        aiResultEl.textContent = JSON.stringify(res.data, null, 2);
    });
});