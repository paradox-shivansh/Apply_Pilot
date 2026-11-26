if (!window.__aiPilotContentLoaded) {
    window.__aiPilotContentLoaded = true;

    console.log("Spring Boot Bridge content script loaded on:", window.location.href);
    const fieldElementRegistry = new Map();

    function isElementVisible(el) {
        if (!el || el.disabled || el.readOnly) return false;
        const type = (el.type || "").toLowerCase();
        if (type === "hidden" || type === "password" || type === "file" || type === "submit" || type === "button") {
            return false;
        }
        const style = window.getComputedStyle(el);
        if (style.display === "none" || style.visibility === "hidden" || parseFloat(style.opacity) === 0) {
            return false;
        }
        const rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }

    function getAssociatedLabelText(el) {
        if (el.labels && el.labels.length > 0) {
            const joined = [...el.labels].map((l) => l.innerText.trim()).filter(Boolean).join(" ");
            if (joined) return joined;
        }
        if (el.id) {
            const safeId = typeof CSS !== "undefined" && CSS.escape ? CSS.escape(el.id) : el.id;
            const explicitLabel = document.querySelector(`label[for="${safeId}"]`);
            if (explicitLabel && explicitLabel.innerText.trim()) {
                return explicitLabel.innerText.trim();
            }
        }
        const parentLabel = el.closest("label");
        if (parentLabel && parentLabel.innerText.trim()) {
            return parentLabel.innerText.trim();
        }
        // Fallback for custom form blocks where label sits in a sibling/parent container
        const container = el.closest(".form-group, .field, [class*='field'], [class*='input']") || el.parentElement;
        if (container) {
            const labelCandidate = container.querySelector("label, [class*='label']");
            if (labelCandidate && labelCandidate.innerText.trim()) {
                return labelCandidate.innerText.trim();
            }
        }
        return "";
    }

    function scanFormFields() {
        fieldElementRegistry.clear();
        const candidates = [...document.querySelectorAll("input, textarea, select")];
        const descriptors = [];
        let counter = 1;

        for (const el of candidates) {
            if (!isElementVisible(el)) continue;

            const fieldId = `field_${counter++}`;
            fieldElementRegistry.set(fieldId, el);

            const tag = el.tagName.toLowerCase();
            const options = tag === "select"
                ? [...el.options].map((o) => (o.text || o.value || "").trim()).filter(Boolean).slice(0, 40)
                : [];

            descriptors.push({
                fieldId,
                tag,
                type: (el.type || tag).toLowerCase(),
                id: el.id || "",
                name: el.name || "",
                placeholder: el.placeholder || "",
                ariaLabel: el.getAttribute("aria-label") || "",
                labelText: getAssociatedLabelText(el),
                options
            });
        }

        return descriptors;
    }

    function setNativeValue(el, value) {
        const tag = el.tagName.toLowerCase();
        if (tag === "select") {
            const lowerVal = String(value).toLowerCase().trim();
            const matchedOption = [...el.options].find(
                (opt) =>
                    opt.value.toLowerCase().trim() === lowerVal ||
                    opt.text.toLowerCase().trim() === lowerVal ||
                    opt.text.toLowerCase().includes(lowerVal)
            );
            if (matchedOption) {
                el.value = matchedOption.value;
            } else {
                el.value = value;
            }
        } else {
            const proto = tag === "textarea"
                ? window.HTMLTextAreaElement.prototype
                : window.HTMLInputElement.prototype;
            const descriptor = Object.getOwnPropertyDescriptor(proto, "value");
            if (descriptor && typeof descriptor.set === "function") {
                descriptor.set.call(el, value);
            } else {
                el.value = value;
            }
        }

        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function applyAutofillMapping(mapping) {
        let filledCount = 0;
        for (const [fieldId, val] of Object.entries(mapping || {})) {
            const el = fieldElementRegistry.get(fieldId);
            if (!el || !document.contains(el) || !isElementVisible(el)) continue;
            if (typeof val !== "string" || !val.trim()) continue;

            setNativeValue(el, val.trim());
            filledCount++;
        }
        return filledCount;
    }
    function normalizeJobUrl(rawUrl) {
        try {
            const u = new URL(rawUrl);
            ["utm_source", "utm_medium", "utm_campaign", "trackingId", "refId"].forEach((p) =>
                u.searchParams.delete(p)
            );
            return u.origin + u.pathname + (u.search ? u.search : "");
        } catch {
            return rawUrl;
        }
    }

    function isJobPage() {
        const url = window.location.href.toLowerCase();
        const portalMatch =
            url.includes("linkedin.com/jobs") ||
            url.includes("naukri.com/job-listings") ||
            url.includes("indeed.com/viewjob") ||
            url.includes("indeed.co.in/viewjob") ||
            url.includes("internshala.com/internship/detail") ||
            url.includes("internshala.com/job/detail") ||
            url.includes("test-job") ||
            url.includes("scam-job");

        const bodyText = document.body ? document.body.innerText : "";
        const hasJdKeyword =
            /(job description|about the role|role overview|responsibilities|qualifications|hiring|vacancy|internship)/i.test(
                bodyText
            );
        const hasApplyKeyword =
            /\b(apply now|apply here|apply for this role|submit application|send resume|apply)\b/i.test(
                bodyText
            );

        return portalMatch || (hasJdKeyword && hasApplyKeyword);
    }

    function extractJobData() {
        const titleEl =
            document.querySelector(".job-details-jobs-unified-top-card__job-title h1") ||
            document.querySelector(".jobs-unified-top-card__job-title") ||
            document.querySelector("h1");
        const jobTitle = titleEl ? titleEl.innerText.trim() : document.title.trim();

        const companyCandidate =
            document.querySelector(".job-details-jobs-unified-top-card__company-name a") ||
            document.querySelector(".job-details-jobs-unified-top-card__company-name") ||
            document.querySelector('[class*="company"], [data-company-name], [class*="employer"]');
        const companyName = companyCandidate ? companyCandidate.innerText.trim().split("\n")[0] : "";

        const descContainer =
            document.querySelector("#job-details") ||
            document.querySelector(".jobs-description__content") ||
            document.body;
        const rawBody = descContainer ? descContainer.innerText : "";

        const emails = [...new Set(rawBody.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi) || [])];
        const recruiterContact = emails.join(", ");

        const salaryMatch =
            rawBody.match(/(?:₹|INR|\$|Rs\.?)\s*[\d,]+(?:\s*(?:-|to|–)\s*(?:₹|INR|\$|Rs\.?)\s*[\d,]+)?(?:\s*(?:LPA|per month|\/mo|\/yr|annum))?/i) ||
            [];
        const salary = salaryMatch[0] ? salaryMatch[0].trim() : "";

        const descriptionText = rawBody.replace(/\s+/g, " ").trim().slice(0, 6000);

        return {
            jobUrl: normalizeJobUrl(window.location.href),
            jobTitle,
            companyName,
            recruiterContact,
            salary,
            descriptionText
        };
    }

    function injectPageBadge(verdict, scamScore) {
        const existing = document.getElementById("aipilot-scam-badge");
        if (existing) existing.remove();

        const badge = document.createElement("div");
        badge.id = "aipilot-scam-badge";

        const colors = {
            LEGIT: { bg: "#14532d", border: "#22c55e", text: "#dcfce7", label: "Legit" },
            SUSPICIOUS: { bg: "#713f12", border: "#eab308", text: "#fef9c3", label: "Suspicious" },
            SCAM: { bg: "#7f1d1d", border: "#ef4444", text: "#fee2e2", label: "Scam Risk" }
        };
        const style = colors[verdict] || colors.SUSPICIOUS;

        badge.textContent = `AI Pilot: ${style.label} (${scamScore}/100)`;
        Object.assign(badge.style, {
            position: "fixed",
            bottom: "18px",
            right: "18px",
            zIndex: "2147483647",
            padding: "8px 12px",
            borderRadius: "8px",
            backgroundColor: style.bg,
            color: style.text,
            border: `1px solid ${style.border}`,
            fontFamily: "system-ui, -apple-system, sans-serif",
            fontSize: "12px",
            fontWeight: "600",
            boxShadow: "0 6px 18px rgba(0, 0, 0, 0.28)",
            cursor: "pointer"
        });

        badge.title = "Click to dismiss badge";
        badge.addEventListener("click", () => badge.remove());

        document.body.appendChild(badge);
    }

    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (message.type === "PING_PAGE") {
            sendResponse({ url: window.location.href, title: document.title });
            return false;
        }

        if (message.type === "SCRAPE_JOB") {
            if (!isJobPage()) {
                sendResponse({ ok: false, error: "This page does not look like a job description page." });
                return false;
            }
            sendResponse({ ok: true, job: extractJobData() });
            return false;
        }

        if (message.type === "RENDER_SCAM_BADGE") {
            injectPageBadge(message.verdict, message.scamScore);
            sendResponse({ ok: true });
            return false;
        }
        if (message.type === "SCAN_FORM_FIELDS") {
            const fields = scanFormFields();
            sendResponse({
                ok: true,
                jobUrl: normalizeJobUrl(window.location.href),
                jobTitle: document.querySelector("h1")?.innerText.trim() || document.title.trim(),
                companyName: document.querySelector('[class*="company"], [data-company-name], [class*="employer"]')
                    ?.innerText.trim().split("\n")[0] || "",
                fields
            });
            return false;
        }

        if (message.type === "APPLY_AUTOFILL") {
            const filledCount = applyAutofillMapping(message.mapping || {});
            sendResponse({ ok: true, filledCount });
            return false;
        }

        return false;
    });
}