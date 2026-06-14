# UI Thread Regression Checklist

## Basic topic rendering
- [ ] Topic opens.
- [ ] First page opens.
- [ ] Next/previous/last page works.
- [ ] Direct post link / findpost works.
- [ ] Back navigation works.
- [ ] Scroll restore still works.

## Post content
- [ ] Plain text posts render correctly.
- [ ] Quotes render correctly.
- [ ] Nested quotes render correctly.
- [ ] Spoilers open/close.
- [ ] Code blocks remain readable.
- [ ] Images render correctly.
- [ ] Attachments still open/download.
- [ ] Polls still render.

## Smart Quotes
- [ ] Small quotes remain unchanged.
- [ ] Large quotes collapse.
- [ ] Expand works.
- [ ] Collapse works.
- [ ] Dark theme works.
- [ ] No scroll reset after expand/collapse.

## Reader Mode
- [ ] Compact mode works.
- [ ] Comfortable mode works.
- [ ] Font scale does not break layout.
- [ ] Toolbar behavior is unchanged unless task explicitly changes it.

## Performance feel
- [ ] Scrolling remains smooth.
- [ ] No visible flicker.
- [ ] No large layout jumps.
- [ ] No WebView console errors if logs are available.
