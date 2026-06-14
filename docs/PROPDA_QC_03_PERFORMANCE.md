# PROPDA QC-03 Performance Profiling

## Bottlenecks Identified

- Scroll handling for hybrid topic pagination did synchronous scroll-height reads, page-bound scans, and bridge checks directly from every scroll event. On long topics this can collide with WebView painting and cause visible jank.
- Media image load handling updated classes immediately per image callback. Attachment-heavy topics can deliver many callbacks in a short burst and interrupt scroll/render work.
- Visible-page calculation recalculated separator document positions again when mapping the currently visible post back to a page.
- Code/media blocks are large paint surfaces during scroll; without paint containment they can increase invalidation pressure in WebView.

## Optimizations Applied

- Moved hybrid infinite-scroll checks behind a single `requestAnimationFrame` gate and registered the scroll listener as passive. This keeps the native scroll event light and batches DOM reads into frame work.
- Batched media image load completion into one animation-frame pass and kept one-shot load/error listeners.
- Cached per-post media-heavy classification in `dataset`.
- Reused separator positions inside visible-page detection to avoid a second layout-read pass for the same separators.
- Added paint containment for topic media images and code blocks in theme styles to reduce repaint scope during scroll.

## Remaining Heavy Areas

- `updateVisibleThemePage()` still scans posts/containers on scheduled updates. It is now invoked through rAF from scroll, but very long multi-page topics can still make the scan expensive.
- `refreshThemeDynamicPostBlocks()` still runs several legacy transformers after appended pages. This is outside the safe scope for QC-03 because those helpers are shared with quotes/spoilers/code/media behavior.
- Refresh/unread restore intentionally keeps several delayed retries to remain stable while images and bottom chrome settle. Reducing those retries further needs device verification.

## Future Optimization Candidates

- Consider an IntersectionObserver-based visible page signal if Android WebView coverage is acceptable for the supported devices.
- Add lightweight runtime counters around scroll frame duration behind the existing debug flag.
- Limit visible-page post scans to loaded page containers near the viewport once pagination boundaries are reliably available.
- Revisit legacy DOM transformers and make them root-scoped for newly appended pages only.

## Estimated Impact

- Scroll jank risk should be lower because high-frequency scroll events no longer perform the full infinite-scroll check synchronously.
- Image-heavy topics should feel steadier during load bursts due to batched image class updates.
- Paint containment should reduce WebView repaint pressure for large inline images and code blocks without changing layout or UI behavior.

Manual runtime profiling was not available in this pass; verification relies on static review and build/check commands.
