(function installFootnotes() {
    if (document.ebookFootnotesReady) return;
    if (document.readyState !== "complete") {
        window.addEventListener("load", installFootnotes, { once: true });
        return;
    }
    document.ebookFootnotesReady = true;

    // Split selector lists only at top-level commas (not inside :is(), attributes or strings).
    function selectors(list) {
        const parts = [];
        let start = 0, depth = 0, quote = "";
        for (let i = 0; i < list.length; i++) {
            const c = list[i];
            if (c === "\\") { i++; continue; }
            if (quote) { if (c === quote) quote = ""; continue; }
            if (c === "'" || c === '"') quote = c;
            else if (c === "(" || c === "[") depth++;
            else if (c === ")" || c === "]") depth--;
            else if (c === "," && !depth) { parts.push(list.slice(start, i)); start = i + 1; }
        }
        return parts.concat(list.slice(start));
    }

    const hints = new Map();
    const visited = new Set();
    function readSheet(sheet) {
        if (!sheet || sheet.disabled || visited.has(sheet) ||
            (sheet.media.mediaText && !matchMedia(sheet.media.mediaText).matches)) return;
        visited.add(sheet);
        let rules;
        try { rules = sheet.cssRules; } catch (_) { return; } // Origin-restricted sheet.
        readRules(rules);
    }
    function readRules(rules) {
        for (const rule of rules) {
            if (rule.type === CSSRule.IMPORT_RULE) {
                if (!rule.media.mediaText || matchMedia(rule.media.mediaText).matches) readSheet(rule.styleSheet);
            } else if (rule.type === CSSRule.MEDIA_RULE) {
                if (matchMedia(rule.conditionText).matches) readRules(rule.cssRules);
            } else if (rule.type === CSSRule.SUPPORTS_RULE) {
                if (CSS.supports(rule.conditionText)) readRules(rule.cssRules);
            } else if (rule.constructor.name === "CSSLayerBlockRule") {
                readRules(rule.cssRules);
            } else if (rule.type === CSSRule.STYLE_RULE) {
                const attr = /^attr\(\s*([-\w]+)(?:\s+(?:raw-string|string))?\s*\)$/i.exec(rule.style.content);
                if (!attr) continue;
                for (const selector of selectors(rule.selectorText)) {
                    // Only a hint revealed on the marker itself. Static counters and captions stay as authored.
                    const trigger = /:(?:hover|focus|focus-visible|focus-within)(?:::?(?:before|after))?$/i.exec(selector.trimEnd());
                    if (!trigger) continue;
                    const base = selector.slice(0, trigger.index);
                    const owner = !base.trim() || /[\s>+~]$/.test(base) ? base + "*" : base;
                    let elements;
                    try { elements = document.querySelectorAll(owner); } catch (_) { continue; }
                    for (const element of elements) {
                        const text = (element.getAttribute(attr[1]) || "").trim();
                        if (!text) continue;
                        // Conflicting declarations are left to the publisher's own presentation.
                        if (!hints.has(element)) hints.set(element, text);
                        else if (hints.get(element) !== text) hints.set(element, null);
                    }
                }
            }
        }
    }
    for (const sheet of document.styleSheets) readSheet(sheet);

    const interactive = 'a[href],button,input,select,textarea,summary,[contenteditable],[role="link"],[role="button"]';
    for (const [marker, text] of hints) {
        if (!text || !document.body.contains(marker) || marker.closest(interactive) || marker.querySelector(interactive)) continue;
        const hasLabel = marker.textContent.trim() || marker.childElementCount;
        if (hasLabel && !getComputedStyle(marker).display.startsWith("inline")) continue;
        const button = document.createElementNS("http://www.w3.org/1999/xhtml", "button");
        button.type = "button";
        button.className = "ebook-inline-footnote";
        if (marker.id) button.id = marker.id;
        if (hasLabel) {
            while (marker.firstChild) button.appendChild(marker.firstChild);
        } else button.textContent = "注";
        button.addEventListener("click", event => {
            event.stopPropagation();
            window.EbookFootnotes.showNote(text);
        });
        marker.parentNode.replaceChild(button, marker);
    }

    // Readium 3.4 checks for the literal epub:type="noteref" in the rendered anchor.
    for (const link of document.querySelectorAll('a[role~="doc-noteref"], a[*|type~="noteref"], a[epub\\:type~="noteref"]')) {
        if (link.getAttribute("epub:type") === "noteref") continue;
        const type = link.getAttributeNS("http://www.idpf.org/2007/ops", "type") || link.getAttribute("epub:type") || "";
        const roles = (link.getAttribute("role") || "").split(/\s+/);
        if (type.split(/\s+/).includes("noteref") || roles.includes("doc-noteref")) {
            link.setAttribute("epub:type", "noteref");
        }
    }
    document.addEventListener("contextmenu", event => {
        if (!(event.target instanceof Element)) return;
        const marker = event.target.closest("a[href], button.ebook-inline-footnote");
        if (!marker || (marker.localName === "a" && marker.getAttribute("epub:type") !== "noteref")) return;
        event.preventDefault();
        const selection = window.getSelection();
        if (selection) selection.removeAllRanges();
        marker.click();
    }, true);
})();
