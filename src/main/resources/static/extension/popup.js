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

const resumeFileInput = document.getElementById("resumeFileInput");
const uploadResumeBtn = document.getElementById("uploadResumeBtn");
const loadResumeBtn = document.getElementById("loadResumeBtn");
const resumeStatusEl = document.getElementById("resumeStatus");
const resumePreviewEl = document.getElementById("resumePreview");

async function getOrCreateLocalUserId() {
    const data = await chrome.storage.local.get(["localUserId"]);
    if (data.localUserId) return data.localUserId;
    const newId = "user_" + crypto.randomUUID().slice(0, 8);
    await chrome.storage.local.set({ localUserId: newId });
    return newId;
}

function renderProfile(profileObj) {
    resumePreviewEl.textContent = typeof profileObj === "string"
        ? profileObj
        : JSON.stringify(profileObj, null, 2);
}

async function loadSavedResume() {
    const userId = await getOrCreateLocalUserId();
    const cacheKey = `resumeProfile_${userId}`;

    // 1. Show local cache immediately if present
    const cached = await chrome.storage.local.get([cacheKey]);
    if (cached[cacheKey]) {
        renderProfile(cached[cacheKey]);
    }

    // 2. Fetch latest from backend for this userId
    try {
        const res = await fetch(`http://localhost:8080/api/resume?userId=${encodeURIComponent(userId)}`);
        if (!res.ok) return;
        const data = await res.json();
        const parsed = JSON.parse(data.parsedJson);
        await chrome.storage.local.set({ [cacheKey]: parsed });
        renderProfile(parsed);
    } catch {
        // Keep local cached profile if offline
    }
}

uploadResumeBtn.addEventListener("click", async () => {
    const file = resumeFileInput.files[0];
    if (!file) {
        resumeStatusEl.textContent = "Choose a PDF file first.";
        return;
    }

    uploadResumeBtn.disabled = true;
    resumeStatusEl.textContent = "Extracting & parsing resume...";

    try {
        const userId = await getOrCreateLocalUserId();
        const prefs = await chrome.storage.local.get(["aiProvider", "apiKey"]);

        const formData = new FormData();
        formData.append("file", file);
        formData.append("userId", userId);
        formData.append("provider", prefs.aiProvider || "gemini");
        formData.append("apiKey", prefs.apiKey || "");

        const response = await fetch("http://localhost:8080/api/resume/upload", {
            method: "POST",
            body: formData
        });

        const bodyText = await response.text();
        let result;
        try {
            result = JSON.parse(bodyText);
        } catch {
            result = { message: bodyText };
        }

        if (!response.ok) {
            resumeStatusEl.textContent = `Error (${response.status}): ${result.message || "Upload failed"}`;
            return;
        }

        const parsedProfile = JSON.parse(result.parsedJson);
        await chrome.storage.local.set({ [`resumeProfile_${userId}`]: parsedProfile });
        const allStorage = await chrome.storage.local.get(null);
        const staleKeys = Object.keys(allStorage).filter((k) => k.startsWith(`matchCache_${userId}_`));
        if (staleKeys.length) await chrome.storage.local.remove(staleKeys);
        renderProfile(parsedProfile);
        resumeStatusEl.textContent = `Saved (${result.fileName})`;
    } catch (err) {
        resumeStatusEl.textContent = `Upload error: ${err.message}`;
    } finally {
        uploadResumeBtn.disabled = false;
    }
});

loadResumeBtn.addEventListener("click", loadSavedResume);
loadSavedResume();

const analyzeJobBtn = document.getElementById("analyzeJobBtn");
const forceAnalyzeBtn = document.getElementById("forceAnalyzeBtn");
const scamStatusEl = document.getElementById("scamStatus");
const scamResultEl = document.getElementById("scamResult");

function renderScamReport(report, fromCache) {
    const reasonsHtml = (report.reasons || [])
        .map((r) => `<li>${r}</li>`)
        .join("");

    scamResultEl.innerHTML = `
    <div><strong>Verdict:</strong> ${report.verdict} (${report.scamScore}/100)${fromCache ? " <em>(cached)</em>" : ""}</div>
    ${reasonsHtml ? `<ul style="padding-left: 18px; margin: 6px 0;">${reasonsHtml}</ul>` : ""}
  `;
}

async function getActiveTab() {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    return tab;
}
async function getJobFromTab(tabId) {
    try {
        const res = await chrome.tabs.sendMessage(tabId, { type: "SCRAPE_JOB" });
        if (res) return res;
    } catch {
        // Fall through to programmatic injection
    }

    await chrome.scripting.executeScript({
        target: { tabId },
        files: ["content.js"]
    });

    return await chrome.tabs.sendMessage(tabId, { type: "SCRAPE_JOB" });
}
async function runJobAnalysis(bypassCache = false) {
    scamStatusEl.textContent = "Inspecting current page...";
    analyzeJobBtn.disabled = true;
    forceAnalyzeBtn.disabled = true;

    try {
        const tab = await getActiveTab();
        if (!tab || !tab.id) {
            scamStatusEl.textContent = "No active tab found.";
            return;
        }

        const scrapeRes = await getJobFromTab(tab.id);
        if (!scrapeRes || !scrapeRes.ok) {
            scamStatusEl.textContent = scrapeRes?.error || "Unable to read job details from this tab.";
            return;
        }

        const job = scrapeRes.job;
        const cacheKey = `scamCache_${job.jobUrl}`;

        if (!bypassCache) {
            const cached = await chrome.storage.local.get([cacheKey]);
            if (cached[cacheKey]) {
                const report = cached[cacheKey];
                renderScamReport(report, true);
                scamStatusEl.textContent = "Loaded from local cache.";
                await chrome.tabs.sendMessage(tab.id, {
                    type: "RENDER_SCAM_BADGE",
                    verdict: report.verdict,
                    scamScore: report.scamScore
                }).catch(() => {});
                return;
            }
        }

        scamStatusEl.textContent = "Running two-layer scam check...";
        const prefs = await chrome.storage.local.get(["aiProvider", "apiKey"]);

        const response = await fetch("http://localhost:8080/api/analyze-job", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                ...job,
                provider: prefs.aiProvider || "gemini",
                apiKey: prefs.apiKey || ""
            })
        });

        const bodyText = await response.text();
        let parsed;
        try {
            parsed = JSON.parse(bodyText);
        } catch {
            parsed = { message: bodyText };
        }

        if (!response.ok) {
            scamStatusEl.textContent = `Error (${response.status}): ${parsed.message || "Analysis failed"}`;
            return;
        }

        await chrome.storage.local.set({ [cacheKey]: parsed });
        renderScamReport(parsed, false);
        scamStatusEl.textContent = "Analysis complete & cached.";

        await chrome.tabs.sendMessage(tab.id, {
            type: "RENDER_SCAM_BADGE",
            verdict: parsed.verdict,
            scamScore: parsed.scamScore
        }).catch(() => {});
    } catch (err) {
        scamStatusEl.textContent = `Error: ${err.message}`;
    } finally {
        analyzeJobBtn.disabled = false;
        forceAnalyzeBtn.disabled = false;
    }
}

analyzeJobBtn.addEventListener("click", () => runJobAnalysis(false));
forceAnalyzeBtn.addEventListener("click", () => runJobAnalysis(true));


const matchResumeBtn = document.getElementById("matchResumeBtn");
const forceMatchBtn = document.getElementById("forceMatchBtn");
const matchStatusEl = document.getElementById("matchStatus");
const matchResultEl = document.getElementById("matchResult");

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function renderMatchReport(report, fromCache) {
    const score = Number(report.matchScore) || 0;
    const scoreColor = score >= 75 ? "#22c55e" : score >= 50 ? "#eab308" : "#ef4444";

    const matchingTags = (report.matchingSkills || [])
        .map(
            (s) =>
                `<span style="display:inline-block;background:#14532d;color:#dcfce7;padding:2px 6px;border-radius:4px;margin:2px;font-size:11px;">${escapeHtml(s)}</span>`
        )
        .join("");

    const missingTags = (report.missingSkills || [])
        .map(
            (s) =>
                `<span style="display:inline-block;background:#7f1d1d;color:#fee2e2;padding:2px 6px;border-radius:4px;margin:2px;font-size:11px;">${escapeHtml(s)}</span>`
        )
        .join("");

    const suggestionsList = (report.suggestions || [])
        .map((tip) => `<li>${escapeHtml(tip)}</li>`)
        .join("");

    matchResultEl.innerHTML = `
    <div style="margin-bottom:6px;">
      <strong>Resume Match:</strong>
      <span style="color:${scoreColor};font-weight:700;">${score}%</span>
      ${fromCache ? "<em>(cached)</em>" : ""}
    </div>
    <div style="margin-bottom:6px;">
      <strong>Missing Keywords to Add:</strong><br/>
      ${missingTags || "<em>None — strong keyword coverage!</em>"}
    </div>
    <div style="margin-bottom:6px;">
      <strong>Matching Skills:</strong><br/>
      ${matchingTags || "<em>No direct matches detected</em>"}
    </div>
    ${
        suggestionsList
            ? `<div><strong>Suggestions:</strong><ul style="padding-left:18px;margin:4px 0;">${suggestionsList}</ul></div>`
            : ""
    }
  `;
}

async function runResumeMatch(bypassCache = false) {
    matchStatusEl.textContent = "Scraping job & checking resume...";
    matchResumeBtn.disabled = true;
    forceMatchBtn.disabled = true;

    try {
        const tab = await getActiveTab();
        if (!tab || !tab.id) {
            matchStatusEl.textContent = "No active tab found.";
            return;
        }

        const scrapeRes = await getJobFromTab(tab.id);
        if (!scrapeRes || !scrapeRes.ok) {
            matchStatusEl.textContent = scrapeRes?.error || "Unable to read job details from this tab.";
            return;
        }

        const userId = await getOrCreateLocalUserId();
        const job = scrapeRes.job;
        const cacheKey = `matchCache_${userId}_${job.jobUrl}`;

        if (!bypassCache) {
            const cached = await chrome.storage.local.get([cacheKey]);
            if (cached[cacheKey]) {
                renderMatchReport(cached[cacheKey], true);
                matchStatusEl.textContent = "Loaded match from local cache.";
                return;
            }
        }

        matchStatusEl.textContent = "Comparing resume against JD...";
        const resumeCacheKey = `resumeProfile_${userId}`;
        const storageData = await chrome.storage.local.get(["aiProvider", "apiKey", resumeCacheKey]);
        const cachedResume = storageData[resumeCacheKey];
        const resumeJson = cachedResume
            ? (typeof cachedResume === "string" ? cachedResume : JSON.stringify(cachedResume))
            : "";

        const response = await fetch("http://localhost:8080/api/match-resume", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-AI-API-Key": storageData.apiKey || ""
            },
            body: JSON.stringify({
                userId,
                jobUrl: job.jobUrl,
                jobTitle: job.jobTitle,
                companyName: job.companyName,
                descriptionText: job.descriptionText,
                provider: storageData.aiProvider || "gemini",
                resumeJson
            })
        });

        const bodyText = await response.text();
        let parsed;
        try {
            parsed = JSON.parse(bodyText);
        } catch {
            parsed = { message: bodyText };
        }

        if (!response.ok) {
            matchStatusEl.textContent = `Error (${response.status}): ${parsed.message || "Match failed"}`;
            return;
        }

        await chrome.storage.local.set({ [cacheKey]: parsed });
        renderMatchReport(parsed, false);
        matchStatusEl.textContent = "Gap analysis complete & cached.";
    } catch (err) {
        matchStatusEl.textContent = `Error: ${err.message}`;
    } finally {
        matchResumeBtn.disabled = false;
        forceMatchBtn.disabled = false;
    }
}
matchResumeBtn.addEventListener("click", () => runResumeMatch(false));
forceMatchBtn.addEventListener("click", () => runResumeMatch(true));