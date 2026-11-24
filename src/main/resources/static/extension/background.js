chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "CHECK_HEALTH") {
        fetch("http://localhost:8080/api/health")
            .then((response) => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                return response.text();
            })
            .then((data) => {
                sendResponse({ ok: true, data: data.trim() });
            })
            .catch((error) => {
                sendResponse({ ok: false, error: error.message });
            });

        // Required to keep the message channel open for async fetch()
        return true;
    }
});