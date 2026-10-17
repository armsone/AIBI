# Current ChatGPT assistant markup observed on the official site

At 10:35–10:36 KST, the inspection opened only the newly created synthetic blue-image conversation from the official ChatGPT sidebar. No new request was submitted. Read-only DOM inspection returned tags, attribute names, fixed semantic role values and character counts; no prompt or full answer was exported.

The synthetic response exists: a DIV with data-markdown-text-style=assistant-message contains response paragraphs. A sibling heading has data-conversation-role=assistant. Legacy assistant selectors, article/data-role fallbacks and .markdown all match zero. The new exact assistant-style selector matches one. The corresponding synthetic input uses data-user-message-bubble and group/user-message, with no legacy data-message-author-role marker.

The assistant-only semantic selector is added to the shared provider configuration and the Stargram native adapters. User-message and conversation-role markers extend history exclusion, not extraction. The synthetic fixture contains no live content, account identifiers or conversation keys. This explains the previous HTTP-200 request with zero detected assistant messages; installed round-trip confirmation remains a separate step.
