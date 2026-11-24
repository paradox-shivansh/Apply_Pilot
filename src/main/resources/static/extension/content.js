// Runs in the context of matching web pages
console.log("Spring Boot Bridge content script loaded on:", window.location.href);

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "PING_PAGE") {
        sendResponse({ url: window.location.href, title: document.title });
    }
});