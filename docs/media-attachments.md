# Portable Multi-Image Attachment Capability

This document defines the reusable AIBI media layer. It is not a StarManager screen or a
product-valuation policy.

## Portable outcome

- One task can carry an ordered snapshot of zero to twenty normalized images. Eight remains the conservative default host policy; twenty is an opt-in portable ceiling, not a provider guarantee.
- Each attachment has a generated filename, original selection index, and optional semantic role.
- Native adapters resize and re-encode copies sequentially; originals are never changed.
- The browser runtime assigns the full ordered batch to one provider file input.
- Prompt submission is blocked until the visible preview count increases by the requested count.
- A partial batch, a non-multiple input, provider rejection, timeout, or count mismatch requires
  visible user takeover. AIBI never silently submits the prompt with fewer images.

## Default normalization policy

| Property | Default |
|---|---:|
| Maximum images | 8 |
| Maximum long edge | 2,048 px |
| Initial JPEG quality | 0.84 |
| Minimum JPEG quality | 0.50 |
| Maximum encoded bytes | 2,000,000 per image |

If the byte target is not met by lowering JPEG quality, the adapter reduces dimensions in 15%
steps down to a 640 px long edge. Failure to meet the target is explicit. Rendering to a new JPEG
also removes original filenames and source metadata. Hosts should supply upright source pixels;
the Apple renderer normalizes `UIImage` orientation during drawing.

Hosts opting into 9–20 images must define a count-aware policy that lowers both the per-image byte
target and long-edge target as the batch grows. This bounds aggregate upload latency while keeping
a readable resolution floor for labels and engravings.

## Ordered evidence photos

The optional role is host-owned meaning, not provider metadata. A product-inspection host might
use roles such as `overall`, `front`, `back`, `label`, `material`, `damage`, `included-items`, and
`scale-reference`. The host should describe those roles in its prompt because providers are not
required to preserve filenames as model-visible context.

Photos can support observations about condition and visible characteristics. A host that reports
financial value must separately define provenance for comparable prices, uncertainty, currency,
date, and unverifiable authenticity. AIBI transports evidence but does not turn a visual estimate
into a guaranteed appraisal.

## Integration boundary

1. The host selects source photos and optional roles.
2. `AIBIImageNormalizer` creates ordered, bounded attachments away from UI state.
3. The host constructs `AIBITask(attachments: ...)` and a prompt that explains photo roles.
4. `AIBISession` checks provider capability before navigation.
5. The platform adapter attempts the public native file panel when supported; otherwise the
   in-browser runtime uses a standard `DataTransfer` batch.
6. AIBI compares the provider preview count with the pre-attachment baseline and submits only
   after the entire batch is visible.

On Android, the preferred transport is the public `WebChromeClient.onShowFileChooser` path:
normalized copies are exposed as ordered `content://` URIs through the host `FileProvider`, and
all URIs are returned in one callback when the native chooser requests multiple selection. A
single-selection callback instead receives one ordered URI at a time. Provider DOM metadata is
not treated as the native callback-mode authority, and the semantic attachment count is verified.
The host must expose `cache/aibi/` through an authority named `${applicationId}.fileprovider`.
Synthetic `DataTransfer` assignment remains a bounded fallback because some provider pages reject it.

Native Android media handoff must run in an attached `VISIBLE` WebView with a real on-screen
layout when a provider requires a trusted attachment-sheet gesture. The host may keep that view
behind its own opaque surface at near-zero alpha when browser viewing is off; placing it thousands
of pixels off-screen is not a portable substitute for a native multi-file gesture.

Provider navigation completion does not guarantee that the attachment portal is hydrated. If the
hidden WebView cannot yet resolve the attachment trigger or panel, it must remain hidden and retry
until the attachment deadline. The first missing trigger is not an immediate visible-takeover
condition.

Some mobile provider composers expose a nested semantic menu rather than invoking the file input
from the top-level plus button. The provider adapter must select the exact image action (for
example ChatGPT's visible `사진`/`Photos` menu item) before opening the native file panel; it must
not assume that a successful tap on the plus button already invoked `onShowFileChooser`. Hidden
Android WebViews may hydrate this menu more slowly than a visible surface, so discovery must use a
bounded multi-second retry window while staying hidden. A hidden WebView placed behind an opaque
host surface must keep its own click handling enabled and take touch focus only while dispatching
the trusted attachment gesture so the provider UI receives it. The WebView stays behind the opaque
host surface throughout the gesture; provider controls are tapped inside the attached WebView's
own coordinate space, so no visible promotion or opaque browser frame is required. The keyboard
remains dismissed. Its attached layout must also be clamped
to the host window bounds; a nominal dp reference viewport that becomes taller than the physical
window can place bottom composer controls in a clipped, non-interactive region.

After every trusted attachment gesture, hidden Android hosts must blur the active DOM element,
clear WebView focus, and hide the input method using both the WebView and host-window tokens. Repeat
the hide once after the pending IME frame because WebView can enqueue a keyboard-show request just
after the script or touch callback returns. A hidden browser must never leave only the keyboard
visible over the host composer.

Provider-specific selectors and maximums remain in `packages/providers/aibi-providers.json`.
Host limits, photo-role vocabulary, appraisal rules, and result validation remain outside the
portable engine.
