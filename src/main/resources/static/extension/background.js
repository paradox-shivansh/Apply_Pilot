chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "CHECK_HEALTH") {
        fetch("http://localhost:8080/api/health")
            .then((res) => res.text())
            .then((data) => sendResponse({ ok: true, data: data.trim() }))
            .catch((err) => sendResponse({ ok: false, error: err.message || "Health check failed" }));
        return true;
    }

    if (message.type === "GENERATE_AI") {
        (async () => {
            try {
                const prefs = await chrome.storage.local.get(["aiProvider", "apiKey"]);
                const response = await fetch("http://localhost:8080/api/ai/generate", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        provider: prefs.aiProvider || "gemini",
                        apiKey: prefs.apiKey || "",
                        prompt: message.prompt
                    })
                });

                const text = await response.text();
                let parsed;
                try {
                    parsed = JSON.parse(text);
                } catch {
                    parsed = { raw: text };
                }

                if (!response.ok) {
                    sendResponse({
                        ok: false,
                        status: response.status,
                        error: parsed.message || `Request failed (${response.status})`
                    });
                    return;
                }

                sendResponse({ ok: true, data: parsed });
            } catch (err) {
                sendResponse({ ok: false, error: err.message || "Network error" });
            }
        })();

        return true;
    }

    return false;
});